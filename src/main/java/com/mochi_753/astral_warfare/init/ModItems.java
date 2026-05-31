package com.mochi_753.astral_warfare.init;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.item.NocturnalAstrolabeItem;
import com.mochi_753.astral_warfare.item.StellaCoreItem;
import com.mochi_753.astral_warfare.item.VoidHalberdItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

// 物品注册中心，使用 DeferredRegister.Items 简化物品注册
// DeferredRegister.createItems 是 NeoForge 提供的便捷方法，专门用于物品注册
public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(AstralWarfare.MOD_ID);

    // 夜幕星盘 - BOSS 召唤道具
    // 使用自定义 NocturnalAstrolabeItem 替代原版 Item，实现右键环境判定逻辑
    // stacksTo(1)：BOSS 召唤道具不可堆叠，符合设计惯例
    public static final Supplier<NocturnalAstrolabeItem> NOCTURNAL_ASTROLABE =
            ITEMS.register("nocturnal_astrolabe",
                    () -> new NocturnalAstrolabeItem(new NocturnalAstrolabeItem.Properties().stacksTo(1)));

    // 虚空长戟 - BOSS 专属战利品
    // 高炫酷附魔外观的近战武器，二阶段 BOSS 手持的同款武器
    public static final Supplier<VoidHalberdItem> VOID_HALBERD =
            ITEMS.register("void_halberd", VoidHalberdItem::new);

    // 星穹核心 - BOSS 专属战利品
    // 蕴含星穹唤星者残余能量的核心，用于后续合成系统的关键材料
    public static final Supplier<StellaCoreItem> STELLA_CORE =
            ITEMS.register("stella_core", StellaCoreItem::new);

    // ==================== 刷怪蛋 ====================
    // 使用 NeoForge 的 DeferredSpawnEggItem 而非原版 SpawnEggItem
    // 原因：SpawnEggItem 构造函数要求直接传入 EntityType 实例，
    // 但物品注册先于实体注册完成，此时 EntityType Supplier 尚未解析
    // DeferredSpawnEggItem 接受 Supplier，延迟到运行时解析，避免循环依赖

    // 星穹唤星者刷怪蛋
    // 底色：深紫 (0x2B0A3D)，斑点色：星空蓝 (0x3AA6FF)
    public static final Supplier<DeferredSpawnEggItem> STELLA_EVOKER_SPAWN_EGG =
            ITEMS.register("stella_evoker_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.STELLA_EVOKER,
                            0x2B0A3D, 0x3AA6FF, new DeferredSpawnEggItem.Properties()));

    // 星核傀儡刷怪蛋
    // 底色：暗石灰 (0x444444)，斑点色：紫色荧光 (0x9B30FF)
    public static final Supplier<DeferredSpawnEggItem> STARCORE_GOLEM_SPAWN_EGG =
            ITEMS.register("starcore_golem_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.STARCORE_GOLEM,
                            0x444444, 0x9B30FF, new DeferredSpawnEggItem.Properties()));
}
