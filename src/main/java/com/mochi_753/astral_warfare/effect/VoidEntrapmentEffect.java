package com.mochi_753.astral_warfare.effect;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.init.ModEffects;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 虚空禁锢效果 - 二阶段 BOSS 的专属定身+削弱 MobEffect
// 效果机制（借鉴 FEL Suppression/Fragmentation 设计模式）：
//   1. 移动速度归零（AttributeModifier 辅助减速 + setDeltaMovement 底层锁死双保险）
//   2. 禁用跳跃（通过将跳跃力归零）
//   3. 护甲削弱：降低 30% 护甲，每级额外 -10%
//   4. 生命上限削减：减少 2 颗心（4 点），每级额外 -1 颗心（2 点）
//   5. 跳跃挣脱：3 秒内连按跳跃键 10 次可挣脱禁锢，效果立即清除
//      — 跳跃输入通过 ServerboundJumpInputPacket 从客户端同步到服务端
// 颜色：深紫色 (0x8B00FF)
public class VoidEntrapmentEffect extends MobEffect {

    // === 跳跃挣脱机制常量 ===
    // 挣脱所需跳跃按键次数：3 秒内按 10 次空格
    private static final int ESCAPE_JUMP_COUNT = 10;
    // 按键计数时间窗口：60 tick = 3 秒
    private static final int ESCAPE_WINDOW_TICKS = 60;

    // === 跳跃挣脱追踪状态（静态 Map，按玩家 UUID 隔离） ===
    // 当前时间窗口内已按跳跃键的次数
    private static final Map<UUID, Integer> jumpPressCount = new ConcurrentHashMap<>();
    // 当前时间窗口内首次按键的游戏时间（tick）
    private static final Map<UUID, Long> firstPressTick = new ConcurrentHashMap<>();

    public VoidEntrapmentEffect(MobEffectCategory category, int color) {
        super(category, color);
        // 辅助减速：ADD_MULTIPLIED_BASE 模式下 -1.0 将基础速度完全抵消
        // 注意：此修饰符只能抵消基础速度，无法抵消速度药水等叠加效果
        // 真正的移动锁死依赖 applyEffectTick 中的 setDeltaMovement 底层归零
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "void_entrapment_slowdown"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        // 禁用跳跃：将跳跃力归零
        this.addAttributeModifier(Attributes.JUMP_STRENGTH,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "void_entrapment_no_jump"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        // 护甲削弱：ADD_MULTIPLIED_BASE -0.30 = 降低基础护甲的 30%
        this.addAttributeModifier(Attributes.ARMOR,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "void_entrapment_armor_reduction"),
                -0.30, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        // 生命上限削减：ADD_VALUE -4.0 = 减少 2 颗心（1 颗心 = 2 点生命值）
        this.addAttributeModifier(Attributes.MAX_HEALTH,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "void_entrapment_health_reduction"),
                -4.0, AttributeModifier.Operation.ADD_VALUE);
    }

    // 每 tick 应用效果时的逻辑
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
        // 性能优化：粒子扫描降频到每 10 tick 一次
        if (entity.level() instanceof ServerLevel serverLevel) {
            if (entity.tickCount % 10 == 0) {
                boolean hasNearbyPlayer = !serverLevel.getEntitiesOfClass(
                        Player.class,
                        entity.getBoundingBox().inflate(32.0),
                        Player::isAlive
                ).isEmpty();
                if (hasNearbyPlayer) {
                    try (ParticleEmitter emitter = new ParticleEmitter(entity)) {
                        emitter.add(StellaParticles.ID_VOID_SPARK,
                                entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ(), 2);
                    }
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

    // === 跳跃挣脱：服务端处理客户端发来的跳跃输入 ===
    // 由 ModPayloads 中的 playToServer handler 调用
    // 跳跃输入通过 ServerboundJumpInputPacket 从客户端同步到服务端
    // 因为原版 player.jumping 是 protected 且不会从客户端同步到服务端
    public static void handleJumpInput(ServerPlayer player) {
        if (player == null || !player.hasEffect(ModEffects.VOID_ENTRAPMENT)) return;

        UUID uuid = player.getUUID();
        long currentTick = player.level().getGameTime();
        Long firstTick = firstPressTick.get(uuid);

        // 检查时间窗口：如果首次按键距今超过 60 tick，重置计数器
        if (firstTick == null || (currentTick - firstTick) > ESCAPE_WINDOW_TICKS) {
            // 超时或首次按键，重置窗口
            firstPressTick.put(uuid, currentTick);
            jumpPressCount.put(uuid, 1);
        } else {
            // 在时间窗口内，累加计数
            int newCount = jumpPressCount.getOrDefault(uuid, 0) + 1;
            jumpPressCount.put(uuid, newCount);

            // 达到挣脱阈值：移除禁锢效果
            if (newCount >= ESCAPE_JUMP_COUNT) {
                // 挣脱成功！移除效果（属性修饰符由原版自动清理）
                player.removeEffect(ModEffects.VOID_ENTRAPMENT);
                // 清理追踪状态
                cleanupPlayer(uuid);

                // 挣脱粒子反馈：一圈虚空火花爆发
                if (player.level() instanceof ServerLevel serverLevel) {
                    try (ParticleEmitter emitter = new ParticleEmitter(player)) {
                        for (int i = 0; i < 16; i++) {
                            double angle = (Math.PI * 2 / 16) * i;
                            double r = 1.2;
                            double px = player.getX() + Math.cos(angle) * r;
                            double py = player.getY() + 0.5;
                            double pz = player.getZ() + Math.sin(angle) * r;
                            emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 0);
                        }
                    }
                }
            }
        }
    }

    // 清理指定玩家的跳跃挣脱追踪状态，防止内存泄漏
    // 在效果移除、玩家断线时调用
    public static void cleanupPlayer(UUID uuid) {
        jumpPressCount.remove(uuid);
        firstPressTick.remove(uuid);
    }
}
