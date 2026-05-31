package com.mochi_753.astral_warfare.entity;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.init.ModConstants;
import com.mochi_753.astral_warfare.network.ClientboundLodestoneParticlePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

// StellaEvoker 转阶段演出状态机组件
// 从 StellaEvokerEntity 中剥离转阶段演出逻辑，实现单一职责原则
// 组件内部管理全部演出 tick 计数、粒子序列与音效时序
// 实体自身只保留 isTransitioning() 状态判断和 transitionFSM.tick() 调用
//
// 放在 entity 包中（而非 entity.ai），以便访问 StellaEvokerEntity 的包可见字段
public class StellaTransitionStateMachine {

    private static final int TRANSITION_DURATION_TICKS = ModConstants.TRANSITION_DURATION_TICKS;
    private static final double TRANSITION_TARGET_HEIGHT = 8.0;

    private final StellaEvokerEntity evoker;
    private int transitionTimer = 0;
    private boolean hasTransitioned = false;

    public StellaTransitionStateMachine(StellaEvokerEntity evoker) {
        this.evoker = evoker;
    }

    public void startTransition(ServerLevel level) {
        hasTransitioned = true;
        transitionTimer = TRANSITION_DURATION_TICKS;
        evoker.getEntityData().set(StellaEvokerEntity.DATA_IS_TRANSITIONING, true);
        evoker.spellCastGoal.stop();
        level.playSound(null, evoker.getX(), evoker.getY(), evoker.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    public void tick(ServerLevel level) {
        transitionTimer--;

        double groundY = findGroundY(level);
        double targetY = groundY + TRANSITION_TARGET_HEIGHT;
        if (evoker.getY() < targetY) {
            evoker.setDeltaMovement(evoker.getDeltaMovement().add(0, 0.5, 0));
        } else {
            evoker.setDeltaMovement(Vec3.ZERO);
        }

        // 转阶段升空情景：BOSS升空，虚空能量释放，玻璃碎裂
        // 只保留 VOID_SPARK（虚空能量释放）+ REVERSE_PORTAL（原版紫色能量扩散），删除 STELLA_WISP、TRANSITION_BURST，降低密度
        if (evoker.getRandom().nextFloat() < 0.4F) {
            for (int i = 0; i < 2; i++) {
                double px = evoker.getX() + (evoker.getRandom().nextDouble() - 0.5) * 3.0;
                double py = evoker.getY() + evoker.getRandom().nextDouble() * 2.0;
                double pz = evoker.getZ() + (evoker.getRandom().nextDouble() - 0.5) * 3.0;
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(evoker,
                        new ClientboundLodestoneParticlePacket(StellaParticles.ID_VOID_SPARK, px, py, pz, 0));
            }
            // 原版反向传送门粒子：转阶段的紫色能量释放
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    evoker.getX(), evoker.getY() + 1.0, evoker.getZ(),
                    3, 1.0, 1.0, 1.0, 0.5);
        }

        if (transitionTimer == TRANSITION_DURATION_TICKS / 2) {
            level.playSound(null, evoker.getX(), evoker.getY(), evoker.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0F, 0.3F);
        }

        if (transitionTimer <= 0) {
            finishTransition(level);
        }
    }

    private void finishTransition(ServerLevel level) {
        evoker.getEntityData().set(StellaEvokerEntity.DATA_IS_TRANSITIONING, false);
        evoker.getEntityData().set(StellaEvokerEntity.DATA_COMBAT_PHASE, StellaEvokerEntity.PHASE_2_MELEE);
        evoker.getEntityData().set(StellaEvokerEntity.DATA_MANA_SYSTEM_DISABLED, true);
        evoker.getManaData().setManaSystemDisabled(true);
        evoker.setCurrentMana(evoker.getManaData().getCurrentMana());
        evoker.setNoGravity(false);

        detonateAllGolems(level);

        for (ServerPlayer player : evoker.bossEvent.getPlayers()) {
            player.sendSystemMessage(
                    Component.translatable("entity.astral_warfare.stella_evoker.phase2"));
        }
        evoker.setMoveControl(new MoveControl(evoker));

        double groundY = findGroundY(level);
        double safeY = groundY + 3.0;
        if (evoker.getY() > safeY) {
            evoker.moveTo(evoker.getX(), safeY, evoker.getZ(), evoker.getYRot(), evoker.getXRot());
        }
        evoker.setDeltaMovement(0, -0.5, 0);

        // 委托给实体的 restorePhase2State()，消除代码重复（DRY 原则）
        evoker.restorePhase2State();

        level.playSound(null, evoker.getX(), evoker.getY(), evoker.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.5F);
        triggerTransitionImpactShockwave(level);
    }

    private void detonateAllGolems(ServerLevel level) {
        AABB searchBox = evoker.getBoundingBox().inflate(30.0);
        List<StarcoreGolemEntity> golems = level.getEntitiesOfClass(StarcoreGolemEntity.class, searchBox,
                golem -> golem.isAlive());

        for (StarcoreGolemEntity golem : golems) {
            for (int i = 0; i < 20; i++) {
                double angle = evoker.getRandom().nextDouble() * Math.PI * 2;
                double r = evoker.getRandom().nextDouble() * 2.0;
                double px = golem.getX() + Math.cos(angle) * r;
                double pz = golem.getZ() + Math.sin(angle) * r;
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(golem,
                        new ClientboundLodestoneParticlePacket(StellaParticles.ID_VOID_SPARK,
                                px, golem.getY() + evoker.getRandom().nextDouble() * 1.5, pz, 0));
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(golem,
                        new ClientboundLodestoneParticlePacket(StellaParticles.ID_IMPACT_WAVE,
                                px, golem.getY() + 0.3, pz, 0));
            }
            level.playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 1.0F);
            golem.discard();
        }
    }

