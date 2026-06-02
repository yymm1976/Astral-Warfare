package com.mochi_753.astral_warfare.effect;

import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.init.ModDamageTypes;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

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

            // 虚空流血粒子：暗红色余烬 + 虚空火花 + 红色血雾从身体散发
            // 每2秒触发一次，粒子数量适中，不刷屏但有明显视觉反馈
            if (entity instanceof Player || entity.level().random.nextFloat() < 0.5F) {
                try (ParticleEmitter emitter = new ParticleEmitter(entity)) {
                    // 暗红色余烬：从身体周围缓缓上升
                    for (int i = 0; i < 4; i++) {
                        double angle = entity.level().random.nextDouble() * Math.PI * 2;
                        double r = 0.3 + entity.level().random.nextDouble() * 0.5;
                        double px = entity.getX() + Math.cos(angle) * r;
                        double py = entity.getY() + entity.level().random.nextDouble() * 1.5;
                        double pz = entity.getZ() + Math.sin(angle) * r;
                        emitter.add(StellaParticles.ID_DYING_EMBER, px, py, pz, 0);
                    }
                    // 虚空火花：偶尔闪烁的紫色能量点，体现虚空侵蚀
                    for (int i = 0; i < 2; i++) {
                        double px = entity.getX() + (entity.level().random.nextDouble() - 0.5) * 0.8;
                        double py = entity.getY() + 0.5 + entity.level().random.nextDouble() * 1.0;
                        double pz = entity.getZ() + (entity.level().random.nextDouble() - 0.5) * 0.8;
                        emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 0);
                    }
                }
                // 【流血粒子修复】额外纯红色血雾层：使用 DustParticleOptions
                // RGB(180, 20, 20) 暗红色，尺寸 0.6，让流血玩家身体周围有清晰的红色血雾
                DustParticleOptions bloodDust = new DustParticleOptions(
                        new Vector3f(180.0F / 255.0F, 20.0F / 255.0F, 20.0F / 255.0F), 0.6F);
                for (int i = 0; i < 3; i++) {
                    double bx = entity.getX() + (entity.level().random.nextDouble() - 0.5) * 0.6;
                    double by = entity.getY() + entity.level().random.nextDouble() * 1.6;
                    double bz = entity.getZ() + (entity.level().random.nextDouble() - 0.5) * 0.6;
                    serverLevel.sendParticles(bloodDust, bx, by, bz, 1, 0.0, 0.05, 0.0, 0.0);
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
