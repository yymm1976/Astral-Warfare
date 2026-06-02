package com.mochi_753.astral_warfare.entity.ai;

import com.mochi_753.astral_warfare.init.ModEffects;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// ═══════════════════════════════════════════════════════════════════════
// 二阶段三段普攻 AI Goal（Phase2MeleeGoal）
// ═══════════════════════════════════════════════════════════════════════
//
// 【这是BOSS的常规近战攻击，不是终结技！】
//
// 三段连招流程：
//   第一段：虚空弦斩 (Void String Slash)
//     → BOSS单手挥动，前方90度扇形裂缝，0.5秒延迟后闭合伤害
//     → 逼迫玩家横向走位
//
//   第二段：星界突进掌 (Astral Thrust)
//     → BOSS化作紫色残影向前瞬移3-4格，单手刺出虚空能量
//     → 轻微击退，调整玩家位置为第三段做准备
//
//   第三段：背刺·虚空贯穿 (Backstab: Void Pierce)
//     → BOSS消失（玻璃碎裂音效），0.3秒后出现在玩家背后1格
//     → 双手凝聚虚空长矛刺入背部，巨额伤害 + 虚空流血（DoT）
//     → 玩家必须在第二段结束后立刻跑开或举盾才能躲避
//
// 远距离拉人：玩家8-25格时传送玩家到身前接重拳
//
// ═══════════════════════════════════════════════════════════════════════
// 【与终结技（DespairExecutionGoal）的严格边界】
// ═══════════════════════════════════════════════════════════════════════
//
// 三段普攻（本Goal）                  终结技（DespairExecutionGoal）
// ─────────────────────────────      ─────────────────────────────
// 触发：玩家在3格内                   触发：CD+血量阈值+范围
// 效果：施加虚空流血（DoT）            效果：击飞+虚空禁锢+砸地
// 流程：斩→突→背刺                    流程：击飞→瞬移上方→蓄力→砸地
// 频率：常规近战行为                   频率：罕见（10秒CD+35%血量）
// 优先级：2（常规攻击）               优先级：1（特殊技）
// Flag：MOVE+LOOK（互斥终结技）       Flag：MOVE+LOOK（互斥三段普攻）
//
// 【关键】三段普攻施加虚空流血（DoT），绝不施加虚空禁锢
//         终结技有独立的触发条件（CD+血量），不依赖任何外部效果
//         两者共享 MOVE+LOOK Flag，同一时刻只能运行一个
// ═══════════════════════════════════════════════════════════════════════
//
// 状态机：IDLE → SLASH_DELAY → SLASH → THRUST_DASH → THRUST_HIT →
//          BACKSTAB_VANISH → BACKSTAB_STRIKE → COOLDOWN
//          或 IDLE → PULL_WINDUP → PULL_STRIKE → COOLDOWN
public class Phase2MeleeGoal extends Goal {

    private final StellaEvokerEntity evoker;
    private State state = State.IDLE;
    private int stateTimer = 0;
    private LivingEntity target = null;
    // 三段连招冷却：30tick = 1.5秒（连招间短暂停顿，不应长时间站桩）
    private int comboCooldownTimer = 0;
    private static final int COMBO_COOLDOWN = 30;
    // 拉人冷却：60tick = 3秒
    private int pullCooldownTimer = 0;
    private static final int PULL_COOLDOWN = 60;
    // 近战攻击距离
    // 从 3.0 增大到 5.0，扩大普攻触发范围
    private static final double ATTACK_REACH = 5.0;
    // 拉人触发距离范围
    // 从 8.0~25.0 增大到 10.0~35.0，扩大拉人触发范围
    private static final double PULL_MIN_DIST = 10.0;
    private static final double PULL_MAX_DIST = 35.0;
    // 寻路重算间隔
    private int pathRecalcTimer = 0;

