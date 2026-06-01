package com.mochi_753.astral_warfare.entity.ai;

import com.mochi_753.astral_warfare.init.ModEffects;
import com.mochi_753.astral_warfare.init.ModConstants;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.network.ClientboundScreenShakePacket;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumSet;
import java.util.List;

// ═══════════════════════════════════════════════════════════════════════
// 终结技 AI Goal（DespairExecutionGoal）
// ═══════════════════════════════════════════════════════════════════════
//
// 【这是BOSS的特殊处决技，不是三段普攻！】
//
// 终结技流程：前摇预警→击飞→瞬移上方→蓄力→下刺砸地
//   0. WINDUP：明显的前摇蓄力，BOSS周围虚空能量聚集，给玩家闪避窗口
//   1. LAUNCHING：将范围内玩家击飞到空中+伤害，施加虚空禁锢（定身悬浮）
//   2. TELEPORTING：BOSS瞬移到玩家正上方
//   3. CHARGING：凝结虚空长戟蓄力
//   4. SLAMMING：解除禁锢，下刺砸地，地面崩裂
//
// 【非锁头设计】
//   前摇结束后，只有5格范围内的玩家才会被击飞
//   玩家可以在前摇期间跑出范围来完全躲避终结技
//
// 【触发条件】独立判断：CD + 血量阈值 + 范围
//
// 状态机：IDLE → WINDUP → LAUNCHING → TELEPORTING → CHARGING → SLAMMING → IDLE
public class DespairExecutionGoal extends Goal {

    private final StellaEvokerEntity evoker;
    private State state = State.IDLE;
    private int stateTimer = 0;
    private Player targetPlayer = null;
    private int cooldownTimer = 0;
    private static final int COOLDOWN_TICKS = ModConstants.EXECUTION_COOLDOWN_TICKS;
    private static final int WINDUP_TICKS = ModConstants.EXECUTION_WINDUP_TICKS;
    private static final float LAUNCH_DAMAGE = ModConstants.EXECUTION_LAUNCH_DAMAGE;
    private static final double LAUNCH_RANGE = ModConstants.EXECUTION_LAUNCH_RANGE;
    private static final double TELEPORT_HEIGHT = ModConstants.EXECUTION_TELEPORT_HEIGHT;
    private static final int CHARGE_TICKS = ModConstants.EXECUTION_CHARGE_TICKS;
    private static final int LAUNCH_TICKS = 10;
    private static final float EXECUTION_DAMAGE = ModConstants.EXECUTION_DAMAGE;
    private static final float SLAM_RADIUS = ModConstants.EXECUTION_SLAM_RADIUS;
    private static final float TRIGGER_HEALTH_PERCENT = ModConstants.EXECUTION_TRIGGER_HEALTH_PERCENT;
    private static final double TRIGGER_RANGE = ModConstants.EXECUTION_TRIGGER_RANGE;

    public DespairExecutionGoal(StellaEvokerEntity evoker) {
        this.evoker = evoker;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.evoker.getCombatPhase() != StellaEvokerEntity.PHASE_2_MELEE) return false;
        if (this.evoker.isTransitioning()) return false;
        if (this.evoker.isDying()) return false;
        if (this.state != State.IDLE) return false;

        if (this.cooldownTimer > 0) {
            this.cooldownTimer--;
            return false;
        }

        float healthPercent = this.evoker.getHealth() / this.evoker.getMaxHealth();
        if (healthPercent > TRIGGER_HEALTH_PERCENT) return false;

        if (!(this.evoker.level() instanceof ServerLevel serverLevel)) return false;

        AABB searchBox = this.evoker.getBoundingBox().inflate(TRIGGER_RANGE);
        List<Player> nearbyPlayers = serverLevel.getEntitiesOfClass(
                Player.class, searchBox,
                player -> player.isAlive() && !player.isSpectator()
        );

