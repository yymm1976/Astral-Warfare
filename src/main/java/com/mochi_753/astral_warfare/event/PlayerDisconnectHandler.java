package com.mochi_753.astral_warfare.event;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.effect.VoidEntrapmentEffect;
import com.mochi_753.astral_warfare.init.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 玩家断开连接事件处理器
// 处理边缘 case：玩家在被"虚空禁锢"效果影响时突然下线
// 防止玩家下次上线时永久携带该效果导致无法移动
//
// 监听 PlayerEvent.PlayerLoggedOutEvent，在玩家退出时自动清除其身上的虚空禁锢效果
// 此事件仅在服务端触发（包括集成服务端和独立服务端），无需 Dist 限制
// bus 参数省略：默认值为 GAME，无需显式指定
@EventBusSubscriber(modid = AstralWarfare.MOD_ID)
public class PlayerDisconnectHandler {

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // 清除虚空禁锢效果，防止玩家下次上线时永久卡死
        // ModEffects.VOID_ENTRAPMENT 是 DeferredHolder，直接传入 removeEffect
        if (player.hasEffect(ModEffects.VOID_ENTRAPMENT)) {
            player.removeEffect(ModEffects.VOID_ENTRAPMENT);
        }

        // 清理跳跃挣脱追踪状态，防止静态 Map 内存泄漏
        // 玩家下线时效果已移除，但 onEffectRemoved 可能未触发（断线时序问题）
        VoidEntrapmentEffect.cleanupPlayer(player.getUUID());
    }
}
