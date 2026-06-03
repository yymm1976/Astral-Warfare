package com.mochi_753.astral_warfare.entity;

import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.init.ModConstants;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

// 虚空裂隙实体 — 终结技砸地后残留的地面伤害区域
// 不可见的 Misc 实体，每 3 tick 生成上升粒子，每 10 tick 对范围内玩家造成伤害
// 生命周期 300 tick（15 秒），超时自毁
// 粒子颜色随时间从紫色渐变到暗红色
public class VoidFissureEntity extends Entity {

    // 伤害范围半径
    private static final double DAMAGE_RADIUS = 1.5;
    // 伤害间隔（tick）
    private static final int DAMAGE_INTERVAL = 10;
    // 粒子生成间隔（tick）
    private static final int PARTICLE_INTERVAL = 3;
    // 最大生命周期
    private static final int MAX_LIFETIME = ModConstants.VOID_FISSURE_LIFETIME;
    // 伤害值
    private static final float DAMAGE = ModConstants.VOID_FISSURE_DAMAGE;

    // 施法者（用于伤害归因）
    private LivingEntity caster;
    private UUID casterUUID;
    private int damageTimer = 0;

    public VoidFissureEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setInvisible(true);
        this.noPhysics = true;
    }

    public void setCaster(LivingEntity caster) {
        this.caster = caster;
        this.casterUUID = caster != null ? caster.getUUID() : null;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("CasterUUID")) {
            this.casterUUID = tag.getUUID("CasterUUID");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.casterUUID != null) {
            tag.putUUID("CasterUUID", this.casterUUID);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 延迟恢复施法者引用
        if (this.caster == null && this.casterUUID != null) {
            Entity entity = serverLevel.getEntity(this.casterUUID);
            if (entity instanceof LivingEntity living) {
                this.caster = living;
            }
        }

        // 超时自毁
        if (this.tickCount >= MAX_LIFETIME) {
            this.discard();
            return;
        }

        // 生命周期进度 0.0→1.0，用于颜色渐变
        float progress = (float) this.tickCount / MAX_LIFETIME;

        // 每 3 tick 生成上升粒子
        if (this.tickCount % PARTICLE_INTERVAL == 0) {
            // 颜色从紫色(0.5, 0.0, 0.8)渐变到暗红(0.7, 0.08, 0.08)
            float r = 0.5F + progress * 0.2F;
            float g = 0.0F + progress * 0.08F;
            float b = 0.8F - progress * 0.72F;

            try (ParticleEmitter emitter = new ParticleEmitter(this)) {
                for (int i = 0; i < 2; i++) {
                    double ox = (this.random.nextDouble() - 0.5) * 0.6;
                    double oz = (this.random.nextDouble() - 0.5) * 0.6;
                    emitter.add(StellaParticles.ID_VOID_SPARK,
                            this.getX() + ox, this.getY() + 0.1, this.getZ() + oz, 0);
                }
            }

            // 使用原版 DUST 粒子替代 Lodestone WorldParticleBuilder（服务端安全）
            // DUST 粒子支持 RGB 颜色，可呈现紫→暗红渐变
            net.minecraft.core.particles.DustParticleOptions dustColor =
                    new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(r, g, b), 0.5F);
            for (int i = 0; i < 2; i++) {
                double ox = (this.random.nextDouble() - 0.5) * 0.8;
                double oz = (this.random.nextDouble() - 0.5) * 0.8;
                serverLevel.sendParticles(dustColor,
                        this.getX() + ox, this.getY() + 0.1, this.getZ() + oz,
                        1, 0.0, 0.05 + this.random.nextDouble() * 0.03, 0.0, 0.0);
            }
        }

        // 每 10 tick 对范围内玩家造成伤害
        this.damageTimer++;
        if (this.damageTimer >= DAMAGE_INTERVAL) {
            this.damageTimer = 0;
            AABB damageBox = new AABB(
                    this.getX() - DAMAGE_RADIUS, this.getY() - 0.5, this.getZ() - DAMAGE_RADIUS,
                    this.getX() + DAMAGE_RADIUS, this.getY() + 2.0, this.getZ() + DAMAGE_RADIUS
            );
            List<Player> players = serverLevel.getEntitiesOfClass(Player.class, damageBox,
                    p -> p.isAlive() && !p.isSpectator());
            for (Player player : players) {
                if (this.caster != null) {
                    player.hurt(serverLevel.damageSources().indirectMagic(this.caster, this.caster), DAMAGE);
                } else {
                    player.hurt(serverLevel.damageSources().magic(), DAMAGE);
                }
            }
        }
    }
}
