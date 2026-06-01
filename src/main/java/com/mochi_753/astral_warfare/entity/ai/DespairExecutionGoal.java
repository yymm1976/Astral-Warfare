package com.mochi_753.astral_warfare.entity.ai;

import com.mochi_753.astral_warfare.init.ModEffects;
import com.mochi_753.astral_warfare.init.ModConstants;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mochi_753.astral_warfare.entity.StarcoreGolemEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumSet;
import java.util.List;

// 终结技 AI Goal（DespairExecutionGoal）
// ═══════════════════════════════════════════════════════════════════════
//
// 【这是BOSS的特殊处决技，不是三段普攻！】
//
// 终结技流程：冲刺到玩家身前→击飞到高空→空中施加禁锢→瞬移到玩家上方→凝聚长戟→下刺砸地
//   0. WINDUP：BOSS高速冲刺到玩家身前，虚空能量聚集，给玩家短暂闪避窗口
//   1. LAUNCHING：将范围内玩家击飞到高空+伤害（不施加禁锢）
//   2. ENTRAPMENT：玩家已在空中，此时施加虚空禁锢（定身悬浮，无法下落）
//   3. TELEPORTING：BOSS瞬移到被禁锢玩家正上方
//   4. CHARGING：凝结虚空长戟蓄力，BOSS悬浮在玩家上方
//   5. SLAMMING：下刺砸地，长戟贯穿到地面，落地瞬间解除禁锢
//
// 【非锁头设计】
//   前摇结束后，只有5格范围内的玩家才会被击飞
//   玩家可以在前摇期间跑出范围来完全躲避终结技
//
// 【触发条件】独立判断：CD + 血量阈值 + 范围
// 【互斥】普攻进行中时不会触发终结技（BUG#4修复）
// 【不可移动】终结技期间BOSS无法自由移动（BUG#5修复），WINDUP冲刺除外
//
// 状态机：IDLE → WINDUP → LAUNCHING → ENTRAPMENT → TELEPORTING → CHARGING → SLAMMING → IDLE
public class DespairExecutionGoal extends Goal {

    private final StellaEvokerEntity evoker;
    private State state = State.IDLE;
    private int stateTimer = 0;
    private Player targetPlayer = null;
    private int cooldownTimer = 0;
    // 击飞标记：确保击飞+伤害只执行一次，避免每tick重复造成10倍伤害
    private boolean hasLaunched = false;
    private static final int COOLDOWN_TICKS = ModConstants.EXECUTION_COOLDOWN_TICKS;
    private static final int WINDUP_TICKS = ModConstants.EXECUTION_WINDUP_TICKS;
    private static final float LAUNCH_DAMAGE = ModConstants.EXECUTION_LAUNCH_DAMAGE;
    private static final double LAUNCH_RANGE = ModConstants.EXECUTION_LAUNCH_RANGE;
    private static final double TELEPORT_HEIGHT = ModConstants.EXECUTION_TELEPORT_HEIGHT;
    private static final int CHARGE_TICKS = ModConstants.EXECUTION_CHARGE_TICKS;
    private static final int LAUNCH_TICKS = 10;
    // 禁锢施加延迟：击飞后等待8tick让玩家升空，再施加禁锢
    private static final int ENTRAPMENT_DELAY_TICKS = 8;
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