    private void triggerTransitionImpactShockwave(ServerLevel level) {
        float radius = 8.0F;
        AABB impactBox = evoker.getBoundingBox().inflate(radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, impactBox,
                entity -> entity != evoker && entity.isAlive());

        for (LivingEntity target : targets) {
            Vec3 knockbackDir = target.position().subtract(evoker.position()).normalize();
            target.knockback(2.5F, -knockbackDir.x, -knockbackDir.z);
            target.hurt(evoker.damageSources().indirectMagic(evoker, evoker), 12.0F);
        }

        // 落地冲击情景：BOSS落地，冲击波扩散，烟尘四起
        // 只保留 IMPACT_WAVE（冲击波扩散）+ LARGE_SMOKE（原版浓密烟尘），删除 VOID_SPARK、EXPLOSION_EMITTER，降低粒子数量
        // 原版大型烟雾：转阶段落地的冲击烟尘
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                evoker.getX(), evoker.getY() + 0.5, evoker.getZ(),
                10, 2.0, 0.3, 2.0, 0.05);

        for (int i = 0; i < 12; i++) {
            double angle = evoker.getRandom().nextDouble() * Math.PI * 2;
            double r = evoker.getRandom().nextDouble() * radius;
            double px = evoker.getX() + Math.cos(angle) * r;
            double pz = evoker.getZ() + Math.sin(angle) * r;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(evoker,
                    new ClientboundLodestoneParticlePacket(StellaParticles.ID_IMPACT_WAVE, px, evoker.getY() + 0.5, pz, 1));
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
        return evoker.getEntityData().get(StellaEvokerEntity.DATA_IS_TRANSITIONING);
    }

    public boolean hasTransitioned() {
        return hasTransitioned;
    }

    public void setHasTransitioned(boolean hasTransitioned) {
        this.hasTransitioned = hasTransitioned;
    }

    public int getTransitionTimer() {
        return transitionTimer;
    }

    public void setTransitionTimer(int timer) {
        this.transitionTimer = timer;
    }
}
