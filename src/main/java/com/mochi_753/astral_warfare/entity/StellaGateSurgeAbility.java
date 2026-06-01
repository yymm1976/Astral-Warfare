package com.mochi_753.astral_warfare.entity;

import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.init.ModConstants;
import com.mochi_753.astral_warfare.init.ModEntities;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// StellaEvoker 星门涌动技能组件
// 从 StellaEvokerEntity 中剥离星门涌动技能逻辑，实现单一职责原则
// 组件内部管理全部技能状态机 tick 计数、粒子序列与效果时序
//
// 放在 entity 包中（而非 entity.ai），以便访问 StellaEvokerEntity 的包可见字段
public class StellaGateSurgeAbility {

    private static final int GATE_SURGE_RISING_DURATION = 20;
    private static final int GATE_SURGE_CASTING_DURATION = 60;
    private static final double GATE_SURGE_RISE_HEIGHT = 15.0;
    // 【范围匹配】使用 ModConstants.SURGE_PULSE_RADIUS = 8.0，与特效扩散范围一致
    private static final double GATE_SURGE_WAVE_RADIUS = ModConstants.SURGE_PULSE_RADIUS;
    private static final float GATE_SURGE_WAVE_DAMAGE = ModConstants.SURGE_PULSE_DAMAGE;
    private static final double GATE_SURGE_RING_RADIUS = ModConstants.SURGE_RING_RADIUS;

    private final StellaEvokerEntity evoker;
    private boolean gateSurgeTriggered = false;
    private int gateSurgeState = 0;
    private int gateSurgeTimer = 0;
    private double gateSurgeOriginY = 0;

    public StellaGateSurgeAbility(StellaEvokerEntity evoker) {
        this.evoker = evoker;
    }

    public void trigger(ServerLevel serverLevel) {
        gateSurgeTriggered = true;
        gateSurgeState = 1;
        gateSurgeTimer = 0;
        gateSurgeOriginY = evoker.getY();

        if (evoker.spellCastGoal != null) {
            evoker.spellCastGoal.stop();
        }

        for (ServerPlayer player : evoker.bossEvent.getPlayers()) {
            player.sendSystemMessage(
                    Component.translatable("entity.astral_warfare.stella_evoker.spell.gate_surge"));
        }

        // 星门涌动触发音效：末影龙怒吼+信标激活，体现星门开启的震撼
        serverLevel.playSound(null, evoker.getX(), evoker.getY(), evoker.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0F, 0.5F);
        serverLevel.playSound(null, evoker.getX(), evoker.getY(), evoker.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5F, 0.8F);
    }

    public void tick(ServerLevel serverLevel) {
        gateSurgeTimer++;

        if (gateSurgeState == 1) {
            tickRising(serverLevel);
        } else if (gateSurgeState == 2) {
            tickCasting(serverLevel);
        } else if (gateSurgeState == 3) {
            gateSurgeState = 0;
            gateSurgeTimer = 0;
        }
    }

    private void tickRising(ServerLevel serverLevel) {
        double targetY = gateSurgeOriginY + GATE_SURGE_RISE_HEIGHT;
        if (evoker.getY() < targetY) {
            double riseSpeed = GATE_SURGE_RISE_HEIGHT / GATE_SURGE_RISING_DURATION;
            evoker.setDeltaMovement(0, riseSpeed, 0);
        } else {
            evoker.setDeltaMovement(Vec3.ZERO);
        }

        // 升空阶段已有法阵与音效提供足够视觉，删除 ENCHANT 粒子保持简洁

        if (gateSurgeTimer >= GATE_SURGE_RISING_DURATION) {
            gateSurgeState = 2;
            gateSurgeTimer = 0;
            evoker.setDeltaMovement(Vec3.ZERO);
        }
    }

    private void tickCasting(ServerLevel serverLevel) {
        evoker.setDeltaMovement(Vec3.ZERO);

        if (gateSurgeTimer % 4 == 0) {
            try (ParticleEmitter emitter = new ParticleEmitter(evoker)) {
                int particlesPerRing = 16;
                double rotationOffset = gateSurgeTimer * 0.1;
                for (int i = 0; i < particlesPerRing; i++) {
                    double angle = (i * Math.PI * 2.0 / particlesPerRing) + rotationOffset;
                    double px = evoker.getX() + Math.cos(angle) * GATE_SURGE_RING_RADIUS;
                    double pz = evoker.getZ() + Math.sin(angle) * GATE_SURGE_RING_RADIUS;
                    double py = evoker.getY() + (evoker.getRandom().nextDouble() - 0.5) * 0.5;
                    emitter.add(StellaParticles.ID_STELLA_WISP, px, py, pz, 0);
                }
            }
        }

        if (gateSurgeTimer % 40 == 0 && gateSurgeTimer > 0) {
            spawnSurgeWave(serverLevel);
        }

        if (gateSurgeTimer >= GATE_SURGE_CASTING_DURATION) {
            spawnGateSurgeGolems(serverLevel);

            evoker.moveTo(evoker.getX(), gateSurgeOriginY, evoker.getZ(), evoker.getYRot(), evoker.getXRot());

            gateSurgeState = 3;
            gateSurgeTimer = 0;
        }
    }