    // 各阶段持续时间
    // 【调整】整体放慢三段普攻节奏，增加打击感和可反应窗口
    private static final int SLASH_DELAY_TICKS = 15;
    private static final int THRUST_DASH_TICKS = 8;
    private static final int BACKSTAB_VANISH_TICKS = 10;
    private static final int PULL_WINDUP_TICKS = 15;
    // 弦斩持续帧：延长动画展示时间，让玩家能看清斩击
    private static final int SLASH_DURATION_TICKS = 15;

    // 伤害值（二阶段伤害大幅加强）
    private static final float SLASH_DAMAGE = 16.0F;
    private static final float THRUST_DAMAGE = 12.0F;
    private static final float BACKSTAB_DAMAGE = 28.0F;
    private static final float PULL_PUNCH_DAMAGE = 30.0F;

    // 虚空流血持续时间：6秒 = 120tick
    private static final int VOID_BLEED_DURATION = 120;
    // 背刺最大范围：如果玩家在消失阶段跑出此范围，背刺落空
    // 从 12.0 增大到 18.0，扩大背刺追击范围
    private static final double BACKSTAB_MAX_RANGE = 18.0;

    public Phase2MeleeGoal(StellaEvokerEntity evoker) {
        this.evoker = evoker;
        this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.evoker.getCombatPhase() != StellaEvokerEntity.PHASE_2_MELEE) return false;
        if (this.evoker.isTransitioning()) return false;
        if (this.evoker.isDying()) return false;
        if (this.state != State.IDLE) return false;
        LivingEntity target = this.evoker.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.evoker.getCombatPhase() != StellaEvokerEntity.PHASE_2_MELEE) return false;
        if (this.evoker.isTransitioning()) return false;
        if (this.evoker.isDying()) return false;
        // 连招进行中即使目标死了也继续动画
        if (this.state != State.IDLE) return true;
        LivingEntity target = this.evoker.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void tick() {
        this.target = this.evoker.getTarget();
        if (this.target == null) {
            resetToIdle();
            return;
        }

        if (!(this.evoker.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 冷却递减
        if (this.comboCooldownTimer > 0) this.comboCooldownTimer--;
        if (this.pullCooldownTimer > 0) this.pullCooldownTimer--;

        this.stateTimer++;

        switch (this.state) {
            case IDLE -> tickIdle(serverLevel);
            case SLASH_DELAY -> tickSlashDelay(serverLevel);
            case SLASH -> tickSlash(serverLevel);
            case THRUST_DASH -> tickThrustDash(serverLevel);
            case THRUST_HIT -> tickThrustHit(serverLevel);
            case BACKSTAB_VANISH -> tickBackstabVanish(serverLevel);
            case BACKSTAB_STRIKE -> tickBackstabStrike(serverLevel);
            case PULL_WINDUP -> tickPullWindup(serverLevel);
            case PULL_STRIKE -> tickPullStrike(serverLevel);
            case COOLDOWN -> tickCooldown();
        }
    }

    // 空闲状态：寻路到目标，判断进入连招还是拉人
    // 修复：导航失败时使用 MoveControl 回退方案直接走向玩家
    // 原因：GroundPathNavigation 在 BOSS 刚落地或复杂地形时可能返回 null 路径
    //       导致 BOSS 原地不动，看起来像"没有仇恨"
    private void tickIdle(ServerLevel level) {
        if (this.target == null || !this.target.isAlive()) {
            resetToIdle();
            return;
        }

        // 面向目标
        this.evoker.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        // 持续寻路到目标：优先使用导航系统，失败时回退到直接移动
        pathRecalcTimer--;
        if (pathRecalcTimer <= 0) {
            pathRecalcTimer = 4 + this.evoker.getRandom().nextInt(4);
            var path = this.evoker.getNavigation().createPath(this.target, 0);
            if (path != null) {
                this.evoker.getNavigation().moveTo(path, 1.0);
            } else {
                // 回退方案：导航失败时直接设置移动目标位置
                // MoveControl 会直线走向目标，不绕障碍，但至少保证 BOSS 会追人
                this.evoker.getNavigation().stop();
                this.evoker.getMoveControl().setWantedPosition(
                        this.target.getX(), this.target.getY(), this.target.getZ(), 1.0);
            }
        }

        double distToTarget = this.evoker.distanceTo(this.target);

        // 判断是否进入拉人（远距离10-35格）
        if (distToTarget > PULL_MIN_DIST && distToTarget < PULL_MAX_DIST
                && this.pullCooldownTimer <= 0 && this.comboCooldownTimer <= 0) {
            this.state = State.PULL_WINDUP;
            this.stateTimer = 0;
            this.evoker.getNavigation().stop();
            return;
        }

        // 判断是否进入三段连招（近距离5格内）
        if (distToTarget <= ATTACK_REACH && this.comboCooldownTimer <= 0) {
            this.state = State.SLASH_DELAY;
            this.stateTimer = 0;
            this.evoker.getNavigation().stop();
        }
    }

    // ==================== 第一段：虚空弦斩 ====================

    // 蓄力阶段：BOSS看向目标，虚空能量在手部聚集
    // 加强版：更多粒子、更大范围、更丰富的视觉效果
    private void tickSlashDelay(ServerLevel level) {
        this.evoker.getLookControl().setLookAt(this.target, 180.0F, 180.0F);

        // 首次进入蓄力阶段时触发弦斩动画
        if (this.stateTimer == 1) {
            this.evoker.currentAttackAnim = "stella_evoker_slash";
        }

        // 蓄力粒子：虚空能量在BOSS手部聚集（从2个增加到4个）
        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int i = 0; i < 4; i++) {
                double px = this.evoker.getX() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.0;
                double py = this.evoker.getY() + 1.5 + this.evoker.getRandom().nextDouble() * 0.5;
                double pz = this.evoker.getZ() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.0;
                emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 0);
            }
            // 新增：星穹微光环绕蓄力
            for (int i = 0; i < 2; i++) {
                double px = this.evoker.getX() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.2;
                double py = this.evoker.getY() + 1.0 + this.evoker.getRandom().nextDouble() * 0.8;
                double pz = this.evoker.getZ() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.2;
                emitter.add(StellaParticles.ID_STELLA_WISP, px, py, pz, 1);
            }
        }

        // 原版附魔粒子：蓄力时的符文飞入效果（仪式感，数量加倍）
        if (this.stateTimer % 2 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    this.evoker.getX(), this.evoker.getY() + 1.0, this.evoker.getZ(),
                    4, 0.8, 0.5, 0.8, 0.5);
        }

        if (this.stateTimer >= SLASH_DELAY_TICKS) {
            this.state = State.SLASH;
            this.stateTimer = 0;
        }
    }

    // 弦斩阶段：10tick持续动画，首tick伤害判定，全程弧形刃光粒子
    // 加强版：更多弧形刃光、更大范围、更丰富的视觉效果
    // 【范围匹配】弧形刃光最大距离 = 1.5 + 4*1.5 = 7.5，伤害判定统一为 7.5
    private static final double SLASH_DAMAGE_RANGE = 7.5;

    private void tickSlash(ServerLevel level) {
        Vec3 look = this.evoker.getLookAngle();
        double baseYaw = Math.atan2(look.z, look.x);

        // 首tick：伤害判定 + 原版横扫攻击粒子（弧形斩击）
        if (this.stateTimer == 1) {
            if (this.target != null && this.target.isAlive()) {
                double distToTarget = this.evoker.distanceTo(this.target);
                // 伤害判定范围与特效弧形刃光最大距离匹配（7.5格）
                if (distToTarget <= SLASH_DAMAGE_RANGE && isInCone(this.evoker, this.target, 90.0)) {
                    this.target.hurt(level.damageSources().mobAttack(this.evoker), SLASH_DAMAGE);
                }
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    this.evoker.getX() + look.x * 2.0,
                    this.evoker.getY() + 1.0,
                    this.evoker.getZ() + look.z * 2.0,
                    1, 0.0, 0.0, 0.0, 0.0);
            // 虚空弦斩音效：高频撕裂声，体现虚空刃的锐利与速度感
            level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 1.2F);
        }

        // 全程：弧形刃光（IMPACT_WAVE），5道连斩，每道10分段
        // 最外圈距离 1.5 + 4*1.5 = 7.5，与 SLASH_DAMAGE_RANGE 一致
        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int slash = 0; slash < 5; slash++) {
                double slashDist = 1.5 + slash * 1.5;
                int arcSegments = 10;
                for (int i = 0; i < arcSegments; i++) {
                    double arcAngle = baseYaw + (i / (double) arcSegments - 0.5) * Math.PI * 0.9;
                    double px = this.evoker.getX() + Math.cos(arcAngle) * slashDist;
                    double py = this.evoker.getY() + 0.8 + (slash == 1 ? 0.3 : 0);
                    double pz = this.evoker.getZ() + Math.sin(arcAngle) * slashDist;
                    emitter.add(StellaParticles.ID_IMPACT_WAVE, px, py, pz, slash);
                }
            }
            // 新增：虚空火花尾随刃光
            for (int i = 0; i < 3; i++) {
                double angle = baseYaw + (this.evoker.getRandom().nextDouble() - 0.5) * Math.PI * 0.6;
                double dist = 2.0 + this.evoker.getRandom().nextDouble() * 4.0;
                double px = this.evoker.getX() + Math.cos(angle) * dist;
                double pz = this.evoker.getZ() + Math.sin(angle) * dist;
                emitter.add(StellaParticles.ID_VOID_SPARK, px, this.evoker.getY() + 0.8, pz, 0);
            }
        }

        if (this.stateTimer >= SLASH_DURATION_TICKS) {
            this.state = State.THRUST_DASH;
            this.stateTimer = 0;
        }
    }

    // ==================== 第二段：星界突进掌 ====================

    // 突进阶段：BOSS化作紫色残影向前瞬移5-7格
    // 加强版：更长的瞬移距离、更丰富的残影和落点特效
    private void tickThrustDash(ServerLevel level) {
        if (this.stateTimer == 1) {
            // 触发突进掌动画
            this.evoker.currentAttackAnim = "stella_evoker_thrust";

            Vec3 toTarget = this.target.position().subtract(this.evoker.position()).normalize();
            // 突进距离从3.5增大到6.0
            double dashDist = 6.0;
            double newX = this.evoker.getX() + toTarget.x * dashDist;
            double newZ = this.evoker.getZ() + toTarget.z * dashDist;

            // 瞬移路径残影（从4个增加到8个，更密集）
            try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
                for (int i = 0; i < 8; i++) {
                    double t = i / 8.0;
                    double px = this.evoker.getX() + toTarget.x * dashDist * t;
                    double pz = this.evoker.getZ() + toTarget.z * dashDist * t;
                    emitter.add(StellaParticles.ID_STELLA_WISP, px, this.evoker.getY() + 1.0, pz, 0);
                    // 新增：虚空火花残影
                    if (i % 2 == 0) {
                        emitter.add(StellaParticles.ID_VOID_SPARK, px, this.evoker.getY() + 0.8, pz, 1);
                    }
                }

                this.evoker.teleportTo(newX, this.evoker.getY(), newZ);
                // 传送碰撞检测：如果卡在固体方块中，向上扫描安全位置
                net.minecraft.core.BlockPos tpPos = this.evoker.blockPosition();
                if (this.evoker.level().getBlockState(tpPos).isSolidRender(this.evoker.level(), tpPos)) {
                    for (int y = tpPos.getY(); y < tpPos.getY() + 10; y++) {
                        net.minecraft.core.BlockPos checkPos = new net.minecraft.core.BlockPos(tpPos.getX(), y, tpPos.getZ());
                        if (!this.evoker.level().getBlockState(checkPos).isSolidRender(this.evoker.level(), checkPos)) {
                            this.evoker.teleportTo(tpPos.getX(), y, tpPos.getZ());
                            break;
                        }
                    }
                }

                // 落点爆发（从3个增加到8个，更大范围）
                for (int i = 0; i < 8; i++) {
                    double px = newX + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                    double py = this.evoker.getY() + 1.0 + this.evoker.getRandom().nextDouble() * 0.8;
                    double pz = newZ + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                    emitter.add(StellaParticles.ID_TRANSITION_BURST, px, py, pz, 0);
                }
                // 新增：落点冲击波
                for (int i = 0; i < 6; i++) {
                    double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                    double r = this.evoker.getRandom().nextDouble() * 1.5;
                    double px = newX + Math.cos(angle) * r;
                    double pz = newZ + Math.sin(angle) * r;
                    emitter.add(StellaParticles.ID_IMPACT_WAVE, px, this.evoker.getY() + 0.3, pz, 0);
                }
            }
        }

        if (this.stateTimer >= THRUST_DASH_TICKS) {
            this.state = State.THRUST_HIT;
            this.stateTimer = 0;
        }
    }

    // 突进伤害判定：轻微击退
    // 【范围匹配】突进距离 6.0，伤害判定扩大到 4.0 确保突进后能命中
    private static final double THRUST_HIT_RANGE = 4.0;

    private void tickThrustHit(ServerLevel level) {
        if (this.target != null && this.target.isAlive()) {
            double distToTarget = this.evoker.distanceTo(this.target);
            // 伤害判定范围与突进距离匹配（4.0格，突进6.0格后玩家应在范围内）
            if (distToTarget <= THRUST_HIT_RANGE) {
                this.target.hurt(level.damageSources().mobAttack(this.evoker), THRUST_DAMAGE);
                // 轻微击退：调整玩家位置为背刺做准备
                this.target.knockback(0.5F,
                        this.evoker.getX() - this.target.getX(),
                        this.evoker.getZ() - this.target.getZ()
                );
                // 原版暴击粒子：突进命中的物理冲击反馈
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        this.target.getX(), this.target.getY() + 1.0, this.target.getZ(),
                        4, 0.3, 0.3, 0.3, 0.1);
            }
        }

        // 掌击冲击波
        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int i = 0; i < 4; i++) {
                double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                double r = this.evoker.getRandom().nextDouble() * 0.8;
                double px = this.evoker.getX() + Math.cos(angle) * r;
                double py = this.evoker.getY() + 0.8 + this.evoker.getRandom().nextDouble() * 0.6;
                double pz = this.evoker.getZ() + Math.sin(angle) * r;
                emitter.add(StellaParticles.ID_IMPACT_WAVE, px, py, pz, 0);
            }
        }

        // 星界突进掌音效：末影人瞬移+重击组合，体现突进的爆发力
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2F, 0.8F);
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 1.8F);

        this.state = State.BACKSTAB_VANISH;
        this.stateTimer = 0;
    }

    // ==================== 第三段：背刺·虚空贯穿 ====================

    // 消失阶段：BOSS消失，玻璃碎裂音效
    // 精简原则：只保留 TRANSITION_BURST（碎裂闪光）+ POOF（原版烟雾消散）
    private void tickBackstabVanish(ServerLevel level) {
        if (this.stateTimer == 1) {
            // 触发背刺动画（蹲伏→消失→出现→双手穿刺）
            this.evoker.currentAttackAnim = "stella_evoker_backstab";

            // 碎裂闪光
            try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
                for (int i = 0; i < 6; i++) {
                    double px = this.evoker.getX() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                    double py = this.evoker.getY() + this.evoker.getRandom().nextDouble() * 2.0;
                    double pz = this.evoker.getZ() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                    emitter.add(StellaParticles.ID_TRANSITION_BURST, px, py, pz, 0);
                }
            }
            // 原版烟雾消散
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    this.evoker.getX(), this.evoker.getY() + 1.0, this.evoker.getZ(),
                    8, 0.3, 0.5, 0.3, 0.02);
            // 背刺消失音效：玻璃碎裂+末影人传送，体现空间碎裂感
            level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 1.2F);
            level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5F, 1.5F);
        }

        this.evoker.setInvisible(true);

        if (this.stateTimer >= BACKSTAB_VANISH_TICKS) {
            if (this.target != null && this.target.isAlive()) {
                double distToTarget = this.evoker.distanceTo(this.target);
                if (distToTarget <= BACKSTAB_MAX_RANGE) {
                    // 【L3修复】改用 yaw（水平朝向）计算"背后"位置，而非 lookAngle
                    // lookAngle 在玩家仰视/俯视时水平分量趋近 0，导致 BOSS 传送到脚下而非背后
                    // yaw 只包含水平朝向，不受俯仰角影响
                    float yawRad = this.target.getYRot() * ((float) Math.PI / 180F);
                    double behindX = this.target.getX() - Math.sin(yawRad) * 1.0;
                    double behindZ = this.target.getZ() + Math.cos(yawRad) * 1.0;
                    this.evoker.teleportTo(behindX, this.target.getY(), behindZ);
                    // 传送碰撞检测：如果卡在固体方块中，向上扫描安全位置
                    net.minecraft.core.BlockPos tpPos = this.evoker.blockPosition();
                    if (this.evoker.level().getBlockState(tpPos).isSolidRender(this.evoker.level(), tpPos)) {
                        for (int y = tpPos.getY(); y < tpPos.getY() + 10; y++) {
                            net.minecraft.core.BlockPos checkPos = new net.minecraft.core.BlockPos(tpPos.getX(), y, tpPos.getZ());
                            if (!this.evoker.level().getBlockState(checkPos).isSolidRender(this.evoker.level(), checkPos)) {
                                this.evoker.teleportTo(tpPos.getX(), y, tpPos.getZ());
                                break;
                            }
                        }
                    }
                }
            }
            this.evoker.setInvisible(false);
            this.state = State.BACKSTAB_STRIKE;
            this.stateTimer = 0;
        }
    }

    // 背刺伤害判定：巨额伤害+虚空流血（持续掉血DoT）
    // 与终结技（DespairExecutionGoal）的虚空禁锢（定身）完全不同
    // 加强版：更大的伤害范围、更华丽的贯穿特效
    // 【范围匹配】BOSS传送到玩家背后1格处，伤害判定 4.0 确保能命中
    private static final double BACKSTAB_HIT_RANGE = 4.0;

    private void tickBackstabStrike(ServerLevel level) {
        if (this.target != null && this.target.isAlive()) {
            double distToTarget = this.evoker.distanceTo(this.target);
            // 伤害判定范围与传送后位置匹配（4.0格，BOSS在玩家背后1格处）
            if (distToTarget <= BACKSTAB_HIT_RANGE) {
                this.target.hurt(level.damageSources().mobAttack(this.evoker), BACKSTAB_DAMAGE);
                // 施加虚空流血效果：持续掉血DoT，不是虚空禁锢（定身）
                if (!this.target.hasEffect(ModEffects.VOID_BLEED)) {
                    this.target.addEffect(new MobEffectInstance(
                            ModEffects.VOID_BLEED,
                            VOID_BLEED_DURATION, 0, false, true, true
                    ));
                }
                // 原版魔法暴击粒子：虚空贯穿的魔法命中反馈（数量加倍）
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                        this.target.getX(), this.target.getY() + 1.0, this.target.getZ(),
                        12, 0.5, 0.8, 0.5, 0.3);
            }
        }

        Vec3 toTarget = this.target.position().subtract(this.evoker.position()).normalize();

        // 贯穿轨迹（从8个增加到15个，更长更华丽）
        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int i = 0; i < 15; i++) {
                double t = i / 15.0;
                double px = this.evoker.getX() + toTarget.x * t * 3.0;
                double py = this.evoker.getY() + 1.0 + (this.evoker.getRandom().nextDouble() - 0.5) * 0.5;
                double pz = this.evoker.getZ() + toTarget.z * t * 3.0;
                emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 0);
                // 新增：虚空火花混合轨迹
                if (i % 3 == 0) {
                    emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 2);
                }
            }

            // 刺入点爆发（从6个增加到12个，更大范围）
            for (int i = 0; i < 12; i++) {
                double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                double r = this.evoker.getRandom().nextDouble() * 1.2;
                double px = this.target.getX() + Math.cos(angle) * r;
                double py = this.target.getY() + 1.0 + this.evoker.getRandom().nextDouble() * 0.8;
                double pz = this.target.getZ() + Math.sin(angle) * r;
                emitter.add(StellaParticles.ID_IMPACT_WAVE, px, py, pz, 0);
            }
            // 新增：刺入点星形爆发
            for (int i = 0; i < 6; i++) {
                double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                double r = 0.5 + this.evoker.getRandom().nextDouble() * 1.0;
                double px = this.target.getX() + Math.cos(angle) * r;
                double pz = this.target.getZ() + Math.sin(angle) * r;
                emitter.add(StellaParticles.ID_TRANSITION_BURST, px, this.target.getY() + 1.2, pz, 0);
            }
        }

        // 背刺贯穿音效：三叉戟刺入+凋灵破坏，体现虚空长矛的贯穿力
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 1.5F, 1.2F);

        this.state = State.COOLDOWN;
        this.stateTimer = 0;
    }

    // ==================== 远距离拉人 ====================

    // 拉人蓄力阶段：BOSS抬手蓄力
    // 精简原则：只保留 VOID_SPARK（虚空能量）+ PORTAL（原版传送门吸入）
    private void tickPullWindup(ServerLevel level) {
        this.evoker.getLookControl().setLookAt(this.target, 180.0F, 180.0F);

        // 虚空能量聚集
        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int i = 0; i < 2; i++) {
                double px = this.evoker.getX() + (this.evoker.getRandom().nextDouble() - 0.5) * 0.5;
                double py = this.evoker.getY() + 1.5 + this.evoker.getRandom().nextDouble() * 0.5;
                double pz = this.evoker.getZ() + (this.evoker.getRandom().nextDouble() - 0.5) * 0.5;
                emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 0);
            }
        }

        // 原版传送门粒子：虚空吸入效果
        if (this.stateTimer % 3 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    this.evoker.getX(), this.evoker.getY() + 1.0, this.evoker.getZ(),
                    2, 0.3, 0.5, 0.3, 0.5);
        }

        // 拉人蓄力音效：低频虚空吸力，随蓄力进度增强
        if (this.stateTimer % 5 == 0) {
            float pullPitch = 0.4F + (this.stateTimer / (float) PULL_WINDUP_TICKS) * 0.6F;
            level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 0.8F, pullPitch);
        }

        if (this.stateTimer >= PULL_WINDUP_TICKS) {
            if (this.target != null && this.target.isAlive()) {
                Vec3 toTarget = this.target.position().subtract(this.evoker.position()).normalize();
                double tpX = this.evoker.getX() + toTarget.x * 2.0;
                double tpZ = this.evoker.getZ() + toTarget.z * 2.0;

                // 拉扯线：虚空火花
                try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
                    for (int i = 0; i < 8; i++) {
                        double t = i / 8.0;
                        double px = this.target.getX() + (this.evoker.getX() - this.target.getX()) * t;
                        double pz = this.target.getZ() + (this.evoker.getZ() - this.target.getZ()) * t;
                        double py = this.target.getY() + 1.0 + (this.evoker.getRandom().nextDouble() - 0.5) * 0.5;
                        emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 0);
                    }
                }

                if (this.target instanceof Player player) {
                    player.teleportTo(tpX, this.evoker.getY(), tpZ);
                }

                level.playSound(null, this.target.getX(), this.target.getY(), this.target.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5F, 0.5F);
            }

            this.state = State.PULL_STRIKE;
            this.stateTimer = 0;
        }
    }

    // 拉人重拳：提前蓄力好的重拳
    // 【范围匹配】拉人后玩家被传到BOSS身前2格，伤害判定 3.5 确保命中
    private static final double PULL_HIT_RANGE = 3.5;

    private void tickPullStrike(ServerLevel level) {
        if (this.target != null && this.target.isAlive()) {
            double distToTarget = this.evoker.distanceTo(this.target);
            // 伤害判定范围与拉人后位置匹配（3.5格，玩家在BOSS身前2格处）
            if (distToTarget <= PULL_HIT_RANGE) {
                this.target.hurt(level.damageSources().mobAttack(this.evoker), PULL_PUNCH_DAMAGE);
                this.target.knockback(1.5F,
                        this.evoker.getX() - this.target.getX(),
                        this.evoker.getZ() - this.target.getZ()
                );
                // 原版暴击粒子：重拳命中的物理冲击反馈
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        this.target.getX(), this.target.getY() + 1.0, this.target.getZ(),
                        6, 0.3, 0.5, 0.3, 0.1);
            }
        }

        // 冲击波核心
        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int i = 0; i < 6; i++) {
                double px = this.evoker.getX() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                double py = this.evoker.getY() + 1.0 + (this.evoker.getRandom().nextDouble() - 0.5) * 1.0;
                double pz = this.evoker.getZ() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                emitter.add(StellaParticles.ID_IMPACT_WAVE, px, py, pz, 0);
            }
        }

        // 拉人重拳音效：铁砧砸地+重击组合，体现虚空重拳的破坏力
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, 0.8F);
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 2.0F, 0.7F);

        this.state = State.COOLDOWN;
        this.stateTimer = 0;
    }

    // 冷却阶段：BOSS继续寻路追击目标，不站桩
    // 同样使用导航失败回退方案，确保冷却期间也在追人
    private void tickCooldown() {
        // 首次进入冷却时清除攻击动画
        if (this.stateTimer == 1) {
            this.evoker.currentAttackAnim = null;
        }

        if (this.target != null && this.target.isAlive()) {
            this.evoker.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            pathRecalcTimer--;
            if (pathRecalcTimer <= 0) {
                pathRecalcTimer = 4 + this.evoker.getRandom().nextInt(4);
                var path = this.evoker.getNavigation().createPath(this.target, 0);
                if (path != null) {
                    this.evoker.getNavigation().moveTo(path, 1.0);
                } else {
                    this.evoker.getNavigation().stop();
                    this.evoker.getMoveControl().setWantedPosition(
                            this.target.getX(), this.target.getY(), this.target.getZ(), 1.0);
                }
            }
        }
        if (this.stateTimer >= COMBO_COOLDOWN) {
            resetToIdle();
        }
    }

    private void resetToIdle() {
        this.state = State.IDLE;
        this.stateTimer = 0;
        this.comboCooldownTimer = COMBO_COOLDOWN;
        this.pullCooldownTimer = PULL_COOLDOWN;
        this.evoker.setInvisible(false);
        // 清除攻击动画，让 idle_controller 接管
        this.evoker.currentAttackAnim = null;
    }

    @Override
    public void stop() {
        resetToIdle();
    }

    // 判断目标是否在施法者的锥形视野内
    private boolean isInCone(StellaEvokerEntity caster, LivingEntity target, double coneAngleDeg) {
        Vec3 toTarget = target.position().subtract(caster.position()).normalize();
        Vec3 lookDir = caster.getLookAngle().normalize();
        double dot = lookDir.dot(toTarget);
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
        return angle <= Math.toRadians(coneAngleDeg / 2.0);
    }

    // 【BUG#4修复】供 DespairExecutionGoal 检查普攻是否正在进行
    // 防止终结技在普攻三连期间触发
    public boolean isAttacking() {
        return this.state != State.IDLE;
    }

    private enum State {
        IDLE,
        SLASH_DELAY, SLASH,
        THRUST_DASH, THRUST_HIT,
        BACKSTAB_VANISH, BACKSTAB_STRIKE,
        PULL_WINDUP, PULL_STRIKE,
        COOLDOWN
    }
}
