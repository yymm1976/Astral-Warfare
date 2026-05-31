package com.mochi_753.astral_warfare.effect;

import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.init.ModDamageTypes;
import com.mochi_753.astral_warfare.network.ClientboundLodestoneParticlePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

// 虚空流血效果 - 三段普攻第三段背刺的持续掉血 MobEffect
// 效果机制：每 2 秒（40 tick）造成一次虚空伤害，持续 6 秒（120 tick）
// 与虚空禁锢（定身）完全不同，虚空流血是纯粹的 DoT 伤害效果
// 颜色：暗红色 (0x8B0000)，暗示"虚空侵蚀血液"
//
// 伤害类型：自定义虚空伤害（ModDamageTypes.VOID_BLEED）
// 不是魔法伤害！虚空流血是虚空本源的力量侵蚀，绕过盔甲但与魔法体系无关
// 死亡消息："被虚空侵蚀而亡"，而非"被魔法杀死"
public class VoidBleedEffect extends MobEffect {

    // 每 40 tick（2 秒）造成一次伤害
    private static final int DAMAGE_INTERVAL = 40;
    // 每次伤害值：3 点基础 + amplifier * 1 点
    private static final float DAMAGE_PER_TICK = 3.0F;

    public VoidBleedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            float damage = DAMAGE_PER_TICK + amplifier * 1.0F;

            // 使用自定义虚空伤害类型，不是魔法伤害
            // 虚空流血是虚空本源的力量，与魔法体系无关
            entity.hurt(ModDamageTypes.voidBleed(serverLevel), damage);

            // 虚空流血粒子：暗红色虚空火花从身体散发
            if (entity instanceof Player || entity.level().random.nextFloat() < 0.5F) {
                for (int i = 0; i < 3; i++) {
                    double angle = entity.level().random.nextDouble() * Math.PI * 2;
                    double r = 0.3 + entity.level().random.nextDouble() * 0.4;
                    double px = entity.getX() + Math.cos(angle) * r;
                    double py = entity.getY() + entity.level().random.nextDouble() * 1.5;
                    double pz = entity.getZ() + Math.sin(angle) * r;
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
                            new ClientboundLodestoneParticlePacket(StellaParticles.ID_DYING_EMBER, px, py, pz, 0));
                }
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % DAMAGE_INTERVAL == 0;
    }
}
