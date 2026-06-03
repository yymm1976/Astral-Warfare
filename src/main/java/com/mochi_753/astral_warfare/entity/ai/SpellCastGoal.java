package com.mochi_753.astral_warfare.entity.ai;

import com.mochi_753.astral_warfare.entity.NightfallSingularityEntity;
import com.mochi_753.astral_warfare.entity.StarcoreGolemEntity;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mochi_753.astral_warfare.init.ModConstants;
import com.mochi_753.astral_warfare.init.ModEntities;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import com.mochi_753.astral_warfare.network.ClientboundScreenShakePacket;
import com.mochi_753.astral_warfare.network.ClientboundMazeSyncPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.joml.Vector3f;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// StellaEvoker 一阶段智能法术状态机 AI Goal
// 每隔 5-7 秒，在法力值充足的情况下，随机抽取释放法术
// 共 6 个常规法术：星陨矩阵、星界发散光束、夜幕黑洞、星命锁链、星轨切割、念力投掷
// 星门涌动已改为血量触发技能，由 StellaEvokerEntity.tickPhase1() 直接管理
//
// 星命锁链视觉预警：
//   前 1.5 秒（30 tick）：连线粒子为淡蓝色 END_ROD，温和提示
//   后 1.5 秒（30 tick）：连线粒子切换为猩红色 DRAGON_BREATH，伴随滴答声
//   给零基础玩家提供清晰的"断连逃生"视觉暗示
public class SpellCastGoal extends Goal {

    private final StellaEvokerEntity evoker;
    private SpellType currentSpell;
    private int castTick;
    private int nextCastAttempt;
    private final Map<SpellType, Integer> cooldowns = new EnumMap<>(SpellType.class);

    // 星界发散光束：每秒伤害间隔
    private static final int BEAM_DAMAGE_INTERVAL = ModConstants.BEAM_DAMAGE_INTERVAL;
    // 星命锁链：最大距离
    private static final double FATE_LINK_MAX_DIST = ModConstants.FATE_LINK_MAX_DIST;
    // 星命锁链：斩杀伤害
    private static final float FATE_LINK_DAMAGE = ModConstants.FATE_LINK_DAMAGE;
    // 星命锁链：预警切换时间点（前 1.5 秒 = 30 tick）
    private static final int FATE_LINK_WARNING_THRESHOLD = ModConstants.FATE_LINK_WARNING_THRESHOLD;

    // 光束伤害计时器
    private int beamDamageTimer = 0;
    // 星命锁链目标
    private Player fateLinkTarget = null;
    // 星命锁链起始位置
    private Vec3 fateLinkOrigin = null;
    // 夜幕黑洞实体引用
    private NightfallSingularityEntity singularity = null;
    // 星陨矩阵：锁定的目标玩家（施法开始时确定，确保粒子预警与爆炸位置一致）
    private Player starfallTarget = null;
    // 星陨矩阵：施法开始时锁定的目标位置（非锁头，爆炸在此固定位置）
    // 设计意图：陨石砸向的是"施法时玩家所在的位置"，玩家可以通过走位躲避
    private Vec3 starfallLockedPos = null;
    // 星轨切割：锁定的目标玩家
    private Player starRailTarget = null;
    // 星轨切割：激光方向（从 BOSS 到目标玩家的水平方向）
    private Vec3 starRailDirection = null;
    // 星轨切割：激光起始位置
    private Vec3 starRailOrigin = null;
    // 念力投掷：目标傀儡
    private StarcoreGolemEntity throwGolem = null;
    // 念力投掷：目标玩家
    private Player throwTarget = null;

    // 星轨迷宫：网格中心位置
    private Vec3 mazeCenter = null;
    // 星轨迷宫：当前激活列组（0=偶数列，1=奇数列）
    private int mazeActiveGroup = 0;

    public SpellCastGoal(StellaEvokerEntity evoker) {
        this.evoker = evoker;
    }

    // 判断当前是否正在施法（用于 getArmPose() 驱动模型动画）
    public boolean isCasting() {
        return this.currentSpell != null;
    }

    @Override
    public boolean canUse() {
        if (this.evoker.level().isClientSide) return false;
        if (this.evoker.getCombatPhase() != StellaEvokerEntity.PHASE_1_CASTER) return false;
        if (this.evoker.isTransitioning()) return false;
        // 死亡演出期间禁止施法：防止 super.tick() 中 goalSelector.tick() 在 isDying() 检查前激活本 Goal
        if (this.evoker.isDying()) return false;

        // 冷却递减：必须在 canUse() 中执行
        // 关键原因：tick() 仅在 Goal 激活时被 GoalSelector 调用
        // 施法结束后 Goal 进入非激活状态，tick() 不再执行
        // 如果冷却递减只在 tick() 中，第一次施法后冷却永远不递减，BOSS 再也不会施法
        // canUse() 由 GoalSelector 每 tick 调用（无论 Goal 是否激活），确保冷却始终递减
        for (SpellType spell : SpellType.values()) {
            int cd = this.cooldowns.getOrDefault(spell, 0);
            if (cd > 0) {
                this.cooldowns.put(spell, cd - 1);
            }
        }
        if (this.nextCastAttempt > 0) {
            this.nextCastAttempt--;
        }

        // 冷却递减后，检查施法间隔
        if (this.nextCastAttempt > 0) {
            return false;
        }

        if (this.currentSpell != null) return false;

        // 检查是否有任何法术冷却完毕
        boolean allOnCooldown = true;
        for (SpellType spell : SpellType.values()) {
            int cd = this.cooldowns.getOrDefault(spell, 0);
            if (cd <= 0) {
                allOnCooldown = false;
                break;
            }
        }

        // 全部冷却中时，设置重试间隔避免空转
        if (allOnCooldown) {
            this.nextCastAttempt = 60 + this.evoker.getRandom().nextInt(40);
            return false;
        }

        SpellType spell = SpellType.pickRandom(this.evoker);
        if (spell == null) {
            this.nextCastAttempt = 60 + this.evoker.getRandom().nextInt(40);
            return false;
        }

        this.currentSpell = spell;
        this.castTick = 0;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // 死亡演出期间中断正在施放的法术
        if (this.evoker.isDying()) return false;
        return this.currentSpell != null && this.castTick < this.currentSpell.castDuration;
    }

