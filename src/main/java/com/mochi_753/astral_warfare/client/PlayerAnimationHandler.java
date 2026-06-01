package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.init.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ClientTickEvent;
import com.zigythebird.playeranim.PlayerAnimationAccess;
import com.zigythebird.playeranim.PlayerAnimationFactory;
import com.zigythebird.playeranim.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayState;

// 虚空禁锢玩家动画处理器
// 使用 PAL（Player Animation Library）在客户端播放"悬浮挣扎"姿态
// 当玩家受到 VOID_ENTRAPMENT 效果时，双臂微微张开、身体后仰
// 效果移除时，通过 PAL 的 fade-out 机制平滑过渡回默认姿态
//
// Side 安全：@EventBusSubscriber(value=Dist.CLIENT) 确保仅在客户端注册
// 动画层注册在 FMLClientSetupEvent.enqueueWork 中完成（PAL 要求）
@EventBusSubscriber(modid = AstralWarfare.MOD_ID, value = Dist.CLIENT)
public class PlayerAnimationHandler {

    // 动画层唯一标识：PAL 使用 ResourceLocation 区分不同模组的动画层
    private static final ResourceLocation ANIMATION_LAYER = ResourceLocation.fromNamespaceAndPath(
            AstralWarfare.MOD_ID, "void_entrapment_layer");

    // 动画ID：对应 void_entrapment.json 中 animations 对象的键名
    // PAL 的动画ID = ResourceLocation(modid, animation_name_in_json)
    private static final ResourceLocation VOID_ENTRAPMENT_ANIM = ResourceLocation.fromNamespaceAndPath(
            AstralWarfare.MOD_ID, "void_entrapment");

    // 跟踪上一次动画状态，避免每帧重复触发
    private static boolean wasEntrapped = false;

    // 注册 PAL 动画层
    // 必须在 FMLClientSetupEvent.enqueueWork 中调用（PAL NeoForge 要求）
    // priority=1500：高于普通装饰动画(1000)，属于重要游戏玩法动画
    public static void registerAnimationLayer() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                ANIMATION_LAYER, 1500,
                player -> new PlayerAnimationController(player,
                        (controller, state, animSetter) -> PlayState.STOP
                )
        );
    }

    // 客户端 tick 事件：检测本地玩家是否受到虚空禁锢效果
    // @SubscribeEvent 由 @EventBusSubscriber 自动注册到 GAME_BUS
    // Dist.CLIENT 确保服务端不会加载此类
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AbstractClientPlayer player = mc.player;

        // 检查玩家是否拥有虚空禁锢效果
        MobEffectInstance effect = player.getEffect(ModEffects.VOID_ENTRAPMENT);
        boolean isEntrapped = effect != null;

        // 状态变化时才触发动画，避免每tick重复调用
        if (isEntrapped && !wasEntrapped) {
            playAnimation(player);
        } else if (!isEntrapped && wasEntrapped) {
            stopAnimation(player);
        }

        wasEntrapped = isEntrapped;
    }

    // 播放虚空禁锢动画
    // replaceWithFade：带淡入淡出过渡的动画切换
    // 5 tick = 0.25 秒的过渡时间，避免肢体瞬跳
    private static void playAnimation(AbstractClientPlayer player) {
        PlayerAnimationController controller = (PlayerAnimationController)
                PlayerAnimationAccess.getPlayerAnimationLayer(player, ANIMATION_LAYER);
        if (controller != null) {
            controller.replaceWithFade(VOID_ENTRAPMENT_ANIM, 5, 5);
        }
    }

    // 停止虚空禁锢动画，平滑过渡回默认姿态
    // 传入 null 表示回到默认姿态，5 tick 淡出过渡
    private static void stopAnimation(AbstractClientPlayer player) {
        PlayerAnimationController controller = (PlayerAnimationController)
                PlayerAnimationAccess.getPlayerAnimationLayer(player, ANIMATION_LAYER);
        if (controller != null) {
            controller.replaceWithFade(null, 5, 5);
        }
    }
}