        // 【BUG#4修复】普攻进行中时不触发终结技
        // 防止终结技打断正在执行的普攻连招
        if (this.evoker.phase2MeleeGoal != null && this.evoker.phase2MeleeGoal.isAttacking()) return false;

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
        this.hasLaunched = false;
        // 【BUG#5修复】终结技开始时停止导航，防止BOSS自由移动
        // WINDUP阶段会重新启用导航进行冲刺
        this.evoker.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.targetPlayer == null || !this.targetPlayer.isAlive()) {
            // 目标死亡或丢失：清除禁锢后回到 IDLE
            if (this.targetPlayer != null && this.targetPlayer.hasEffect(ModEffects.VOID_ENTRAPMENT)) {
                this.targetPlayer.removeEffect(ModEffects.VOID_ENTRAPMENT);
            }
            cleanupExecution();
            return;
        }

        if (!(this.evoker.level() instanceof ServerLevel serverLevel)) return;

        this.stateTimer++;

        switch (this.state) {
            case WINDUP -> tickWindup(serverLevel);
            case LAUNCHING -> tickLaunching(serverLevel);
            case ENTRAPMENT -> tickEntrapment(serverLevel);
            case TELEPORTING -> tickTeleporting(serverLevel);
            case CHARGING -> tickCharging(serverLevel);
            case SLAMMING -> tickSlamming(serverLevel);
            case COOLDOWN -> tickCooldown(serverLevel);
            case IDLE -> {}
        }
    }

    // 冲刺阶段：BOSS高速冲向玩家身前，同时虚空能量聚集
    // 设计意图：给玩家短暂闪避窗口，但BOSS会高速接近
    // 冲刺速度 1.8 比普通移动快 50%，确保能进入击飞范围
    private void tickWindup(ServerLevel level) {
        this.evoker.getLookControl().setLookAt(this.targetPlayer, 180.0F, 180.0F);

        // 冲刺期间高速向玩家移动，确保能进入击飞范围
        this.evoker.getNavigation().moveTo(this.targetPlayer, 1.8);

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
                if (i % 2 == 0) {
                    emitter.add(StellaParticles.ID_STELLA_WISP, px, py + 0.5, pz, 2);
                }
            }

            double warningRadius = LAUNCH_RANGE;
            int circleSegments = 48;
            for (int i = 0; i < circleSegments; i++) {
                double angle = i * Math.PI * 2.0 / circleSegments;
                double px = this.evoker.getX() + Math.cos(angle) * warningRadius;
                double pz = this.evoker.getZ() + Math.sin(angle) * warningRadius;
                emitter.add(StellaParticles.ID_DYING_EMBER, px, this.evoker.getY() + 0.1, pz, 0);
                if (i % 4 == 0) {
                    double innerR = warningRadius * 0.6;
                    double innerPx = this.evoker.getX() + Math.cos(angle) * innerR;
                    double innerPz = this.evoker.getZ() + Math.sin(angle) * innerR;
                    emitter.add(StellaParticles.ID_TRANSITION_BURST, innerPx, this.evoker.getY() + 0.15, innerPz, 0);
                }
            }
        }

        if (this.stateTimer % 10 == 0) {
            float pitch = 0.5F + (float) progress * 1.0F;
            level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.5F, pitch);
        }
        if (this.stateTimer % 15 == 0) {
            float rumbleVolume = 0.3F + (float) progress * 0.7F;
            level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, rumbleVolume, 0.5F);
        }

        if (this.stateTimer > WINDUP_TICKS / 2 && this.stateTimer % 5 == 0) {
            float intensity = (float) progress * 0.3F;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(this.evoker,
                    new ClientboundScreenShakePacket(intensity, 3, 0.05F));
        }

        if (this.stateTimer >= WINDUP_TICKS) {
            // 【BUG#5修复】冲刺结束后停止导航，BOSS不再自由移动
            this.evoker.getNavigation().stop();
            this.state = State.LAUNCHING;
            this.stateTimer = 0;
        }
    }

    // 击飞阶段：将范围内玩家击飞到高空+伤害
    // 【BUG#1修复】此阶段只击飞+伤害，不施加禁锢
    // 禁锢在下一个状态 ENTRAPMENT 中施加，确保玩家已在空中
    // 距离检查只在击飞前执行一次，击飞后不再检查（防止玩家升空后距离增大导致终结技中断）
    private void tickLaunching(ServerLevel level) {
        // 【BUG#5修复】击飞阶段BOSS不可移动
        suppressMovement();

        if (!this.hasLaunched) {
            // 击飞前：检查距离，只在首次执行
            if (this.targetPlayer != null && this.targetPlayer.isAlive()) {
                double distToTarget = this.evoker.distanceTo(this.targetPlayer);

                if (distToTarget <= LAUNCH_RANGE) {
                    this.hasLaunched = true;
                    // 击飞到高空：2.5 格/tick 向上速度，约飞到 8-10 格高度
                    this.targetPlayer.setDeltaMovement(0, 2.5, 0);
                    this.targetPlayer.hurt(level.damageSources().indirectMagic(this.evoker, this.evoker),
                            LAUNCH_DAMAGE);
                    this.targetPlayer.hurtMarked = true;

                    // 击飞爆发粒子
                    try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
                        for (int i = 0; i < 12; i++) {
                            double px = this.targetPlayer.getX() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                            double py = this.targetPlayer.getY() + this.evoker.getRandom().nextDouble() * 1.5;
                            double pz = this.targetPlayer.getZ() + (this.evoker.getRandom().nextDouble() - 0.5) * 1.5;
                            emitter.add(StellaParticles.ID_TRANSITION_BURST, px, py, pz, 0);
                        }
                    }
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                            this.targetPlayer.getX(), this.targetPlayer.getY() + 1.0, this.targetPlayer.getZ(),
                            1, 0.0, 0.0, 0.0, 0.0);

                    level.playSound(null, this.targetPlayer.getX(), this.targetPlayer.getY(), this.targetPlayer.getZ(),
                            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 1.0F);
                } else {
                    // 玩家跑出范围：终结技落空，进入冷却
                    this.state = State.IDLE;
                    this.stateTimer = 0;
                    this.cooldownTimer = COOLDOWN_TICKS / 2;
                    this.targetPlayer = null;
                    return;
                }
            }
        }

        // 击飞后持续悬浮：防止玩家因重力下落（但不禁锢，禁锢在ENTRAPMENT阶段施加）
        if (this.targetPlayer != null && this.targetPlayer.getDeltaMovement().y < 0) {
            this.targetPlayer.setDeltaMovement(0, 0, 0);
            this.targetPlayer.hurtMarked = true;
        }

        if (this.stateTimer >= LAUNCH_TICKS) {
            // 击飞阶段结束，进入禁锢施加阶段
            this.state = State.ENTRAPMENT;
            this.stateTimer = 0;
        }
    }

    // 禁锢施加阶段：玩家已在空中，此时施加虚空禁锢
    // 【BUG#1修复】将禁锢从击飞阶段分离到独立阶段
    // 确保玩家先被击飞升空，再在空中施加禁锢，符合"击飞后在空中施加禁锢"的要求
    private void tickEntrapment(ServerLevel level) {
        // 【BUG#5修复】禁锢阶段BOSS不可移动
        suppressMovement();

        // 首次进入禁锢阶段时施加虚空禁锢
        if (this.stateTimer == 1) {
            if (this.targetPlayer != null && !this.targetPlayer.hasEffect(ModEffects.VOID_ENTRAPMENT)) {
                this.targetPlayer.addEffect(new MobEffectInstance(
                        ModEffects.VOID_ENTRAPMENT,
                        120, 0, false, true, true
                ));
            }
        }

        // 持续悬浮：禁锢效果应该已经阻止了下落，但作为保险也在这里抑制
        if (this.targetPlayer != null && this.targetPlayer.getDeltaMovement().y < 0) {
            this.targetPlayer.setDeltaMovement(0, 0, 0);
            this.targetPlayer.hurtMarked = true;
        }

        if (this.stateTimer >= ENTRAPMENT_DELAY_TICKS) {
            this.state = State.TELEPORTING;
            this.stateTimer = 0;
        }
    }

    // 瞬移阶段：BOSS瞬移到玩家正上方
    private void tickTeleporting(ServerLevel level) {
        // 【BUG#5修复】瞬移阶段BOSS不可移动（瞬移是位置设置，不是物理移动）
        if (this.stateTimer == 1) {
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
    private void tickCharging(ServerLevel level) {
        // 【BUG#5修复】蓄力阶段BOSS不可移动
        suppressMovement();

        if (this.targetPlayer != null && this.targetPlayer.isAlive()) {
            this.evoker.getLookControl().setLookAt(this.targetPlayer, 180.0F, 180.0F);
        }

        if (this.targetPlayer != null && this.targetPlayer.getDeltaMovement().y < 0) {
            this.targetPlayer.setDeltaMovement(0, 0, 0);
            this.targetPlayer.hurtMarked = true;
        }

        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int i = 0; i < 4; i++) {
                double px = this.evoker.getX() + (this.evoker.getRandom().nextDouble() - 0.5) * 0.5;
                double py = this.evoker.getY() - 0.5 + (this.evoker.getRandom().nextDouble() - 0.5) * 0.5;
                double pz = this.evoker.getZ() + (this.evoker.getRandom().nextDouble() - 0.5) * 0.5;
                emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 0);
            }

            if (this.stateTimer % 4 == 0) {
                for (int i = 0; i < 2; i++) {
                    double px = this.evoker.getX() + (this.evoker.getRandom().nextDouble() - 0.5) * 0.8;
                    double py = this.evoker.getY() - 0.3;
                    double pz = this.evoker.getZ() + (this.evoker.getRandom().nextDouble() - 0.5) * 0.8;
                    emitter.add(StellaParticles.ID_DYING_EMBER, px, py, pz, 0);
                }
            }
        }

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

    // 下刺阶段：下刺砸地，落地瞬间解除禁锢
    private void tickSlamming(ServerLevel level) {
        if (this.stateTimer == 1) {
            this.evoker.currentAttackAnim = "stella_evoker_execution_slam";
        }

        this.evoker.setNoGravity(false);
        this.evoker.setDeltaMovement(0, -3.0, 0);

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
                    emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 0);
                    emitter.add(StellaParticles.ID_DYING_EMBER, px, py, pz, 0);
                    if (i % 2 == 0) {
                        emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 0);
                    }
                }
            }
        }

        if (this.evoker.onGround()) {
            executeSlamImpact(level);
            this.evoker.currentAttackAnim = null;
            this.state = State.IDLE;
            this.stateTimer = 0;
            this.cooldownTimer = COOLDOWN_TICKS;
            this.targetPlayer = null;
        }

        if (this.stateTimer > 60) {
            if (this.targetPlayer != null && this.targetPlayer.hasEffect(ModEffects.VOID_ENTRAPMENT)) {
                this.targetPlayer.removeEffect(ModEffects.VOID_ENTRAPMENT);
            }
            this.evoker.currentAttackAnim = null;
            cleanupExecution();
        }
    }

    private void tickCooldown(ServerLevel level) {
        if (this.cooldownTimer > 0) {
            this.cooldownTimer--;
        }
        if (this.cooldownTimer <= 0) {
            this.state = State.IDLE;
            this.stateTimer = 0;
            this.targetPlayer = null;
        }
    }

    // 【BUG#5修复】抑制BOSS移动：停止导航+清零速度
    // 在终结技的 LAUNCHING/ENTRAPMENT/CHARGING 阶段调用
    // WINDUP 阶段允许冲刺（导航移动），SLAMMING 阶段有受控下落
    private void suppressMovement() {
        this.evoker.getNavigation().stop();
        this.evoker.setDeltaMovement(Vec3.ZERO);
    }

    // 终结技异常终止时的统一清理方法
    private void cleanupExecution() {
        this.state = State.IDLE;
        this.stateTimer = 0;
        this.evoker.currentAttackAnim = null;
        this.cooldownTimer = COOLDOWN_TICKS / 2;
        this.evoker.setNoGravity(false);
        this.targetPlayer = null;
    }

    // 砸地冲击阶段
    private void executeSlamImpact(ServerLevel level) {
        if (this.targetPlayer != null && this.targetPlayer.hasEffect(ModEffects.VOID_ENTRAPMENT)) {
            this.targetPlayer.removeEffect(ModEffects.VOID_ENTRAPMENT);
        }

        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                this.evoker.getX(), this.evoker.getY() + 0.5, this.evoker.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                this.evoker.getX(), this.evoker.getY() + 0.5, this.evoker.getZ(),
                10, 1.0, 0.3, 1.0, 0.05);

        if (this.targetPlayer != null && this.targetPlayer.isAlive()) {
            this.targetPlayer.hurt(
                    level.damageSources().indirectMagic(this.evoker, this.evoker),
                    EXECUTION_DAMAGE
            );
            spawnHalberdImpactTrail(level, this.targetPlayer);
            this.targetPlayer.knockback(1.5F,
                    this.evoker.getX() - this.targetPlayer.getX(),
                    this.evoker.getZ() - this.targetPlayer.getZ()
            );
            this.targetPlayer.setDeltaMovement(
                    this.targetPlayer.getDeltaMovement().add(0, 0.3, 0)
            );
        }

        AABB impactBox = this.evoker.getBoundingBox().inflate(SLAM_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, impactBox,
                entity -> entity != this.evoker && entity.isAlive() && entity != this.targetPlayer
                        && !(entity instanceof StarcoreGolemEntity) && !(entity instanceof Player));

        for (LivingEntity target : targets) {
            target.hurt(level.damageSources().indirectMagic(this.evoker, this.evoker), ModConstants.EXECUTION_SLAM_SPLASH_DAMAGE);
            double kx = target.getX() - this.evoker.getX();
            double kz = target.getZ() - this.evoker.getZ();
            double kLen = Math.sqrt(kx * kx + kz * kz);
            if (kLen > 0.001) {
                target.knockback(2.0F, -kx / kLen, -kz / kLen);
            }
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this.evoker,
                new ClientboundScreenShakePacket(0.8F, 10, 0.08F));

        AABB slamBox = this.evoker.getBoundingBox().inflate(SLAM_RADIUS);
        for (Player player : level.getEntitiesOfClass(Player.class, slamBox,
                p -> p.isAlive() && !p.isSpectator() && p != this.targetPlayer)) {
            player.hurt(level.damageSources().indirectMagic(this.evoker, this.evoker),
                    ModConstants.EXECUTION_SLAM_SPLASH_DAMAGE);
            Vec3 knockbackDir = player.position().subtract(this.evoker.position()).normalize();
            player.knockback(1.5F, -knockbackDir.x, -knockbackDir.z);
        }

        try (ParticleEmitter emitter = new ParticleEmitter(this.evoker)) {
            for (int i = 0; i < 40; i++) {
                double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                double r = this.evoker.getRandom().nextDouble() * SLAM_RADIUS;
                double px = this.evoker.getX() + Math.cos(angle) * r;
                double pz = this.evoker.getZ() + Math.sin(angle) * r;
                double py = this.evoker.getY() + 0.3 + this.evoker.getRandom().nextDouble() * 0.5;
                emitter.add(StellaParticles.ID_IMPACT_WAVE, px, py, pz, 0);
            }

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
        }

        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, 0.6F);
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 1.5F, 0.8F);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this.evoker,
                new ClientboundScreenShakePacket(0.8F, 10, 0.08F));
    }

    @Override
    public void stop() {
        this.state = State.IDLE;
        this.stateTimer = 0;
        this.evoker.currentAttackAnim = null;
        if (this.targetPlayer != null && this.targetPlayer.hasEffect(ModEffects.VOID_ENTRAPMENT)) {
            this.targetPlayer.removeEffect(ModEffects.VOID_ENTRAPMENT);
        }
        if (this.evoker.getCombatPhase() == StellaEvokerEntity.PHASE_2_MELEE) {
            this.evoker.setNoGravity(false);
        }
    }

    private enum State {
        IDLE, WINDUP, LAUNCHING, ENTRAPMENT, TELEPORTING, CHARGING, SLAMMING, COOLDOWN
    }

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
                if (i > steps * 0.7) {
                    emitter.add(StellaParticles.ID_TRANSITION_BURST, px, py, pz, 0);
                }
            }
        }
    }
}