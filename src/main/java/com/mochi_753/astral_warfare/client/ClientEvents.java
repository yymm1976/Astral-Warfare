package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.client.model.AstralCrystalModel;
import com.mochi_753.astral_warfare.client.model.StarcoreGolemModel;
import com.mochi_753.astral_warfare.init.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 客户端事件注册中心
 * 所有事件均为 MOD_BUS 事件（渲染器注册、GUI层注册、模型层注册）
 * 通过主类构造函数的 modEventBus.addListener() 手动注册
 * 避免使用 @EventBusSubscriber 的 bus 参数（已被标记 @removal）
 */
public class ClientEvents {

    // 法力条覆盖层的唯一标识
    private static final ResourceLocation STELLA_MANA_BAR =
            ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "stella_mana_bar");

    /**
     * 注册自定义 GUI 层
     */
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.BOSS_OVERLAY,
                STELLA_MANA_BAR,
                StellaBossBarOverlay.MANA_BAR_LAYER
        );
    }

    /**
     * 注册自定义实体渲染器
     */
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 星界水晶渲染器：使用通过 Blockbench MCP 创建的自定义模型
        event.registerEntityRenderer(
                ModEntities.ASTRAL_CRYSTAL.get(),
                AstralCrystalRenderer::new
        );
        // 星穹唤星者专属渲染器（紫色光环 + 虚空长戟）
        event.registerEntityRenderer(
                ModEntities.STELLA_EVOKER.get(),
                StellaEvokerRenderer::new
        );
        // 星核傀儡渲染器：使用 HumanoidModel + 紫色纹理
        event.registerEntityRenderer(
                ModEntities.STARCORE_GOLEM.get(),
                StarcoreGolemRenderer::new
        );
        // 夜幕黑洞渲染器：空实现，仅满足渲染系统非空约束
        // 缺少渲染器会导致 NullPointerException 崩溃
        event.registerEntityRenderer(
                ModEntities.NIGHTFALL_SINGULARITY.get(),
                NightfallSingularityRenderer::new
        );
    }

    /**
     * 注册模型层定义
     * 星界水晶使用通过 Blockbench MCP 创建的自定义模型定义
     * 星核傀儡使用通过 Blockbench MCP 创建的自定义模型定义
     */
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                AstralCrystalModel.LAYER,
                AstralCrystalModel::createBodyLayer
        );
        event.registerLayerDefinition(
                StarcoreGolemModel.LAYER,
                StarcoreGolemModel::createBodyLayer
        );
    }
}