    @Override
    public void start() {
        this.castTick = 0;
        this.beamDamageTimer = 0;

        // 施法动画：BOSS 举手→下挥，增强法术释放的视觉反馈
        // 先清除旧值再设置，强制 GeckoLib 重新播放动画（而非继续上次残留帧）
        this.evoker.currentAttackAnim = null;
        this.evoker.currentAttackAnim = "stella_evoker_spell_cast";

        // 星命锁链：记录目标玩家和起始位置
        if (this.currentSpell == SpellType.FATE_LINK) {
            Player target = findNearestSurvivalPlayer();
            if (target != null) {
                this.fateLinkTarget = target;
                this.fateLinkOrigin = target.position();
            } else {
                this.currentSpell = null;
            }
        }

        // 星陨矩阵：锁定目标玩家，用于粒子预警和最终爆炸定位
        // 非锁头设计：保存施法开始时玩家的位置，爆炸在此固定位置
        // 玩家可以通过走位躲避陨石，而不是被追踪
        if (this.currentSpell == SpellType.STARFALL_MATRIX) {
            Player target = findNearestSurvivalPlayer();
            if (target != null) {
                this.starfallTarget = target;
                this.starfallLockedPos = target.position();
            } else {
                this.currentSpell = null;
            }
        }

        // 夜幕黑洞：在目标位置生成引力触点
        if (this.currentSpell == SpellType.NIGHTFALL_SINGULARITY) {
            spawnSingularity();
        }

        // 星轨切割：锁定目标玩家，计算激光方向
        if (this.currentSpell == SpellType.STAR_RAIL_CUT) {
            Player target = findNearestSurvivalPlayer();
            if (target != null) {
                this.starRailTarget = target;
                Vec3 toTarget = target.position().subtract(this.evoker.position());
                // 仅取水平方向，激光沿地面切割
                this.starRailDirection = new Vec3(toTarget.x, 0, toTarget.z).normalize();
                // 修复：起点在目标玩家同高度的地面，而非BOSS下方4格
                // BOSS悬浮在Y+6，-4格仍在空中，激光无法命中地面玩家
                // 改为将Y坐标对齐到目标玩家的地面高度
                this.starRailOrigin = this.evoker.position().add(0, -this.evoker.getY() + this.starRailTarget.getY() + 0.5, 0);
            } else {
                this.currentSpell = null;
            }
        }

        // 念力投掷：搜索附近充能傀儡
        if (this.currentSpell == SpellType.TELEKINETIC_THROW) {
            if (!(this.evoker.level() instanceof ServerLevel serverLevel)) {
                this.currentSpell = null;
            } else {
                AABB searchBox = this.evoker.getBoundingBox().inflate(ModConstants.TELEKINETIC_THROW_GOLEM_RANGE);
                List<StarcoreGolemEntity> golems = serverLevel.getEntitiesOfClass(
                        StarcoreGolemEntity.class, searchBox,
                        golem -> golem.isAlive() && golem.isCharged()
                );
                if (golems.isEmpty()) {
                    // 没有充能傀儡时取消此法术，避免浪费法力
                    // 设置重试间隔防止每 tick 空转：无傀儡时连续选取 TELEKINETIC_THROW 并取消
                    this.currentSpell = null;
                    this.nextCastAttempt = 40 + this.evoker.getRandom().nextInt(20);
                } else {
                    this.throwGolem = golems.get(0);
                    this.throwTarget = findNearestSurvivalPlayer(serverLevel);
                    if (this.throwTarget == null) {
                        this.currentSpell = null;
                    }
                }
            }
        }

        // 星轨迷宫：以目标玩家位置为网格中心
        if (this.currentSpell == SpellType.STAR_TRACK_MAZE) {
            Player target = findNearestSurvivalPlayer();
            if (target != null) {
                this.mazeCenter = target.position();
                this.mazeActiveGroup = 0;
            } else {
                this.currentSpell = null;
            }
        }

        // 技能施放提示：向所有追踪 BOSS 的玩家发送聊天框消息
        if (this.currentSpell != null && this.evoker.level() instanceof ServerLevel) {
            String translationKey = switch (this.currentSpell) {
                case STARFALL_MATRIX -> "entity.astral_warfare.stella_evoker.spell.starfall";
                case ASTRAL_BEAM -> "entity.astral_warfare.stella_evoker.spell.astral_beam";
                case NIGHTFALL_SINGULARITY -> "entity.astral_warfare.stella_evoker.spell.singularity";
                case FATE_LINK -> "entity.astral_warfare.stella_evoker.spell.fate_link";
                case STAR_RAIL_CUT -> "entity.astral_warfare.stella_evoker.spell.star_rail_cut";
                case TELEKINETIC_THROW -> "entity.astral_warfare.stella_evoker.spell.telekinetic_throw";
                case STAR_TRACK_MAZE -> "entity.astral_warfare.stella_evoker.spell.star_track_maze";
            };
            for (net.minecraft.server.level.ServerPlayer player : this.evoker.getBossEvent().getPlayers()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(translationKey));
            }
        }
    }

    @Override
    public void tick() {
        // 空指针防御：非标调用可能导致 currentSpell 为 null
        if (this.currentSpell == null) return;

        // 冷却递减已移至 canUse()：canUse() 由 GoalSelector 每 tick 调用
        // 无论 Goal 是否激活都会执行，确保冷却始终递减
        // tick() 仅在 Goal 激活时运行，不适合承担冷却递减职责

        this.castTick++;

        // 施法过程中根据法术类型执行持续逻辑
        if (this.currentSpell == SpellType.ASTRAL_BEAM) {
            tickAstralBeam();
        } else if (this.currentSpell == SpellType.FATE_LINK) {
            tickFateLink();
        } else if (this.currentSpell == SpellType.STARFALL_MATRIX) {
            tickStarfallMatrix();
        } else if (this.currentSpell == SpellType.STAR_RAIL_CUT) {
            tickStarRailCut();
        } else if (this.currentSpell == SpellType.TELEKINETIC_THROW) {
            tickTelekineticThrow();
        } else if (this.currentSpell == SpellType.STAR_TRACK_MAZE) {
            tickStarTrackMaze();
        }

        // 施法结束
        if (this.castTick >= this.currentSpell.castDuration) {
            executeSpell(this.currentSpell);
            // 法力扣减：统一入口，仅在此处扣减法术基础消耗
            // setCurrentMana 内部做边界钳位（Math.max(0, mana)），不会产生负值
            int newMana = Math.max(0, this.evoker.getManaData().getCurrentMana() - this.currentSpell.manaCost);
            this.evoker.setCurrentMana(newMana);
            this.cooldowns.put(this.currentSpell, this.currentSpell.cooldownTicks);
            this.nextCastAttempt = 100 + this.evoker.getRandom().nextInt(40);
            // 施法动画结束，清除攻击动画引用，让 idle_controller 接管
            this.evoker.currentAttackAnim = null;
            cleanupSpell();
            this.currentSpell = null;
        }
    }

    @Override
    public void stop() {
        cleanupSpell();
        this.currentSpell = null;
    }

    // 清理法术残留状态
    private void cleanupSpell() {
        // 夜幕黑洞：施法结束后移除引力触点
        if (this.singularity != null) {
            this.singularity.discard();
            this.singularity = null;
        }
        this.fateLinkTarget = null;
        this.fateLinkOrigin = null;
        this.starfallTarget = null;
        this.starfallLockedPos = null;
        this.starRailTarget = null;
        this.starRailDirection = null;
        this.starRailOrigin = null;
        this.throwGolem = null;
        this.throwTarget = null;
        this.mazeCenter = null;
        this.mazeActiveGroup = 0;
        this.beamDamageTimer = 0;
    }

    public int getSpellCooldown(SpellType spell) {
        return this.cooldowns.getOrDefault(spell, 0);
    }

    // 查找最近的非创造/非旁观模式玩家
    // getNearestPlayer 不自动过滤创造模式，需手动搜索
    // 统一过滤逻辑：BOSS 不应追踪无法攻击的玩家
    private Player findNearestSurvivalPlayer() {
        return findNearestSurvivalPlayer(null);
    }

    private Player findNearestSurvivalPlayer(ServerLevel serverLevel) {
        var level = serverLevel != null ? serverLevel : this.evoker.level();
        var players = level.getEntitiesOfClass(
                Player.class,
                this.evoker.getBoundingBox().inflate(64.0),
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
        );
        if (players.isEmpty()) return null;
        players.sort((a, b) -> Double.compare(this.evoker.distanceToSqr(a), this.evoker.distanceToSqr(b)));
        return players.get(0);
    }

    // ==================== 法术效果实现 ====================

    // 策略模式：通过 SpellType.executor 统一调用各法术的执行逻辑
    // 替代原有的 switch 语句，新增法术只需在 SpellType 枚举中注册即可
    private void executeSpell(SpellType spell) {
        if (spell.executor != null && this.evoker.level() instanceof ServerLevel serverLevel) {
            spell.executor.accept(this, serverLevel);
        }
    }

    // ==================== 星陨矩阵 ====================

    // 星陨矩阵粒子预警：在 1.5 秒施法期间，从目标头顶 30 格处向下发射 END_ROD 粒子流
    // 粒子密度随施法进度递增，营造"陨石逼近"的紧迫感
    // 使玩家在预警窗口内有明确的视觉线索指示危险位置，而非纯靠运气规避
    private static final int STARFALL_METEOR_HEIGHT = ModConstants.STARFALL_METEOR_HEIGHT;

    private void tickStarfallMatrix() {
        if (this.starfallTarget == null || this.starfallLockedPos == null) return;
        if (!(this.evoker.level() instanceof ServerLevel)) return;

        // 使用锁定位置（施法开始时玩家所在位置），非实时追踪
        // 玩家可以通过走位躲避陨石
        double targetX = this.starfallLockedPos.x;
        double targetZ = this.starfallLockedPos.z;
        double targetY = this.starfallLockedPos.y;
        double topY = targetY + STARFALL_METEOR_HEIGHT;

        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            // 修复：天空粒子柱从Y+30到Y+5，更清晰的陨石下落轨迹
            // 密度随施法进度递增，营造"陨石逼近"的紧迫感
            int particlesPerTick = 5 + this.castTick / 5;
            for (int i = 0; i < particlesPerTick; i++) {
                double t = this.evoker.getRandom().nextDouble();
                // 粒子从Y+30到Y+5分布，模拟陨石下落轨迹
                double py = (targetY + 5.0) + (topY - targetY - 5.0) * t;
                emitter.add(StellaParticles.ID_ASTRAL_BEAM, targetX + (this.evoker.getRandom().nextDouble() - 0.5) * 0.3, py, targetZ + (this.evoker.getRandom().nextDouble() - 0.5) * 0.3, 0);
            }

            // 修复：头顶预警圈替代脚下预警圈
            // 在目标玩家头顶Y+5到Y+10处画红色圆环，提示陨石即将从天而降
            int warningParticles = 6 + this.castTick / 5;
            for (int i = 0; i < warningParticles; i++) {
                double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                double r = ModConstants.STARFALL_RADIUS * (0.8 + this.evoker.getRandom().nextDouble() * 0.4);
                double px = targetX + Math.cos(angle) * r;
                double pz = targetZ + Math.sin(angle) * r;
                // 预警圆环在头顶Y+5到Y+10之间
                double warningY = targetY + 5.0 + this.evoker.getRandom().nextDouble() * 5.0;
                emitter.add(StellaParticles.ID_DYING_EMBER, px, warningY, pz, 0);
            }

            // 修复：最后10tick在Y+5处生成密集"陨石头部"粒子簇，模拟陨石即将砸到
            if (this.castTick > 20) {
                for (int i = 0; i < 8; i++) {
                    double ox = (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                    double oz = (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                    emitter.add(StellaParticles.ID_IMPACT_WAVE, targetX + ox, targetY + 5.0, targetZ + oz, 0);
                }
                // 陨石核心：密集的星界光束粒子
                for (int i = 0; i < 4; i++) {
                    double ox = (this.evoker.getRandom().nextDouble() - 0.5) * 0.8;
                    double oz = (this.evoker.getRandom().nextDouble() - 0.5) * 0.8;
                    emitter.add(StellaParticles.ID_ASTRAL_BEAM, targetX + ox, targetY + 4.5, targetZ + oz, 0);
                }
            }
        }
    }

    // 策略模式：静态方法，通过 SpellCastGoal 实例访问成员变量
    static void castStarfallMatrix(SpellCastGoal goal, ServerLevel serverLevel) {
        // 使用锁定位置（施法开始时玩家所在位置），非实时追踪
        if (goal.starfallLockedPos == null) return;

        double targetX = goal.starfallLockedPos.x;
        double targetZ = goal.starfallLockedPos.z;
        double targetY = goal.starfallLockedPos.y;

        // 爆炸视觉效果：纯粒子+音效替代 level.explode()
        // level.explode() 即使使用 ExplosionInteraction.NONE 仍会对范围内所有实体
        // （包括 StarcoreGolemEntity 傀儡）造成伤害和击退，违反"BOSS技能不得伤害傀儡"约束
        // 手动伤害逻辑已在下方 AABB 中精确控制，只对 Player 生效

        // 地面爆炸粒子：在锁定位置生成大量冲击波粒子（密度×3）
        try (ParticleEmitter emitter = new ParticleEmitter(goal.evoker)) {
            for (int i = 0; i < 120; i++) {
                double angle = goal.evoker.getRandom().nextDouble() * Math.PI * 2;
                double r = goal.evoker.getRandom().nextDouble() * ModConstants.STARFALL_RADIUS;
                double px = targetX + Math.cos(angle) * r;
                double pz = targetZ + Math.sin(angle) * r;
                emitter.add(StellaParticles.ID_IMPACT_WAVE, px, targetY + 0.3, pz, 0);
                emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, targetY + 0.5, pz, 0);
            }
        }

        // 屏幕震动
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(goal.evoker,
                new ClientboundScreenShakePacket(2.0F, 15, 0.1F));

        // 手动伤害：以锁定位置为中心，范围内的生物受到伤害
        // 非锁头：伤害判定基于位置而非追踪特定玩家
        // 排除 BOSS 自身和 StarcoreGolemEntity（BOSS技能不得伤害傀儡）
        AABB explosionBox = new AABB(
                targetX - ModConstants.STARFALL_RADIUS, targetY - ModConstants.STARFALL_RADIUS, targetZ - ModConstants.STARFALL_RADIUS,
                targetX + ModConstants.STARFALL_RADIUS, targetY + ModConstants.STARFALL_RADIUS, targetZ + ModConstants.STARFALL_RADIUS
        );
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, explosionBox,
                entity -> entity.isAlive() && !entity.isSpectator()
                        && entity != goal.evoker
                        && !(entity instanceof StarcoreGolemEntity));
        for (LivingEntity t : targets) {
            t.hurt(serverLevel.damageSources().indirectMagic(goal.evoker, goal.evoker), ModConstants.STARFALL_DAMAGE);
        }
    }

    // ==================== 星界发散光束 ====================

    // 圆锥形宽广光束扫过地面，持续 3 秒
    // 重写：从"朝玩家方向发射稀疏粒子"改为"灯塔式旋转扫射"
    // 核心机制：BOSS从空中向下发射宽广圆锥光束，光束方向在3秒内旋转360度
    // 形成"灯塔扫射"效果，玩家需要在光束扫过时走位躲避
    // 每10tick对当前锥形范围内的玩家造成一次魔法伤害（绕过盔甲）
    private void tickAstralBeam() {
        if (!(this.evoker.level() instanceof ServerLevel serverLevel)) return;

        beamDamageTimer++;

        // 旋转角度：60tick内旋转360度 = 每tick旋转6度
        // castTick从0到59，除以castDuration(60)得到0~1的进度
        double sweepAngle = ((double) this.castTick / this.currentSpell.castDuration) * Math.PI * 2;

        // 光束方向计算：
        // 水平分量随sweepAngle旋转，垂直分量固定向下倾斜
        // 倾斜角度50度，适配BOSS飞行高度6格
        // 当BOSS在Y+6处，50度倾斜时光束落点距BOSS水平距离约5格
        double tiltAngle = Math.toRadians(50);
        double beamX = Math.cos(sweepAngle) * Math.cos(tiltAngle);
        double beamY = -Math.sin(tiltAngle);
        double beamZ = Math.sin(sweepAngle) * Math.cos(tiltAngle);
        Vec3 beamDir = new Vec3(beamX, beamY, beamZ).normalize();

        Vec3 bossPos = this.evoker.position().add(0, -0.5, 0);

        // 计算光束落点（射线与地面的交点）
        // 地面高度近似取最近玩家的Y坐标，无玩家时取BOSS的Y-6
        double groundY;
        Player nearestPlayer = findNearestSurvivalPlayer(serverLevel);
        if (nearestPlayer != null) {
            groundY = nearestPlayer.getY();
        } else {
            groundY = this.evoker.getY() - 6.0;
        }

        double groundDist = beamDir.y < -0.01 ? (groundY - bossPos.y) / beamDir.y : -1;
        double groundX = groundDist > 0 ? bossPos.x + beamDir.x * groundDist : bossPos.x;
        double groundZ = groundDist > 0 ? bossPos.z + beamDir.z * groundDist : bossPos.z;

        // 计算垂直于光束方向的正交基，用于锥形扩散粒子的偏移
        // perp1和perp2构成垂直于beamDir的平面，粒子在该平面内扩散形成圆锥
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 perp1 = beamDir.cross(up);
        if (perp1.lengthSqr() < 0.001) {
            perp1 = beamDir.cross(new Vec3(1, 0, 0));
        }
        perp1 = perp1.normalize();
        Vec3 perp2 = beamDir.cross(perp1).normalize();

        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            // ===== 光束核心线 =====
            // 从BOSS到地面的明亮核心线，形成可见的"光柱"
            for (int i = 0; i < 12; i++) {
                double dist = 0.5 + i * 0.8;
                double px = bossPos.x + beamDir.x * dist;
                double py = bossPos.y + beamDir.y * dist;
                double pz = bossPos.z + beamDir.z * dist;
                emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 0);
            }

            // ===== 锥形扩散粒子 =====
            // 围绕核心线的扩散粒子，形成宽广的圆锥形光束
            // 偏移量在垂直于beamDir的平面内，随距离线性增大形成锥形
            for (int i = 0; i < 25; i++) {
                double dist = 1.0 + this.evoker.getRandom().nextDouble() * 9.0;
                double spread = dist * 0.2;
                double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                double r = this.evoker.getRandom().nextDouble() * spread;
                double offsetX = Math.cos(angle) * r;
                double offsetY = Math.sin(angle) * r;
                double px = bossPos.x + beamDir.x * dist + perp1.x * offsetX + perp2.x * offsetY;
                double py = bossPos.y + beamDir.y * dist + perp1.y * offsetX + perp2.y * offsetY;
                double pz = bossPos.z + beamDir.z * dist + perp1.z * offsetX + perp2.z * offsetY;
                emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 0);
            }

            // ===== 地面投影光斑 =====
            // 在光束落点处生成扩散的圆形光斑，营造"光束照射地面"的效果
            // 【范围匹配】地面光斑半径与光束锥形角度和射程匹配
            // BOSS在Y+6处，50度倾斜，光束落点处锥形覆盖半径约 6*tan(45°) ≈ 6 格
            // 使用 6.0 作为地面光斑半径，与BEAM_RANGE和BEAM_CONE_ANGLE一致
            if (groundDist > 0 && groundDist < ModConstants.BEAM_RANGE) {
                int groundParticles = 48 + this.evoker.getRandom().nextInt(16);
                double groundSpotRadius = 6.0;
                for (int i = 0; i < groundParticles; i++) {
                    double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                    double r = this.evoker.getRandom().nextDouble() * groundSpotRadius;
                    double px = groundX + Math.cos(angle) * r;
                    double pz = groundZ + Math.sin(angle) * r;
                    emitter.add(StellaParticles.ID_STELLA_WISP, px, groundY + 0.05, pz, 1);
                }
                // 中心亮点
                for (int i = 0; i < 5; i++) {
                    double ox = (this.evoker.getRandom().nextDouble() - 0.5) * 1.0;
                    double oz = (this.evoker.getRandom().nextDouble() - 0.5) * 1.0;
                    emitter.add(StellaParticles.ID_ASTRAL_BEAM, groundX + ox, groundY + 0.1, groundZ + oz, 0);
                }
            }
        }

        // ===== 光束启动音效 =====
        if (this.castTick == 1) {
            serverLevel.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 1.5F);
        }
        // 光束持续扫射音效：每20tick播放一次能量嗡鸣
        if (this.castTick % 20 == 0) {
            serverLevel.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.2F);
        }

        // ===== 伤害判定 =====
        if (beamDamageTimer >= BEAM_DAMAGE_INTERVAL) {
            beamDamageTimer = 0;

            // 使用当前光束方向判定锥形范围
            // 与isInCone不同，此处使用计算出的旋转光束方向而非实体的getLookAngle()
            // 排除 BOSS 自身和 StarcoreGolemEntity
            AABB beamBox = this.evoker.getBoundingBox().inflate(ModConstants.BEAM_RANGE);
            List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, beamBox,
                    entity -> entity.isAlive() && !entity.isSpectator()
                            && entity != this.evoker
                            && !(entity instanceof StarcoreGolemEntity)
                            && this.evoker.distanceTo(entity) <= ModConstants.BEAM_RANGE
                            && isInConeWithDir(this.evoker.position(), entity, beamDir, ModConstants.BEAM_CONE_ANGLE));

            for (LivingEntity target : targets) {
                // 使用魔法伤害源绕过盔甲减免
                target.hurt(serverLevel.damageSources().indirectMagic(this.evoker, this.evoker), ModConstants.BEAM_DAMAGE);
            }

            // 每次伤害判定时额外消耗法力
            this.evoker.setCurrentMana(
                    Math.max(0, this.evoker.getManaData().getCurrentMana() - ModConstants.BEAM_EXTRA_MANA_PER_SEC)
            );
        }
    }

    // 使用指定方向判定锥形范围
    // 与isInCone不同，此方法使用计算出的光束方向而非实体的getLookAngle()
    // 因为星界发散光束是旋转扫射的，方向由sweepAngle计算得出
    private boolean isInConeWithDir(Vec3 origin, LivingEntity target, Vec3 coneDir, double coneAngleDeg) {
        Vec3 toTarget = target.position().subtract(origin).normalize();
        double dot = coneDir.dot(toTarget);
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
        return angle <= Math.toRadians(coneAngleDeg / 2.0);
    }

    // ==================== 夜幕黑洞 ====================

    // 在目标玩家附近生成引力触点
    private void spawnSingularity() {
        if (!(this.evoker.level() instanceof ServerLevel serverLevel)) return;

        Player target = findNearestSurvivalPlayer(serverLevel);
        if (target == null) return;

        // 修复：在玩家附近（6-10 格偏移）生成黑洞，给玩家反应时间
        // 原偏移2-4格太近，玩家来不及反应就被吸入中心
        double offsetX = (this.evoker.getRandom().nextDouble() * 2.0 - 1.0) * (6.0 + this.evoker.getRandom().nextDouble() * 4.0);
        double offsetZ = (this.evoker.getRandom().nextDouble() * 2.0 - 1.0) * (6.0 + this.evoker.getRandom().nextDouble() * 4.0);

        NightfallSingularityEntity entity = ModEntities.NIGHTFALL_SINGULARITY.get().create(serverLevel);
        if (entity != null) {
            entity.moveTo(target.getX() + offsetX, target.getY(), target.getZ() + offsetZ);
            entity.setCaster(this.evoker);
            serverLevel.addFreshEntity(entity);
            this.singularity = entity;
        }
    }

    // ==================== 星命锁链 ====================

    // 持续渲染锁链粒子线，带有视觉预警系统
    // 前 1.5 秒：淡蓝色链环（温和提示）
    // 后 1.5 秒：猩红色链环 + 滴答声（高警示）
    //
    // 视觉设计（修复版）：
    //   - 连接点改为腰部（Y+0.8），避免第一人称糊脸
    //   - 删除所有"光晕"粒子，只保留链环本体
    //   - 链环使用 SMOKE_PARTICLE（小而实，不像光晕）
    //   - 链环之间有细线连接，使用 STAR_PARTICLE（小星形，有实体感）
    //   - 轻微垂直摆动，营造物理感
    private void tickFateLink() {
        if (this.fateLinkTarget == null || this.fateLinkOrigin == null) return;
        if (!(this.evoker.level() instanceof ServerLevel serverLevel)) return;

        // 跨维度孤儿引用断言
        if (this.fateLinkTarget.isRemoved() || this.fateLinkTarget.level() != this.evoker.level()) {
            this.fateLinkTarget = null;
            this.fateLinkOrigin = null;
            return;
        }

        // 从 BOSS 腰部到目标玩家腰部绘制锁链
        // 腰部高度约 Y+0.8，避免第一人称视角粒子糊脸
        Vec3 start = this.evoker.position().add(0, 0.8, 0);
        Vec3 end = this.fateLinkTarget.position().add(0, 0.8, 0);
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        if (distance < 0.1) return;
        direction = direction.normalize();

        // 终点截断在距玩家 1.5 格处，避免第一人称糊脸
        double effectiveDistance = Math.max(0, distance - 1.5);

        boolean isWarningPhase = this.castTick >= FATE_LINK_WARNING_THRESHOLD;

        // 链环参数
        double chainLinkSpacing = 1.2;
        int linkCount = (int) (effectiveDistance / chainLinkSpacing);

        // 轻微摆动
        double baseSway = Math.sin(this.castTick * 0.12) * 0.08;

        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int i = 0; i < linkCount; i++) {
                double t = (i + 1) * chainLinkSpacing / effectiveDistance;
                if (t > 1.0) break;

                double px = start.x + direction.x * effectiveDistance * t;
                double py = start.y + direction.y * effectiveDistance * t;
                double pz = start.z + direction.z * effectiveDistance * t;

                // 轻微摆动
                double swayOffset = baseSway * Math.sin(i * 0.7 + this.castTick * 0.15);
                py += swayOffset;

                if (isWarningPhase) {
                    // 后 1.5 秒：鲜红色链环（variant=1 更鲜红）
                    // variant=3：短生命周期（5 tick），快速消散不拖影
                    emitter.add(StellaParticles.ID_DYING_EMBER, px, py, pz, 3);
                } else {
                    // 前 1.5 秒：淡蓝色链环，使用 ASTRAL_BEAM（星空蓝，温和提示）
                    emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 0);
                }
            }

            // 连接线：在链环之间填充细粒子，增强"连续锁链"感
            // 每 0.4 格一个，使用 STAR_PARTICLE（小星形，有实体感但不大）
            int connectorCount = (int) (effectiveDistance / 0.4);
            for (int i = 0; i < connectorCount; i++) {
                double t = (i + 1) * 0.4 / effectiveDistance;
                if (t > 1.0) break;

                // 避开链环位置
                double linkPhase = (t * effectiveDistance) % chainLinkSpacing;
                if (linkPhase < 0.15 || linkPhase > chainLinkSpacing - 0.15) continue;

                double px = start.x + direction.x * effectiveDistance * t;
                double py = start.y + direction.y * effectiveDistance * t;
                double pz = start.z + direction.z * effectiveDistance * t;

                double swayOffset = baseSway * Math.sin(t * 8 + this.castTick * 0.15);
                py += swayOffset;

                if (isWarningPhase) {
                    emitter.add(StellaParticles.ID_DYING_EMBER, px, py, pz, 3);
                } else {
                    // 非预警连接线：淡蓝色 ASTRAL_BEAM
                    emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 0);
                }
            }
        }

        // 【预警色修复】预警阶段：每 3 tick 朝玩家身上发射纯红色粒子包裹
        // 使用 DustParticleOptions(RGB(255,0,0), 尺寸) 让玩家身体周围明显变红
        if (isWarningPhase && this.castTick % 3 == 0) {
            double targetX = this.fateLinkTarget.getX();
            double targetY = this.fateLinkTarget.getY();
            double targetZ = this.fateLinkTarget.getZ();
            DustParticleOptions redDust = new DustParticleOptions(
                    new Vector3f(1.0F, 0.0F, 0.0F), 1.2F);
            for (int i = 0; i < 4; i++) {
                double ox = (this.evoker.getRandom().nextDouble() - 0.5) * 0.8;
                double oy = this.evoker.getRandom().nextDouble() * 1.8;
                double oz = (this.evoker.getRandom().nextDouble() - 0.5) * 0.8;
                serverLevel.sendParticles(redDust,
                        targetX + ox, targetY + oy, targetZ + oz,
                        1, 0.0, 0.1, 0.0, 0.0);
            }
        }

        // 高警示阶段：伴随滴答声
        if (isWarningPhase && this.castTick % 10 == 0) {
            serverLevel.playSound(null,
                    this.fateLinkTarget.getX(), this.fateLinkTarget.getY(), this.fateLinkTarget.getZ(),
                    SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.HOSTILE,
                    1.0F, 0.5F + this.evoker.getRandom().nextFloat() * 0.5F);
        }
    }

    // 星命锁链施法结束时判定：玩家是否跑出 12 格
    // 策略模式：静态方法，通过 SpellCastGoal 实例访问成员变量
    // 使用 magic 伤害源绕过盔甲减免（星命锁链是魔法攻击，不是物理打击）
    static void executeFateLinkDamage(SpellCastGoal goal, ServerLevel serverLevel) {
        if (goal.fateLinkTarget == null || goal.fateLinkOrigin == null) return;

        // 跨维度孤儿引用断言：目标玩家已移除或不在同一维度时，跳过伤害判定
        if (goal.fateLinkTarget.isRemoved() || goal.fateLinkTarget.level() != goal.evoker.level()) {
            return;
        }

        // 确保目标玩家仍然存活
        if (!goal.fateLinkTarget.isAlive()) return;

        // 计算玩家当前位置与起始位置的距离
        double distFromOrigin = goal.fateLinkTarget.position().distanceTo(goal.fateLinkOrigin);

        if (distFromOrigin < FATE_LINK_MAX_DIST) {
            // 玩家未能跑出 12 格：受到魔法斩杀伤害（绕过盔甲）
            goal.fateLinkTarget.hurt(
                    serverLevel.damageSources().indirectMagic(goal.evoker, goal.evoker),
                    FATE_LINK_DAMAGE
            );
            // 斩杀音效：三叉戟刺入+末影龙惨叫，体现锁链收紧绞杀的恐怖
            serverLevel.playSound(null, goal.fateLinkTarget.getX(), goal.fateLinkTarget.getY(), goal.fateLinkTarget.getZ(),
                    SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 2.0F, 0.5F);
            serverLevel.playSound(null, goal.fateLinkTarget.getX(), goal.fateLinkTarget.getY(), goal.fateLinkTarget.getZ(),
                    SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 1.5F, 0.6F);
            // 斩杀特效：大量虚空火花粒子
            try (ParticleEmitter emitter = new ParticleEmitter(goal.evoker)) {
                for (int i = 0; i < 20; i++) {
                    double ox = (goal.evoker.getRandom().nextDouble() - 0.5) * 2.0;
                    double oy = goal.evoker.getRandom().nextDouble() * 2.0;
                    double oz = (goal.evoker.getRandom().nextDouble() - 0.5) * 2.0;
                    emitter.add(StellaParticles.ID_VOID_SPARK, goal.fateLinkTarget.getX() + ox, goal.fateLinkTarget.getY() + oy, goal.fateLinkTarget.getZ() + oz, 0);
                }
            }
        } else {
            // 玩家成功跑出 12 格：锁链断裂特效
            // 锁链断裂音效：玻璃碎裂，体现虚空锁链崩断
            serverLevel.playSound(null, goal.fateLinkTarget.getX(), goal.fateLinkTarget.getY(), goal.fateLinkTarget.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5F, 1.0F);
            try (ParticleEmitter emitter = new ParticleEmitter(goal.evoker)) {
                for (int i = 0; i < 12; i++) {
                    double ox = (goal.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                    double oy = goal.evoker.getRandom().nextDouble() * 1.5;
                    double oz = (goal.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                    emitter.add(StellaParticles.ID_STELLA_WISP, goal.fateLinkTarget.getX() + ox, goal.fateLinkTarget.getY() + oy, goal.fateLinkTarget.getZ() + oz, 0);
                }
            }
        }
    }

    // ==================== 星轨切割 ====================

    // 蓝图 A.5：地面星轨预警，2秒后爆发高能激光
    // 施法 40 tick：前 30 tick 地面画预警线，后 10 tick 激光爆发
    // 预警线使用死亡余烬粒子（暗红色），激光使用星界光束粒子（亮蓝色）
    private void tickStarRailCut() {
        if (this.starRailTarget == null || this.starRailDirection == null || this.starRailOrigin == null) return;
        if (!(this.evoker.level() instanceof ServerLevel serverLevel)) return;

        // 跨维度孤儿引用断言
        if (this.starRailTarget.isRemoved() || this.starRailTarget.level() != this.evoker.level()) {
            this.starRailTarget = null;
            return;
        }

        // 非锁头设计：使用施法开始时锁定的固定方向，不实时追踪玩家
        // 玩家可以通过走位躲避激光，而不是被追踪
        double originX = this.starRailOrigin.x;
        double originY = this.starRailOrigin.y;
        double originZ = this.starRailOrigin.z;
        double dirX = this.starRailDirection.x;
        double dirZ = this.starRailDirection.z;

        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            if (this.castTick < 30) {
                // 修复：预警线使用STELLA_WISP粒子（淡紫色），间隔1格，沿方向延伸20格
                // 粒子密度随时间递增，越接近激光爆发越密集
                int warningDensity = 4 + this.castTick / 3;
                for (int i = 0; i < warningDensity; i++) {
                    double dist = (i + 1) * 1.0;
                    if (dist > ModConstants.STAR_RAIL_CUT_LENGTH) break;
                    double px = originX + dirX * dist + (this.evoker.getRandom().nextDouble() - 0.5) * 0.3;
                    double pz = originZ + dirZ * dist + (this.evoker.getRandom().nextDouble() - 0.5) * 0.3;
                    emitter.add(StellaParticles.ID_STELLA_WISP, px, originY, pz, 0);
                }

                // 预警音效：每 10 tick 一次滴答声
                if (this.castTick % 10 == 0) {
                    serverLevel.playSound(null,
                            this.starRailTarget.getX(), this.starRailTarget.getY(), this.starRailTarget.getZ(),
                            SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.HOSTILE,
                            0.8F, 0.8F + this.evoker.getRandom().nextFloat() * 0.4F);
                }
            } else {
                // 修复：激光爆发使用ASTRAL_BEAM粒子（亮蓝色），Y在地面+0.5，宽度1.5格
                // 粒子间隔从1.0格缩短到0.3格，密度×3提升激光可见度
                for (int i = 0; i < 60; i++) {
                    double dist = i * 0.3;
                    if (dist > ModConstants.STAR_RAIL_CUT_LENGTH) break;
                    double px = originX + dirX * dist;
                    double pz = originZ + dirZ * dist;
                    // 激光主体：亮蓝色，宽度1.5格
                    emitter.add(StellaParticles.ID_ASTRAL_BEAM, px + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5, originY, pz + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5, 0);
                    // 激光边缘：冲击波粒子
                    emitter.add(StellaParticles.ID_IMPACT_WAVE, px + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5, originY, pz + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5, 0);
                }
            }
        }
    }

    // 星轨切割：激光爆发伤害判定
    // 沿激光方向的长条形 AABB 检测玩家
    static void executeStarRailCut(SpellCastGoal goal, ServerLevel serverLevel) {
        if (goal.starRailTarget == null || goal.starRailDirection == null || goal.starRailOrigin == null) return;

        double originX = goal.starRailOrigin.x;
        double originY = goal.starRailOrigin.y;
        double originZ = goal.starRailOrigin.z;
        double dirX = goal.starRailDirection.x;
        double dirZ = goal.starRailDirection.z;
        double length = ModConstants.STAR_RAIL_CUT_LENGTH;
        double width = ModConstants.STAR_RAIL_CUT_WIDTH;

        // 激光伤害 AABB：沿方向延伸的长条形
        double endX = originX + dirX * length;
        double endZ = originZ + dirZ * length;
        AABB laserBox = new AABB(
                Math.min(originX, endX) - width, originY - 1.0, Math.min(originZ, endZ) - width,
                Math.max(originX, endX) + width, originY + 3.0, Math.max(originZ, endZ) + width
        );

        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, laserBox,
                entity -> entity.isAlive() && !entity.isSpectator()
                        && entity != goal.evoker
                        && !(entity instanceof StarcoreGolemEntity));

        for (LivingEntity target : targets) {
            target.hurt(serverLevel.damageSources().indirectMagic(goal.evoker, goal.evoker), ModConstants.STAR_RAIL_CUT_DAMAGE);
        }

        // 激光结束大爆炸粒子
        try (ParticleEmitter emitter = new ParticleEmitter(goal.evoker)) {
            for (int i = 0; i < 50; i++) {
                double dist = goal.evoker.getRandom().nextDouble() * length;
                double px = originX + dirX * dist + (goal.evoker.getRandom().nextDouble() - 0.5) * 1.0;
                double pz = originZ + dirZ * dist + (goal.evoker.getRandom().nextDouble() - 0.5) * 1.0;
                emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, originY + goal.evoker.getRandom().nextDouble() * 1.5, pz, 0);
            }
        }

        // 屏幕震动
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(goal.evoker,
                new ClientboundScreenShakePacket(1.5F, 10, 0.08F));

        // 星轨切割激光爆发音效：信标充能+末影龙怒吼，体现高能激光释放
        serverLevel.playSound(null, originX, originY, originZ,
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 1.5F);
        serverLevel.playSound(null, originX, originY, originZ,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.5F, 1.2F);
    }

    // ==================== 念力投掷 ====================

    // 蓝图 B.7：BOSS 抓起充能完毕待命的傀儡砸向玩家，落地星尘爆炸
    // 施法 20 tick：傀儡被吸向 BOSS，然后砸向目标玩家位置
    private void tickTelekineticThrow() {
        if (this.throwGolem == null || this.throwTarget == null) return;
        if (!(this.evoker.level() instanceof ServerLevel)) return;

        // 傀儡已被移除时取消
        if (this.throwGolem.isRemoved() || !this.throwGolem.isAlive()) {
            this.throwGolem = null;
            return;
        }

        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            // 前 10 tick：傀儡被吸向 BOSS 位置（念力抓取动画）
            if (this.castTick < 10) {
                double lerpFactor = 0.3;
                double targetX = this.evoker.getX();
                double targetY = this.evoker.getY() - 1.0;
                double targetZ = this.evoker.getZ();
                double newX = this.throwGolem.getX() + (targetX - this.throwGolem.getX()) * lerpFactor;
                double newY = this.throwGolem.getY() + (targetY - this.throwGolem.getY()) * lerpFactor;
                double newZ = this.throwGolem.getZ() + (targetZ - this.throwGolem.getZ()) * lerpFactor;
                this.throwGolem.teleportTo(newX, newY, newZ);
                this.throwGolem.setNoGravity(true);
                this.throwGolem.setDeltaMovement(Vec3.ZERO);

                // 念力抓取粒子：傀儡周围的虚空能量
                for (int i = 0; i < 4; i++) {
                    double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                    double r = 0.5 + this.evoker.getRandom().nextDouble() * 0.5;
                    double px = this.throwGolem.getX() + Math.cos(angle) * r;
                    double pz = this.throwGolem.getZ() + Math.sin(angle) * r;
                    emitter.add(StellaParticles.ID_VOID_SPARK, px, this.throwGolem.getY() + this.evoker.getRandom().nextDouble() * 1.0, pz, 0);
                }
            }

            // 后 10 tick：蓄力阶段，傀儡在 BOSS 手中旋转
            if (this.castTick >= 10) {
                double angle = (this.castTick - 10) * 0.5;
                double r = 1.0;
                double px = this.evoker.getX() + Math.cos(angle) * r;
                double pz = this.evoker.getZ() + Math.sin(angle) * r;
                this.throwGolem.teleportTo(px, this.evoker.getY() - 1.0, pz);

                // 蓄力粒子
                for (int i = 0; i < 6; i++) {
                    double pAngle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                    double pr = 0.3 + this.evoker.getRandom().nextDouble() * 0.8;
                    double ppx = px + Math.cos(pAngle) * pr;
                    double ppz = pz + Math.sin(pAngle) * pr;
                    emitter.add(StellaParticles.ID_STELLA_WISP, ppx, this.evoker.getY() - 0.5 + this.evoker.getRandom().nextDouble() * 1.0, ppz, 0);
                }
            }
        }
    }

    // 念力投掷：将傀儡砸向目标玩家位置，落地爆炸
    static void executeTelekineticThrow(SpellCastGoal goal, ServerLevel serverLevel) {
        if (goal.throwGolem == null || goal.throwTarget == null) return;
        if (goal.throwGolem.isRemoved() || !goal.throwGolem.isAlive()) return;

        // 将傀儡传送到目标玩家位置上方
        double targetX = goal.throwTarget.getX();
        double targetZ = goal.throwTarget.getZ();
        double targetY = goal.throwTarget.getY() + 8.0;

        goal.throwGolem.teleportTo(targetX, targetY, targetZ);
        goal.throwGolem.setNoGravity(false);
        goal.throwGolem.setDeltaMovement(0, -2.0, 0);

        try (ParticleEmitter emitter = new ParticleEmitter(goal.evoker)) {
            // 投掷轨迹粒子
            for (int i = 0; i < 20; i++) {
                double t = goal.evoker.getRandom().nextDouble();
                double px = goal.evoker.getX() + (targetX - goal.evoker.getX()) * t + (goal.evoker.getRandom().nextDouble() - 0.5) * 1.0;
                double pz = goal.evoker.getZ() + (targetZ - goal.evoker.getZ()) * t + (goal.evoker.getRandom().nextDouble() - 0.5) * 1.0;
                double py = goal.evoker.getY() + (targetY - goal.evoker.getY()) * t;
                emitter.add(StellaParticles.ID_STELLA_WISP, px, py, pz, 0);
            }
        }

        // 念力投掷音效：傀儡抛出时的呼啸声
        serverLevel.playSound(null, goal.evoker.getX(), goal.evoker.getY(), goal.evoker.getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.5F, 0.6F);

        // 延迟爆炸视觉效果：纯粒子+音效替代 level.explode()
        // level.explode() 即使 ExplosionInteraction.NONE 仍会对傀儡造成伤害
        // 手动伤害逻辑已在下方 AABB 中精确控制，只对 Player 生效

        // 爆炸伤害
        AABB explosionBox = new AABB(
                targetX - ModConstants.TELEKINETIC_THROW_RADIUS,
                goal.throwTarget.getY() - ModConstants.TELEKINETIC_THROW_RADIUS,
                targetZ - ModConstants.TELEKINETIC_THROW_RADIUS,
                targetX + ModConstants.TELEKINETIC_THROW_RADIUS,
                goal.throwTarget.getY() + ModConstants.TELEKINETIC_THROW_RADIUS,
                targetZ + ModConstants.TELEKINETIC_THROW_RADIUS
        );
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, explosionBox,
                entity -> entity.isAlive() && !entity.isSpectator()
                        && entity != goal.evoker
                        && !(entity instanceof StarcoreGolemEntity));
        for (LivingEntity t : targets) {
            t.hurt(serverLevel.damageSources().indirectMagic(goal.evoker, goal.evoker), ModConstants.TELEKINETIC_THROW_DAMAGE);
        }

        // 星尘爆炸粒子
        try (ParticleEmitter emitter = new ParticleEmitter(goal.evoker)) {
            for (int i = 0; i < 50; i++) {
                double angle = goal.evoker.getRandom().nextDouble() * Math.PI * 2;
                double r = goal.evoker.getRandom().nextDouble() * ModConstants.TELEKINETIC_THROW_RADIUS;
                double px = targetX + Math.cos(angle) * r;
                double pz = targetZ + Math.sin(angle) * r;
                emitter.add(StellaParticles.ID_STELLA_WISP, px, goal.throwTarget.getY() + 0.3, pz, 0);
                emitter.add(StellaParticles.ID_IMPACT_WAVE, px, goal.throwTarget.getY() + 0.1, pz, 1);
            }
        }

        // 屏幕震动
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(goal.evoker,
                new ClientboundScreenShakePacket(2.0F, 12, 0.1F));

        // 傀儡在爆炸中消散
        goal.throwGolem.discard();
    }

    // ==================== 星轨迷宫 ====================

    // 星轨迷宫：地面网格奇偶列交替伤害
    // 前 40 tick 预警：地面画全网格（淡蓝细线粒子）
    // 40-120 tick：奇偶列交替，每 10 tick 切换
    // 激活列蓝色发光粒子 + 伤害，非激活列暗色
    private static final int MAZE_GRID_SIZE = ModConstants.STAR_TRACK_MAZE_GRID_SIZE;
    private static final float MAZE_DAMAGE = ModConstants.STAR_TRACK_MAZE_DAMAGE;
    // 预警阶段持续时间（tick）
    private static final int MAZE_WARNING_TICKS = 40;
    // 列切换间隔（tick）
    private static final int MAZE_SWITCH_INTERVAL = 10;
    // 网格间距（格）
    private static final double MAZE_CELL_SIZE = 2.0;

    private void tickStarTrackMaze() {
        if (this.mazeCenter == null) return;
        if (!(this.evoker.level() instanceof ServerLevel serverLevel)) return;

        double cx = this.mazeCenter.x;
        double cz = this.mazeCenter.z;
        double cy = this.mazeCenter.y;
        int halfGrid = MAZE_GRID_SIZE / 2;

        // 预警阶段（0-40 tick）：画全网格淡蓝细线
        if (this.castTick < MAZE_WARNING_TICKS) {
            try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
                // 纵向线（沿 Z 轴）
                for (int col = -halfGrid; col <= halfGrid; col++) {
                    double x = cx + col * MAZE_CELL_SIZE;
                    for (int row = 0; row < 20; row++) {
                        double z = cz - halfGrid * MAZE_CELL_SIZE + row * (MAZE_GRID_SIZE * MAZE_CELL_SIZE / 20.0);
                        emitter.add(StellaParticles.ID_ASTRAL_BEAM, x, cy + 0.05, z, 0);
                    }
                }
                // 横向线（沿 X 轴）
                for (int row = -halfGrid; row <= halfGrid; row++) {
                    double z = cz + row * MAZE_CELL_SIZE;
                    for (int col = 0; col < 20; col++) {
                        double x = cx - halfGrid * MAZE_CELL_SIZE + col * (MAZE_GRID_SIZE * MAZE_CELL_SIZE / 20.0);
                        emitter.add(StellaParticles.ID_ASTRAL_BEAM, x, cy + 0.05, z, 0);
                    }
                }
            }
            return;
        }

        // 激活阶段（40-120 tick）：奇偶列交替
        int activeTick = this.castTick - MAZE_WARNING_TICKS;
        // 每 10 tick 切换激活列组
        this.mazeActiveGroup = (activeTick / MAZE_SWITCH_INTERVAL) % 2;

        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int col = -halfGrid; col <= halfGrid; col++) {
                boolean isActive = ((col + halfGrid) % 2 == this.mazeActiveGroup);
                double x = cx + col * MAZE_CELL_SIZE;

                for (int row = -halfGrid; row <= halfGrid; row++) {
                    double z = cz + row * MAZE_CELL_SIZE;

                    if (isActive) {
                        // 激活列：蓝色发光粒子（交点密集×3 + 额外亮度）
                        emitter.add(StellaParticles.ID_ASTRAL_BEAM, x, cy + 0.1, z, 0);
                        emitter.add(StellaParticles.ID_ASTRAL_BEAM, x, cy + 0.1, z, 1);
                        emitter.add(StellaParticles.ID_ASTRAL_BEAM, x, cy + 0.15, z, 0);
                        emitter.add(StellaParticles.ID_ASTRAL_BEAM, x, cy + 0.15, z, 1);
                        emitter.add(StellaParticles.ID_VOID_TWINKLE, x, cy + 0.2, z, 1);
                        emitter.add(StellaParticles.ID_VOID_TWINKLE, x, cy + 0.2, z, 1);
                    } else {
                        // 非激活列：暗色稀疏粒子
                        if ((row + halfGrid) % 2 == 0) {
                            emitter.add(StellaParticles.ID_VOID_TWINKLE, x, cy + 0.05, z, 0);
                        }
                    }
                }
            }
        }

        // 伤害判定：每 10 tick 的首 tick 对激活列上的玩家造成伤害
        if (activeTick % MAZE_SWITCH_INTERVAL == 0) {
            for (int col = -halfGrid; col <= halfGrid; col++) {
                boolean isActive = ((col + halfGrid) % 2 == this.mazeActiveGroup);
                if (!isActive) continue;

                double x = cx + col * MAZE_CELL_SIZE;
                // 激活列的 AABB 判定区域（宽 1 格，高 2 格）
                AABB colBox = new AABB(
                        x - MAZE_CELL_SIZE * 0.4, cy - 1.0, cz - halfGrid * MAZE_CELL_SIZE,
                        x + MAZE_CELL_SIZE * 0.4, cy + 2.0, cz + halfGrid * MAZE_CELL_SIZE
                );
                List<LivingEntity> hitEntities = serverLevel.getEntitiesOfClass(LivingEntity.class, colBox,
                        entity -> entity.isAlive() && !entity.isSpectator()
                                && entity != this.evoker
                                && !(entity instanceof StarcoreGolemEntity));
                for (LivingEntity entity : hitEntities) {
                    entity.hurt(serverLevel.damageSources().indirectMagic(this.evoker, this.evoker), MAZE_DAMAGE);
                }
            }
        }

        // 发送网络同步包给客户端渲染器
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this.evoker,
                new ClientboundMazeSyncPacket(cx, cy, cz, this.mazeActiveGroup, MAZE_GRID_SIZE));
    }

    // 星轨迷宫执行阶段：施法结束时的收尾效果
    static void executeStarTrackMaze(SpellCastGoal goal, ServerLevel serverLevel) {
        // 迷宫在 tick 中已持续处理，execute 阶段播放收尾音效
        if (goal.mazeCenter != null) {
            serverLevel.playSound(null, goal.mazeCenter.x, goal.mazeCenter.y, goal.mazeCenter.z,
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.0F, 0.5F);
        }
    }
}
