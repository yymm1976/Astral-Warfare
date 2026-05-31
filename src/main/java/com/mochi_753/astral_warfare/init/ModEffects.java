package com.mochi_753.astral_warfare.init;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.effect.VoidBleedEffect;
import com.mochi_753.astral_warfare.effect.VoidEntrapmentEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 自定义 MobEffect 注册中心
// 使用 DeferredRegister 在模组加载阶段完成注册
// 声明类型为 DeferredHolder（继承自 Holder<MobEffect>），
// 可直接传入 MobEffectInstance 构造函数和 hasEffect() 等需要 Holder 的 API
public class ModEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, AstralWarfare.MOD_ID);

    // 虚空禁锢：二阶段 BOSS 普攻命中的专属定身效果
    // 使用 DeferredHolder<MobEffect, MobEffect> 声明，可直接作为 Holder<MobEffect> 使用
    // DespairExecutionGoal 仅检测此效果来触发处决连招
    public static final DeferredHolder<MobEffect, MobEffect> VOID_ENTRAPMENT =
            MOB_EFFECTS.register("void_entrapment",
                    () -> new VoidEntrapmentEffect(MobEffectCategory.HARMFUL, 0x8B00FF));

    // 虚空流血：三段普攻第三段背刺的持续掉血效果
    // 每 2 秒造成一次虚空伤害，持续 6 秒，与虚空禁锢（定身）完全不同
    // 颜色：暗红色 (0x8B0000)，暗示"虚空侵蚀血液"
    public static final DeferredHolder<MobEffect, MobEffect> VOID_BLEED =
            MOB_EFFECTS.register("void_bleed",
                    () -> new VoidBleedEffect(MobEffectCategory.HARMFUL, 0x8B0000));
}