        if (!nearbyPlayers.isEmpty()) {
            this.targetPlayer = nearbyPlayers.get(0);
            return true;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.state != State.IDLE && this.targetPlayer != null && this.targetPlayer.isAlive();
    }

    @Override
    public void start() {
        this.state = State.WINDUP;
        this.stateTimer = 0;
    }

    @Override
    public void tick() {
        if (this.targetPlayer == null || !this.targetPlayer.isAlive()) {
            this.state = State.IDLE;
            return;
        }

        if (!(this.evoker.level() instanceof ServerLevel serverLevel)) return;

        this.stateTimer++;

        switch (this.state) {
            case WINDUP -> tickWindup(serverLevel);
            case LAUNCHING -> tickLaunching(serverLevel);
            case TELEPORTING -> tickTeleporting(serverLevel);
            case CHARGING -> tickCharging(serverLevel);
            case SLAMMING -> tickSlamming(serverLevel);
            case COOLDOWN -> tickCooldown(serverLevel);
            case IDLE -> {}
        }
    }

    // 前摇预警阶段：BOSS周围虚空能量剧烈聚集
    // 设计意图：给玩家1.5秒的闪避窗口，跑出15格范围即可完全躲避
    // 加强版：更密集的螺旋能量、更明显的地面预警圈
    private void tickWindup(ServerLevel level) {
        this.evoker.getLookControl().setLookAt(this.targetPlayer, 180.0F, 180.0F);

        double progress = (double) this.stateTimer / WINDUP_TICKS;

        // BOSS身体周围虚空能量螺旋上升（密度加倍）
        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            int spiralCount = (int) (4 + progress * 6);
            for (int i = 0; i < spiralCount; i++) {
                double angle = this.stateTimer * 0.5 + i * Math.PI * 2.0 / spiralCount;
                double r = 0.8 + progress * 2.5;
                double px = this.evoker.getX() + Math.cos(angle) * r;
                double py = this.evoker.getY() + 0.5 + (this.stateTimer % 20) / 20.0 * 3.0;
                double pz = this.evoker.getZ() + Math.sin(angle) * r;
                emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 0);
                // 新增：星穹微光混合螺旋
                if (i % 2 == 0) {
                    emitter.add(StellaParticles.ID_STELLA_WISP, px, py + 0.5, pz, 2);
                }
            }

