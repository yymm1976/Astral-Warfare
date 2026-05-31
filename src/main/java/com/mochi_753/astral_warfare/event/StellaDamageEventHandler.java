package com.mochi_753.astral_warfare.event;

import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mochi_753.astral_warfare.init.ModConstants;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

// 全局伤害事件监听器
// 通过 NeoForge 的 LivingDamageEvent.Pre 动态截获对 StellaEvoker 的伤害
// 实现虚弱状态下的伤害放大，替代在 hurt() 中硬编码的方式
// 优势：不破坏与其他模组（如拔刀剑、饰品模组）的兼容性
// 注意：此事件在 GAME_BUS 上触发，属于游戏运行时事件
// 使用 @EventBusSubscriber 静态注册，与类中全 static 方法风格一致
// bus 参数省略：默认值为 GAME，无需显式指定
@EventBusSubscriber(modid = com.mochi_753.astral_warfare.AstralWarfare.MOD_ID)
public class StellaDamageEventHandler {

    // 虚弱状态伤害倍率：1.5 倍（+50%），集中管理于 ModConstants
    private static final float WEAKENED_DAMAGE_MULTIPLIER = ModConstants.WEAKENED_DAMAGE_MULTIPLIER;

    // 监听 LivingDamageEvent.Pre（伤害应用前的最终拦截点）
    // 在此处修改伤害值不会影响其他模组对同一事件的监听
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        // 仅对 StellaEvokerEntity 生效
        if (!(event.getEntity() instanceof StellaEvokerEntity evoker)) {
            return;
        }

        // 虚弱状态下受到伤害增加 50%
        if (evoker.isWeakened()) {
            // 通过修改 Container 中的 newDamage 实现伤害放大
            // LivingDamageEvent.Pre 允许修改最终伤害值
            event.setNewDamage(event.getNewDamage() * WEAKENED_DAMAGE_MULTIPLIER);
        }
    }
}
