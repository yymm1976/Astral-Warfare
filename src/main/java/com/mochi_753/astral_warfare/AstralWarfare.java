package com.mochi_753.astral_warfare;

import com.mochi_753.astral_warfare.client.ClientEvents;
import com.mochi_753.astral_warfare.client.PlayerAnimationHandler;
import com.mochi_753.astral_warfare.client.postprocess.SingularityPostProcessor;
import com.mochi_753.astral_warfare.init.ModAttachments;
import com.mochi_753.astral_warfare.init.ModConfig;
import com.mochi_753.astral_warfare.init.ModCreativeTabs;
import com.mochi_753.astral_warfare.init.ModEffects;
import com.mochi_753.astral_warfare.init.ModEntities;
import com.mochi_753.astral_warfare.init.ModItems;
import com.mochi_753.astral_warfare.init.ModSounds;
import com.mochi_753.astral_warfare.network.ModPayloads;
import com.mochi_753.astral_warfare.worldgen.ModStructures;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import team.lodestar.lodestone.systems.postprocess.PostProcessHandler;

// 模组主入口类，由 NeoForge 通过 @Mod 注解自动发现和加载
@Mod(AstralWarfare.MOD_ID)
public class AstralWarfare {
    public static final String MOD_ID = "astral_warfare";

    // NeoForge 通过构造函数注入 MOD 事件总线和模组容器
    public AstralWarfare(IEventBus modEventBus, ModContainer modContainer) {
        // 将所有 DeferredRegister 注册到 MOD 事件总线，确保在正确的注册阶段完成注册
        ModEntities.ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModEffects.MOB_EFFECTS.register(modEventBus);
        ModStructures.STRUCTURE_TYPES.register(modEventBus);
        ModStructures.STRUCTURE_PIECE_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);

        // 监听实体属性创建事件，为自定义实体注册属性（生命值、移速等）
        modEventBus.addListener(ModEntities::onEntityAttributeCreation);

        // 监听网络包注册事件，注册自定义 CustomPacketPayload
        modEventBus.addListener(ModPayloads::onRegisterPayloadHandlers);

        // 注册服务端配置文件（astral_warfare-server.toml）
        // NeoForge 会自动在 config 目录生成并管理此文件
        modContainer.registerConfig(Type.SERVER, ModConfig.SPEC);

        // 配置热重载监听：手动注册到 MOD_BUS，替代 @EventBusSubscriber(bus = Bus.MOD)
        // Bus.MOD 已被标记 @removal，使用 addListener 是 1.21.1 推荐的方式
        modEventBus.addListener(ModConfig::onConfigReload);

        // 客户端 MOD_BUS 事件手动注册
        // 通过 FMLEnvironment.dist 判断当前运行环境，避免在独立服务端加载客户端类
        // 这替代了 @EventBusSubscriber(bus = Bus.MOD) 的方式，消除 @removal 警告
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientEvents(modEventBus);
        }

        // GAME_BUS 事件监听器通过 @EventBusSubscriber 自动注册，无需手动注册
        // StellaDamageEventHandler：虚弱状态伤害放大
        // PlayerDisconnectHandler：玩家断线清除禁锢效果
        // ClientManaData：客户端法力缓存清理
    }

    // 客户端事件注册，仅在客户端环境调用
    // 通过方法引用延迟加载 ClientEvents 类，确保独立服务端不会触发类加载
    private static void registerClientEvents(IEventBus modEventBus) {
        modEventBus.addListener(ClientEvents::onRegisterGuiLayers);
        modEventBus.addListener(ClientEvents::onRegisterRenderers);
        modEventBus.addListener(ClientEvents::onRegisterLayerDefinitions);

        // 客户端初始化事件：注册 Lodestone PostProcessor
        // PostProcessHandler.addInstance 必须在客户端主线程执行
        // enqueueWork 确保操作在渲染线程安全执行，避免并发问题
        modEventBus.addListener(AstralWarfare::onClientSetup);
    }

    // 客户端初始化回调：注册黑洞后处理器到 Lodestone 系统
    // PostProcessHandler 会在 AFTER_LEVEL 阶段自动调用所有已注册 PostProcessor 的 applyPostProcess()
    // FMLClientSetupEvent 在 MOD_BUS 上触发，此时游戏资源已加载完毕
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PostProcessHandler.addInstance(SingularityPostProcessor.INSTANCE);
            // 注册 PAL 动画层：虚空禁锢玩家动画
            // PAL 要求在 enqueueWork 中注册，确保在主线程执行
            PlayerAnimationHandler.registerAnimationLayer();
        });
    }
}
