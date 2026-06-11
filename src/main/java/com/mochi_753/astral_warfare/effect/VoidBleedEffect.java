package com.mochi_753.astral_warfare.effect;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.init.ModDamageTypes;
import com.mochi_753.astral_warfare.init.ModEffects;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

// 虚空流血效果 - 三段普攻第三段背刺的复合减益 MobEffect
// 效果机制（借鉴 FEL Scorch/VacuumErosion/Sever 设计模式）：
//   1. 双伤害：每 40tick 同时造成虚空伤害 + 窒息伤害（原版溺水伤害类型）
//   2. 削弱攻击：降低 25% 攻击力，每级额外 -5%
//   3. 叠层爆发：每 40tick 自动升级 amplifier，达到 5 层（amp=4）时触发"虚空爆发"
//      — 额外造成 8+amp×2 虚空爆发伤害 + 冲击波粒子，然后效果提前结束
// 颜色：暗红色 (0x8B0000)，暗示"虚空侵蚀血液"
public class VoidBleedEffect extends MobEffect {

    // 每 40 tick（2 秒）造成一次伤害并升级叠层
    private static final int DAMAGE_INTERVAL = 40;
    // 虚空伤害基础值：2 点 + amplifier * 1 点
    private static final float VOID_DAMAGE_BASE = 2.0F;
    // 窒息伤害基础值：1 点 + amplifier * 0.5 点
    // 窒息伤害低于虚空伤害，作为辅助伤害类型增加伤害多样性
    private static final float DROWN_DAMAGE_BASE = 1.0F;
    // 虚空爆发伤害基础值：8 点 + amplifier * 2 点
    private static final float ERUPTION_DAMAGE_BASE = 8.0F;
    // 叠层爆发阈值：amplifier 达到此值时触发虚空爆发（0-indexed，即第5层）
    private static final int ERUPTION_AMPLIFIER = 4;

    public VoidBleedEffect(MobEffectCategory category, int color) {
        super(category, color);
        // 削弱攻击力：ADD_MULTIPLIED_BASE -0.25 = 降低基础攻击力的 25%
        this.addAttributeModifier(Attributes.ATTACK_DAMAGE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "void_bleed_attack_reduction"),
                -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return true;

        // === 双伤害机制 ===
        // 虚空伤害：虚空本源力量侵蚀，绕过盔甲
        float voidDamage = VOID_DAMAGE_BASE + amplifier * 1.0F;
        entity.hurt(ModDamageTypes.voidBleed(serverLevel), voidDamage);

        // 窒息伤害：使用原版溺水伤害类型（DamageTypes.DROWN）
        // 溺水伤害同样绕过盔甲，且死亡消息为"溺亡"，与虚空伤害形成双重视觉反馈
        float drownDamage = DROWN_DAMAGE_BASE + amplifier * 0.5F;
        entity.hurt(serverLevel.damageSources().drown(), drownDamage);

        // === 叠层爆发检测 ===
        if (amplifier >= ERUPTION_AMPLIFIER) {
            // 触发虚空爆发：额外伤害 + 粒子特效 + 效果移除
            float eruptionDamage = ERUPTION_DAMAGE_BASE + amplifier * 2.0F;
            entity.hurt(ModDamageTypes.voidEruption(serverLevel), eruptionDamage);

            // 虚空爆发粒子：冲击波 + 星光爆发
            if (entity instanceof Player || entity.level().random.nextFloat() < 0.5F) {
                try (ParticleEmitter emitter = new ParticleEmitter(entity)) {
                    // 冲击波环：从中心向外扩散
                    for (int i = 0; i < 12; i++) {
                        double angle = (Math.PI * 2 / 12) * i;
                        double r = 1.5;
                        double px = entity.getX() + Math.cos(angle) * r;
                        double py = entity.getY() + 0.5;
                        double pz = entity.getZ() + Math.sin(angle) * r;
                        emitter.add(StellaParticles.ID_IMPACT_WAVE, px, py, pz, 0);
                    }
                    // 星光粒子：从身体中心向上爆发
                    for (int i = 0; i < 8; i++) {
                        double px = entity.getX() + (entity.level().random.nextDouble() - 0.5) * 0.8;
                        double py = entity.getY() + entity.level().random.nextDouble() * 2.0;
                        double pz = entity.getZ() + (entity.level().random.nextDouble() - 0.5) * 0.8;
                        emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 0);
                    }
                }
            }

            // 虚空爆发后移除效果（返回 false = 提前结束效果）
            return false;
        }

        // === 叠层升级 ===
        // 借鉴 FEL Scorch 机制：每次伤害触发时自动升级 amplifier
        // 通过重新施加效果实例实现升级（原版 MobEffect 的 amplifier 是只读的）
        // ModEffects.VOID_BLEED 是 DeferredHolder，可直接作为 Holder<MobEffect> 使用
        MobEffectInstance currentEffect = entity.getEffect(ModEffects.VOID_BLEED);
        if (currentEffect != null) {
            int newAmplifier = amplifier + 1;
            int remainingDuration = currentEffect.getDuration();
            // 重新施加升级后的效果实例
            entity.addEffect(new MobEffectInstance(
                    ModEffects.VOID_BLEED,
                    remainingDuration,
                    newAmplifier,
                    currentEffect.isAmbient(),
                    currentEffect.isVisible(),
                    currentEffect.showIcon()
            ));
        }

        // === 常规粒子效果 ===
        // 暗红色余烬 + 虚空火花 + 红色血雾
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
            // 额外纯红色血雾层：使用 DustParticleOptions
            DustParticleOptions bloodDust = new DustParticleOptions(
                    new Vector3f(180.0F / 255.0F, 20.0F / 255.0F, 20.0F / 255.0F), 0.6F);
            for (int i = 0; i < 3; i++) {
                double bx = entity.getX() + (entity.level().random.nextDouble() - 0.5) * 0.6;
                double by = entity.getY() + entity.level().random.nextDouble() * 1.6;
                double bz = entity.getZ() + (entity.level().random.nextDouble() - 0.5) * 0.6;
                serverLevel.sendParticles(bloodDust, bx, by, bz, 1, 0.0, 0.05, 0.0, 0.0);
            }
        }

        // 叠层升级后旧效果实例会被新实例覆盖，此处返回 true 无实际影响
        return true;
    }

    // duration % DAMAGE_INTERVAL == DAMAGE_INTERVAL - 1
    // 确保效果在正确的 tick 触发
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % DAMAGE_INTERVAL == DAMAGE_INTERVAL - 1;
    }
}