            // 地面预警圈：暗红余烬标记击飞范围（从32分段增加到48分段，更密集）
            double warningRadius = LAUNCH_RANGE;
            int circleSegments = 48;
            for (int i = 0; i < circleSegments; i++) {
                double angle = i * Math.PI * 2.0 / circleSegments;
                double px = this.evoker.getX() + Math.cos(angle) * warningRadius;
                double pz = this.evoker.getZ() + Math.sin(angle) * warningRadius;
                emitter.add(StellaParticles.ID_DYING_EMBER, px, this.evoker.getY() + 0.1, pz, 0);
                // 新增：预警圈内圈星形标记
                if (i % 4 == 0) {
                    double innerR = warningRadius * 0.6;
                    double innerPx = this.evoker.getX() + Math.cos(angle) * innerR;
                    double innerPz = this.evoker.getZ() + Math.sin(angle) * innerR;
                    emitter.add(StellaParticles.ID_TRANSITION_BURST, innerPx, this.evoker.getY() + 0.15, innerPz, 0);
                }
            }
        } // auto-flush on close

        // 蓄力音效：逐渐升高的音调+低频心跳，营造压迫感
        if (this.stateTimer % 10 == 0) {
            float pitch = 0.5F + (float) progress * 1.0F;
            level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.5F, pitch);
        }
        // 低频虚空轰鸣：随蓄力进度增强，给玩家强烈压迫感
        if (this.stateTimer % 15 == 0) {
            float rumbleVolume = 0.3F + (float) progress * 0.7F;
            level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, rumbleVolume, 0.5F);
        }

        // 屏幕微震：蓄力高潮时加剧
        if (this.stateTimer > WINDUP_TICKS / 2 && this.stateTimer % 5 == 0) {
            float intensity = (float) progress * 0.3F;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(this.evoker,
                    new ClientboundScreenShakePacket(intensity, 3, 0.05F));
        }

        if (this.stateTimer >= WINDUP_TICKS) {
            this.state = State.LAUNCHING;
            this.stateTimer = 0;
        }
    }

    // 击飞阶段：将范围内玩家击飞到空中+伤害，施加虚空禁锢
    // 非锁头：只有LAUNCH_RANGE内的玩家才会被击飞
    // 精简原则：只保留 TRANSITION_BURST（击飞爆发）+ EXPLOSION（原版爆炸闪光）
    private void tickLaunching(ServerLevel level) {
        if (this.targetPlayer != null && this.targetPlayer.isAlive()) {
            double distToTarget = this.evoker.distanceTo(this.targetPlayer);

            if (distToTarget <= LAUNCH_RANGE) {
                // 击飞：向上抛射 + 伤害
                this.targetPlayer.setDeltaMovement(0, 1.8, 0);
                this.targetPlayer.hurt(level.damageSources().indirectMagic(this.evoker, this.evoker),
                        LAUNCH_DAMAGE);
                this.targetPlayer.hurtMarked = true;

                if (!this.targetPlayer.hasEffect(ModEffects.VOID_ENTRAPMENT)) {
                    this.targetPlayer.addEffect(new MobEffectInstance(
                            ModEffects.VOID_ENTRAPMENT,
                            80, 0, false, true, true
                    ));
                }

                // 击飞爆发粒子
                try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
                    for (int i = 0; i < 12; i++) {
                        double px = this.targetPlayer.getX() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                        double py = this.targetPlayer.getY() + this.evoker.getRandom().nextDouble() * 1.5;
                        double pz = this.targetPlayer.getZ() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                        emitter.add(StellaParticles.ID_TRANSITION_BURST, px, py, pz, 0);
                    }
                } // auto-flush on close
                // 原版爆炸粒子：击飞瞬间的爆炸闪光
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                        this.targetPlayer.getX(), this.targetPlayer.getY() + 1.0, this.targetPlayer.getZ(),
                        1, 0.0, 0.0, 0.0, 0.0);

                level.playSound(null, this.targetPlayer.getX(), this.targetPlayer.getY(), this.targetPlayer.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 1.0F);
            } else {
                // 玩家跑出范围：终结技落空，进入半冷却
                this.state = State.COOLDOWN;
                this.stateTimer = 0;
                this.cooldownTimer = COOLDOWN_TICKS / 2;
                return;
            }
        }

        if (this.targetPlayer != null && this.targetPlayer.getDeltaMovement().y < 0) {
            this.targetPlayer.setDeltaMovement(0, 0, 0);
            this.targetPlayer.hurtMarked = true;
        }

        if (this.stateTimer >= LAUNCH_TICKS) {
            this.state = State.TELEPORTING;
            this.stateTimer = 0;
        }
    }

    // 瞬移阶段：BOSS瞬移到玩家正上方
    // 精简原则：只保留 POOF（原版消失烟雾）+ REVERSE_PORTAL（原版出现能量）
    private void tickTeleporting(ServerLevel level) {
        if (this.stateTimer == 1) {
            // 原版烟雾消散：瞬移消失
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    this.evoker.getX(), this.evoker.getY() + 1.0, this.evoker.getZ(),
                    6, 0.3, 0.5, 0.3, 0.02);

            if (this.targetPlayer != null && this.targetPlayer.isAlive()) {
                double tpX = this.targetPlayer.getX();
                double tpY = this.targetPlayer.getY() + TELEPORT_HEIGHT;
                double tpZ = this.targetPlayer.getZ();
                this.evoker.teleportTo(tpX, tpY, tpZ);
            }

            net.minecraft.core.BlockPos evokerPos = this.evoker.blockPosition();
            if (this.evoker.level().getBlockState(evokerPos).isSolidRender(this.evoker.level(), evokerPos)) {
                for (int y = evokerPos.getY(); y < evokerPos.getY() + 10; y++) {
                    net.minecraft.core.BlockPos checkPos = new net.minecraft.core.BlockPos(evokerPos.getX(), y, evokerPos.getZ());
                    if (!this.evoker.level().getBlockState(checkPos).isSolidRender(this.evoker.level(), checkPos)) {
                        this.evoker.teleportTo(evokerPos.getX(), y, evokerPos.getZ());
                        break;
                    }
                }
            }
            this.evoker.setNoGravity(true);
            this.evoker.setDeltaMovement(Vec3.ZERO);
        }

        if (this.stateTimer == 2) {
            // 原版反向传送门粒子：瞬移出现的紫色能量扩散
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    this.evoker.getX(), this.evoker.getY() + 1.0, this.evoker.getZ(),
                    8, 0.5, 0.5, 0.5, 0.5);
        }

        if (this.stateTimer >= 3) {
            this.state = State.CHARGING;
            this.stateTimer = 0;
        }
    }

    // 蓄力阶段：凝结虚空长戟
    // 精简原则：只保留 VOID_SPARK（长戟凝聚）+ DYING_EMBER（下坠余烬）
    private void tickCharging(ServerLevel level) {
        if (this.targetPlayer != null && this.targetPlayer.isAlive()) {
            this.evoker.getLookControl().setLookAt(this.targetPlayer, 180.0F, 180.0F);
        }

        if (this.targetPlayer != null && this.targetPlayer.getDeltaMovement().y < 0) {
            this.targetPlayer.setDeltaMovement(0, 0, 0);
            this.targetPlayer.hurtMarked = true;
        }

        // 长戟凝聚：虚空能量在BOSS下方聚集
        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int i = 0; i < 4; i++) {
                double px = this.evoker.getX() + (this.evoker.getRandom().nextDouble() - 0.5) * 0.5;
                double py = this.evoker.getY() - 0.5 + (this.evoker.getRandom().nextDouble() - 0.5) * 0.5;
                double pz = this.evoker.getZ() + (this.evoker.getRandom().nextDouble() - 0.5) * 0.5;
                emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 0);
            }

            // 下坠余烬
            if (this.stateTimer % 4 == 0) {
                for (int i = 0; i < 2; i++) {
                    double px = this.evoker.getX() + (this.evoker.getRandom().nextDouble() - 0.5) * 0.8;
                    double py = this.evoker.getY() - 0.3;
                    double pz = this.evoker.getZ() + (this.evoker.getRandom().nextDouble() - 0.5) * 0.8;
                    emitter.add(StellaParticles.ID_DYING_EMBER, px, py, pz, 0);
                }
            }
        } // auto-flush on close

        // 蓄力音效：信标充能+末影龙低吼，体现虚空长戟凝聚的恐怖能量
        if (this.stateTimer % 5 == 0) {
            level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.2F, 0.4F);
        }
        if (this.stateTimer % 8 == 0) {
            level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.6F, 0.6F);
        }

        if (this.stateTimer >= CHARGE_TICKS) {
            this.state = State.SLAMMING;
            this.stateTimer = 0;
        }
    }

    // 下刺阶段：解除禁锢并下刺砸地
    private void tickSlamming(ServerLevel level) {
        if (this.stateTimer == 1) {
            this.targetPlayer.removeEffect(ModEffects.VOID_ENTRAPMENT);
        }

        this.evoker.setNoGravity(false);
        this.evoker.setDeltaMovement(0, -3.0, 0);

        // 下刺拖尾粒子：多类型组合
        double ex = this.evoker.getX();
        double ey = this.evoker.getY();
        double ez = this.evoker.getZ();
        Vec3 vel = this.evoker.getDeltaMovement();
        double velLen = vel.length();
        if (velLen > 0.001) {
            Vec3 trailDir = vel.normalize().reverse();
            try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
                for (int i = 0; i < 10; i++) {
                    double t = i / 10.0;
                    double px = ex + trailDir.x * t * 4.0;
                    double py = ey + trailDir.y * t * 4.0;
                    double pz = ez + trailDir.z * t * 4.0;
                    // 虚空火花（SPARKLE）：下刺核心
                    emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 0);
                    // 死亡余烬（SMOKE）：下刺余波
                    emitter.add(StellaParticles.ID_DYING_EMBER, px, py, pz, 0);
                    // 星界光束（SPARK）：能量拖尾
                    if (i % 2 == 0) {
                        emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 0);
                    }
                }
            } // auto-flush on close
        }

        if (this.evoker.onGround()) {
            executeSlamImpact(level);
            this.state = State.IDLE;
            this.cooldownTimer = COOLDOWN_TICKS;
            this.targetPlayer = null;
        }
    }

    // 冷却阶段：终结技落空后的短暂冷却，等待冷却结束后回到 IDLE
    private void tickCooldown(ServerLevel level) {
        this.stateTimer++;
        if (this.cooldownTimer > 0) {
            this.cooldownTimer--;
        }
        if (this.cooldownTimer <= 0) {
            this.state = State.IDLE;
            this.stateTimer = 0;
            this.targetPlayer = null;
        }
    }

    // 砸地冲击阶段：长戟砸地，冲击波扩散
    // 精简原则：只保留 IMPACT_WAVE（冲击波扩散）+ EXPLOSION_EMITTER（原版持续爆炸）+ LARGE_SMOKE（原版浓密烟尘）
    private void executeSlamImpact(ServerLevel level) {
        // 原版爆炸发射器：砸地中心的持续爆炸震撼感
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                this.evoker.getX(), this.evoker.getY() + 0.5, this.evoker.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        // 原版大型烟雾：砸地冲击波的浓密烟尘
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                this.evoker.getX(), this.evoker.getY() + 0.5, this.evoker.getZ(),
                10, 1.0, 0.3, 1.0, 0.05);

        if (this.targetPlayer != null && this.targetPlayer.isAlive()) {
            this.targetPlayer.hurt(
                    level.damageSources().indirectMagic(this.evoker, this.evoker),
                    EXECUTION_DAMAGE
            );
            spawnHalberdImpactTrail(level, this.targetPlayer);
            this.targetPlayer.knockback(2.5F,
                    this.evoker.getX() - this.targetPlayer.getX(),
                    this.evoker.getZ() - this.targetPlayer.getZ()
            );
            this.targetPlayer.setDeltaMovement(
                    this.targetPlayer.getDeltaMovement().add(0, 0.8, 0)
            );
            this.targetPlayer.removeEffect(ModEffects.VOID_ENTRAPMENT);
        }

        AABB impactBox = this.evoker.getBoundingBox().inflate(SLAM_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, impactBox,
                entity -> entity != this.evoker && entity.isAlive() && entity != this.targetPlayer);

        for (LivingEntity target : targets) {
            target.hurt(level.damageSources().indirectMagic(this.evoker, this.evoker), ModConstants.EXECUTION_SLAM_SPLASH_DAMAGE);
            double kx = target.getX() - this.evoker.getX();
            double kz = target.getZ() - this.evoker.getZ();
            double kLen = Math.sqrt(kx * kx + kz * kz);
            if (kLen > 0.001) {
                target.knockback(2.0F, -kx / kLen, -kz / kLen);
            }
        }

        level.explode(
                this.evoker,
                this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                2.0F, Level.ExplosionInteraction.NONE
        );
        level.explode(
                this.evoker,
                this.evoker.getX(), this.evoker.getY() + 0.5, this.evoker.getZ(),
                3.0F, Level.ExplosionInteraction.NONE
        );

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this.evoker,
                new ClientboundScreenShakePacket(0.8F, 10, 0.08F));

        // 伤害判定：排除主目标（已在上方单独处理），避免双重伤害
        AABB slamBox = this.evoker.getBoundingBox().inflate(SLAM_RADIUS);
        for (Player player : level.getEntitiesOfClass(Player.class, slamBox,
                p -> p.isAlive() && !p.isSpectator() && p != this.targetPlayer)) {
            player.hurt(level.damageSources().indirectMagic(this.evoker, this.evoker),
                    ModConstants.EXECUTION_SLAM_SPLASH_DAMAGE);
            Vec3 knockbackDir = player.position().subtract(this.evoker.position()).normalize();
            player.knockback(1.5F, -knockbackDir.x, -knockbackDir.z);
        }

        // 冲击波扩散：IMPACT_WAVE
        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int i = 0; i < 40; i++) {
                double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                double r = this.evoker.getRandom().nextDouble() * SLAM_RADIUS;
                double px = this.evoker.getX() + Math.cos(angle) * r;
                double pz = this.evoker.getZ() + Math.sin(angle) * r;
                double py = this.evoker.getY() + 0.3 + this.evoker.getRandom().nextDouble() * 0.5;
                emitter.add(StellaParticles.ID_IMPACT_WAVE, px, py, pz, 0);
            }

            // 十字裂缝线
            for (int dir = 0; dir < 4; dir++) {
                double angle = dir * Math.PI * 0.5;
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                for (int i = 0; i < 6; i++) {
                    double dist = (i + 1) * 0.8;
                    double px = this.evoker.getX() + dx * dist;
                    double pz = this.evoker.getZ() + dz * dist;
                    emitter.add(StellaParticles.ID_IMPACT_WAVE, px, this.evoker.getY() + 0.1, pz, dir);
                }
            }
        } // auto-flush on close

        // 砸地音效：爆炸+铁砧砸地+凋灵破坏，三重叠加体现毁灭性冲击
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, 0.6F);
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 1.5F, 0.8F);

        // 屏幕震动
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this.evoker,
                new ClientboundScreenShakePacket(0.8F, 10, 0.08F));
    }

    @Override
    public void stop() {
        this.state = State.IDLE;
        this.stateTimer = 0;
        if (this.evoker.getCombatPhase() == StellaEvokerEntity.PHASE_2_MELEE) {
            this.evoker.setNoGravity(false);
        }
    }

    private enum State {
        IDLE, WINDUP, LAUNCHING, TELEPORTING, CHARGING, SLAMMING, COOLDOWN
    }

    // 长戟命中轨迹：长戟划过空中留下的能量轨迹
    // 精简原则：只保留 ASTRAL_BEAM（能量轨迹）+ TRANSITION_BURST（命中点爆发）
    private void spawnHalberdImpactTrail(ServerLevel level, LivingEntity target) {
        double startX = this.evoker.getX();
        double startY = this.evoker.getY() + 1.0;
        double startZ = this.evoker.getZ();
        double endX = target.getX();
        double endY = target.getY();
        double endZ = target.getZ();

        Vec3 dir = new Vec3(endX - startX, endY - startY, endZ - startZ);
        double dist = dir.length();
        if (dist < 0.1) return;
        dir = dir.normalize();
        int steps = (int) (dist * 4);
        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int i = 0; i < steps; i++) {
                double t = (i + 0.5) / steps;
                double px = startX + dir.x * dist * t;
                double py = startY + dir.y * dist * t;
                double pz = startZ + dir.z * dist * t;
                emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 0);
                // 转阶段爆发（STAR）：命中点爆发
                if (i > steps * 0.7) {
                    emitter.add(StellaParticles.ID_TRANSITION_BURST, px, py, pz, 0);
                }
            }
        } // auto-flush on close
    }
}
