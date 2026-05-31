package com.mochi_753.astral_warfare.init;

import com.mochi_753.astral_warfare.AstralWarfare;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

// 自定义创造模式物品栏页面
// 将模组所有物品和刷怪蛋集中到一个独立标签页，方便玩家查找
// 使用 DeferredRegister 注册，在主类中通过 modEventBus 注册到 MOD_BUS
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AstralWarfare.MOD_ID);

    // 星穹战争专属创造物品栏
    // 图标：夜幕星盘（模组最具代表性的道具）
    public static final Supplier<CreativeModeTab> ASTRAL_WARFARE_TAB =
            CREATIVE_MODE_TABS.register("astral_warfare_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.astral_warfare"))
                            .icon(() -> new ItemStack(ModItems.NOCTURNAL_ASTROLABE.get()))
                            .displayItems((parameters, output) -> {
                                // 物品按获取顺序排列：召唤道具 → 战利品 → 刷怪蛋
                                output.accept(ModItems.NOCTURNAL_ASTROLABE.get());
                                output.accept(ModItems.VOID_HALBERD.get());
                                output.accept(ModItems.STELLA_CORE.get());
                                output.accept(ModItems.STELLA_EVOKER_SPAWN_EGG.get());
                                output.accept(ModItems.STARCORE_GOLEM_SPAWN_EGG.get());
                            })
                            .build());
}
