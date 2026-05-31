package com.mochi_753.astral_warfare.effect;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.network.ClientboundLodestoneParticlePacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

// 虚空禁锢效果 - 二阶段 BOSS 的专属定身 MobEffect
// 效果机制：
//   1. 移动速度归零（AttributeModifier 辅助减速 + setDeltaMovement 底层锁死双保险）
//   2. 禁用跳跃（通过将跳跃力归零）
//   3. DespairExecutionGoal 仅检测此效果来触发处决连招，不会误伤其他模组
// 颜色：深紫色 (0x8B00FF)
public class VoidEntrapmentEffect extends MobEffect {

    public VoidEntrapmentEffect(MobEffectCategory category, int color) {
        super(category, color);
        // 辅助减速：ADD_MULTIPLIED_BASE 模式下 -1.0 将基础速度完全抵消
        // 注意：此修饰符只能抵消基础速度，无法抵消速度药水等叠加效果
        // 真正的移动锁死依赖 applyEffectTick 中的 setDeltaMovement 底层归零
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "void_entrapment_slowdown"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        this.addAttributeModifier(Attributes.JUMP_STRENGTH,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "void_entrapment_no_jump"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    // 每 tick 应用效果时的逻辑
    // 返回 true 表示效果继续生效，false 表示提前移除
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // 底层锁死：直接将水平方向动量归零
        // AttributeModifier 只能抵消基础速度，无法抵消速度药水等叠加效果
        // 通过 setDeltaMovement 从物理底层强制锁死，任凭身上有再高的速度加成也动弹不得
        net.minecraft.world.phys.Vec3 motion = entity.getDeltaMovement();
        if (Math.abs(motion.x) > 0.001 || Math.abs(motion.z) > 0.001) {
            // 仅保留 Y 轴速度（重力/击退），X/Z 轴归零
            entity.setDeltaMovement(0.0, motion.y, 0.0);
        }

        // 持续生成虚空火花粒子效果，增强视觉压迫感
        // 使用 Lodestone 粒子网络包替代原版 sendParticles
        // 设计意图：禁锢效果对所有附近玩家可见，增强多人联机的战斗信息透明度
        // 距离限制：仅当附近 32 格内有玩家时才发送粒子，避免低配客户端性能压力
        // 性能优化：粒子扫描降频到每 10 tick 一次，减少 getEntitiesOfClass 调用
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (entity.tickCount % 10 == 0) {
                boolean hasNearbyPlayer = !serverLevel.getEntitiesOfClass(
                        net.minecraft.world.entity.player.Player.class,
                        entity.getBoundingBox().inflate(32.0),
                        net.minecraft.world.entity.player.Player::isAlive
                ).isEmpty();
                if (hasNearbyPlayer) {
                    // 虚空火花（传送门变体）：虚空禁锢环绕粒子
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
                            new ClientboundLodestoneParticlePacket(StellaParticles.ID_VOID_SPARK,
                                    entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ(), 2));
                }
            }
        }
        return true;
    }

    // 效果是否每 tick 应用（true = applyEffectTick 会被调用）
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // 底层锁死需要每 tick 执行，确保玩家无法移动
        return true;
    }
}
