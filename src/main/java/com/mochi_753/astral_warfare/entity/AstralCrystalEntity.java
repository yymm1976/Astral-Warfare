package com.mochi_753.astral_warfare.entity;

import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collections;

// 星界水晶实体 - BOSS 战的能量纽带
// 不可移动、无 AI，固定在祭坛四角的基座上
// 每个存活的水晶为 BOSS 提供法力恢复加成（1 + 存活数 × 3 / 秒）
// 被玩家击碎后永久失效，对应的法力加成和能量射线消失
//
// 父类选择 LivingEntity 而非 Mob：
//   水晶不需要 AI 系统（GoalSelector/LookControl/MoveControl），
//   也不需要 Mob 提供的寻路、装备栏、经验掉落等功能。
//   使用 LivingEntity 避免无用的 AI 组件开销，语义更精确。
public class AstralCrystalEntity extends LivingEntity {

    // 同步标志：水晶是否存活（用于客户端渲染射线）
    private static final EntityDataAccessor<Boolean> DATA_ALIVE =
            SynchedEntityData.defineId(AstralCrystalEntity.class, EntityDataSerializers.BOOLEAN);

    // 粒子效果计时器
    private int particleTimer = 0;

    public AstralCrystalEntity(EntityType<? extends AstralCrystalEntity> entityType, Level level) {
        super(entityType, level);
        // 水晶不可移动：禁用重力、禁用碰撞推挤
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ALIVE, true);
    }

    // LivingEntity 不自带 createMobAttributes()，需手动构建属性
    // MOVEMENT_SPEED 即便水晶不移动也必须注册，否则运行时崩溃
    // （LivingEntity.getAttributeValue() 会因属性不存在而抛出 NullPointerException）
    public static AttributeSupplier createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .build();
    }

    // LivingEntity 要求实现的抽象方法：返回主手
    // 水晶没有手臂概念，默认返回 RIGHT
    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    // LivingEntity 要求实现的抽象方法：设置装备槽物品
    // 水晶无装备系统，空实现
    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    // LivingEntity 要求实现的抽象方法：获取装备槽物品
    // 水晶无装备系统，返回空 ItemStack
    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    // LivingEntity 要求实现的抽象方法：获取护甲槽迭代器
    // 水晶无装备系统，返回空集合迭代器
    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    // 水晶是否存活（用于射线渲染判断）
    public boolean isCrystalAlive() {
        return this.entityData.get(DATA_ALIVE);
    }

    @Override
    public void tick() {
        super.tick();

        // 客户端：每 5 tick 播放一次环境粒子（星界光束）
        // 使用原版 addParticle 替代 StellaParticles 客户端方法
        // 原因：StellaParticles 和 ClientLevel 是客户端专属类，
        //       在通用代码中导入会导致专用服务端 ClassNotFoundException
        //       addParticle 是 Level 的通用方法，客户端自动渲染，服务端忽略
        if (this.level().isClientSide && isCrystalAlive()) {
            particleTimer++;
            if (particleTimer >= 5) {
                particleTimer = 0;
                this.level().addParticle(ParticleTypes.END_ROD,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.5,
                        this.getY() + 1.0 + this.random.nextDouble() * 0.5,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.5,
                        0.0, 0.05, 0.0);
            }
        }
    }

    // 水晶死亡时：播放碎裂粒子并标记死亡
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {
            // 标记水晶死亡，同步到客户端用于射线断开
            this.entityData.set(DATA_ALIVE, false);
            // 服务端：播放碎裂粒子（使用 Lodestone 粒子网络包替代原版 sendParticles）
            // S-49修复：使用 StellaParticles 常量替代硬编码数字，避免 ID 不一致
            if (this.level() instanceof ServerLevel) {
                try (ParticleEmitter emitter = new ParticleEmitter(this)) {
                    emitter.add(StellaParticles.ID_IMPACT_WAVE, this.getX(), this.getY() + 0.5, this.getZ(), 0);
                    for (int i = 0; i < 20; i++) {
                        emitter.add(StellaParticles.ID_ASTRAL_BEAM,
                                this.getX() + (this.random.nextDouble() - 0.5) * 1.0,
                                this.getY() + this.random.nextDouble() * 1.5,
                                this.getZ() + (this.random.nextDouble() - 0.5) * 1.0, 0);
                    }
                }
            }
        }
        super.die(source);
    }

    // 禁用推挤：其他实体无法推动水晶
    @Override
    public boolean isPushable() {
        return false;
    }

    // 禁用被推挤
    @Override
    public void push(Entity entity) {
    }

    // 禁用水流推挤
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    // 保存数据到 NBT
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("CrystalAlive", isCrystalAlive());
    }

    // 从 NBT 读取数据
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("CrystalAlive")) {
            this.entityData.set(DATA_ALIVE, tag.getBoolean("CrystalAlive"));
        }
    }
}
