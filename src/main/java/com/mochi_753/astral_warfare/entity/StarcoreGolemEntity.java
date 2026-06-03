package com.mochi_753.astral_warfare.entity;

import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.entity.ai.GolemMoveToBossGoal;
import com.mochi_753.astral_warfare.init.ModConstants;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// 星核傀儡 - BOSS 召唤的回蓝小怪
// 核心定位：移动法力源，不是战斗单位
// 蓝图约定：傀儡不攻击玩家，充能后奔向 BOSS 为其恢复法力
// 玩家应优先击杀傀儡来削弱 BOSS 的法力恢复能力
public class StarcoreGolemEntity extends Monster {

    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGED =
            SynchedEntityData.defineId(StarcoreGolemEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double BASE_MOVEMENT_SPEED = 0.25;
    private static final double CHARGED_SPEED_MULTIPLIER = 1.5;

    private int chargeDelayTimer = 0;
    private boolean chargeScheduled = false;

    public StarcoreGolemEntity(EntityType<? extends StarcoreGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_CHARGED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // 蓝图约定：傀儡不攻击玩家，核心职责是奔向 BOSS 回蓝
        // 已移除 MeleeAttackGoal、HurtByTargetGoal、NearestAttackableTargetGoal
        // 傀儡被玩家攻击时不会反击，只会继续向 BOSS 移动

        // 向 BOSS 聚拢：充能状态下主动向星穹唤星者移动
        // 优先级最高，确保傀儡始终向 BOSS 移动
        this.goalSelector.addGoal(1, new GolemMoveToBossGoal(this));

        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    public static AttributeSupplier createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
                .add(Attributes.FOLLOW_RANGE, 30.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .build();
    }

    public boolean isCharged() {
        return this.entityData.get(DATA_IS_CHARGED);
    }

    public void scheduleDelayedCharge() {
        this.chargeScheduled = true;
        this.chargeDelayTimer = ModConstants.GOLEM_CHARGE_DELAY_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.chargeScheduled && !this.level().isClientSide) {
            // S-51修复：Math.max 确保计时器不会下溢为负数
            // 理论上不会发生，但 NBT 恢复异常值时可能触发
            this.chargeDelayTimer = Math.max(0, this.chargeDelayTimer - 1);
            if (this.chargeDelayTimer <= 0) {
                this.chargeScheduled = false;
                this.setCharged(true);
            }
        }

        // 充能状态视觉反馈：充能时持续散发星界蓝色微光粒子
        // 每 3 tick 生成 2 个粒子，比之前更密集，让玩家能一眼识别
        if (isCharged() && !this.level().isClientSide && this.tickCount % 3 == 0) {
            try (ParticleEmitter emitter = new ParticleEmitter(this)) {
                for (int i = 0; i < 2; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2;
                    double radius = 0.4 + this.random.nextDouble() * 0.3;
                    double px = this.getX() + Math.cos(angle) * radius;
                    double py = this.getY() + this.random.nextDouble() * 1.2;
                    double pz = this.getZ() + Math.sin(angle) * radius;
                    emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 0);
                }
            }
        }
    }

    // 傀儡不反击：被玩家攻击时不产生仇恨
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        // 被攻击后清除目标，确保傀儡不会反击
        this.setTarget(null);
        return result;
    }

    // 【M1修复】充能延迟状态持久化到 NBT
    // 原先 chargeDelayTimer 和 chargeScheduled 未保存，区块卸载重载后充能倒计时丢失
    // 导致傀儡永久处于未充能状态，无法再变为充能
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ChargeDelayTimer", this.chargeDelayTimer);
        tag.putBoolean("ChargeScheduled", this.chargeScheduled);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.chargeDelayTimer = tag.getInt("ChargeDelayTimer");
        this.chargeScheduled = tag.getBoolean("ChargeScheduled");
        // NBT 恢复后重新应用充能状态的速度修饰符
        // 原因：区块卸载重载后 AttributeModifier 丢失，isCharged() 返回 true 但速度未提升
        if (isCharged()) {
            setCharged(true);
        }
    }

    private static final ResourceLocation CHARGED_SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath("astral_warfare", "golem_charged_speed");

    public void setCharged(boolean charged) {
        boolean oldCharged = this.entityData.get(DATA_IS_CHARGED);
        if (oldCharged == charged) {
            return;
        }

        this.entityData.set(DATA_IS_CHARGED, charged);

        if (!this.level().isClientSide && this.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (charged) {
                speedAttr.addPermanentModifier(new AttributeModifier(
                        CHARGED_SPEED_MODIFIER_ID,
                        CHARGED_SPEED_MULTIPLIER - 1.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                ));
            } else {
                speedAttr.removeModifier(CHARGED_SPEED_MODIFIER_ID);
            }
        }
    }
}