    private void spawnSurgeWave(ServerLevel serverLevel) {
        double groundY = findGroundY(serverLevel);
        double centerX = evoker.getX();
        double centerZ = evoker.getZ();

        // 脉冲波情景：能量环从中心向外扩散，冲击地面
        // 只保留 IMPACT_WAVE（冲击波扩散）+ CLOUD（原版云雾扩散），删除 VOID_SPARK、ASTRAL_BEAM，降低粒子数量
        try (ParticleEmitter emitter = new ParticleEmitter(evoker)) {
            int ringCount = 2;
            for (int ring = 0; ring < ringCount; ring++) {
                double ringRadius = GATE_SURGE_WAVE_RADIUS * (0.3 + ring * 0.5);
                int particlesPerRing = 12 + ring * 6;
                for (int i = 0; i < particlesPerRing; i++) {
                    double angle = i * Math.PI * 2.0 / particlesPerRing;
                    double px = centerX + Math.cos(angle) * ringRadius;
                    double pz = centerZ + Math.sin(angle) * ringRadius;
                    emitter.add(StellaParticles.ID_IMPACT_WAVE, px, groundY + 0.3, pz, 0);
                }
            }
        }

        // 原版云雾粒子：脉冲波的冲击云雾扩散
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                centerX, groundY + 0.5, centerZ,
                8, GATE_SURGE_WAVE_RADIUS * 0.5, 0.3, GATE_SURGE_WAVE_RADIUS * 0.5, 0.05);

        AABB damageBox = new AABB(
                centerX - GATE_SURGE_WAVE_RADIUS, groundY - 1.0, centerZ - GATE_SURGE_WAVE_RADIUS,
                centerX + GATE_SURGE_WAVE_RADIUS, groundY + 3.0, centerZ + GATE_SURGE_WAVE_RADIUS
        );
        List<Player> targets = serverLevel.getEntitiesOfClass(Player.class, damageBox,
                player -> player.isAlive() && !player.isSpectator());
        for (Player target : targets) {
            target.hurt(serverLevel.damageSources().indirectMagic(evoker, evoker), GATE_SURGE_WAVE_DAMAGE);
            Vec3 knockbackDir = target.position().subtract(evoker.position()).normalize();
            target.knockback(1.0F, -knockbackDir.x, -knockbackDir.z);
        }

        // 星门脉冲波音效：信标环境嗡鸣+爆炸，体现能量环冲击地面
        serverLevel.playSound(null, centerX, groundY, centerZ,
                SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
        serverLevel.playSound(null, centerX, groundY, centerZ,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 1.2F);
    }

    private void spawnGateSurgeGolems(ServerLevel serverLevel) {
        double groundY = findGroundY(serverLevel);
        for (int i = 0; i < 3; i++) {
            double angle = (i * Math.PI * 2.0 / 3.0) + evoker.getRandom().nextDouble() * 0.5;
            double dist = 5.0 + evoker.getRandom().nextDouble() * 3.0;
            double spawnX = evoker.getX() + Math.cos(angle) * dist;
            double spawnZ = evoker.getZ() + Math.sin(angle) * dist;

            var golem = ModEntities.STARCORE_GOLEM.get().create(serverLevel);
            if (golem != null) {
                golem.moveTo(spawnX, groundY, spawnZ, evoker.getYRot(), 0.0f);
                golem.scheduleDelayedCharge();
                serverLevel.addFreshEntity(golem);
            }
        }
    }

    private double findGroundY(ServerLevel level) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = evoker.blockPosition().getY(); y > level.getMinBuildHeight(); y--) {
            mutable.set(evoker.getX(), y, evoker.getZ());
            if (!level.getBlockState(mutable).isAir()) {
                return y + 1.0;
            }
        }
        return evoker.getY();
    }

    public boolean isActive() {
        return gateSurgeState == 1 || gateSurgeState == 2;
    }

    public boolean isTriggered() {
        return gateSurgeTriggered;
    }

    public void setTriggered(boolean triggered) {
        this.gateSurgeTriggered = triggered;
    }

    public int getState() {
        return gateSurgeState;
    }

    public void setState(int state) {
        this.gateSurgeState = state;
    }

    public int getTimer() {
        return gateSurgeTimer;
    }

    public void setTimer(int timer) {
        this.gateSurgeTimer = timer;
    }

    public double getOriginY() {
        return gateSurgeOriginY;
    }

    public void setOriginY(double y) {
        this.gateSurgeOriginY = y;
    }
}
