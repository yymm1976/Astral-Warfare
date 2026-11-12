package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.init.ModEffects;
import com.mochi_753.astral_warfare.network.ServerboundJumpInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 客户端跳跃输入检测器
// 当玩家被虚空禁锢时，检测跳跃键按下并发送网络包到服务端
// 用于实现"连按空格挣脱禁锢"机制
//
// Side 安全：此类仅在客户端执行，通过 @EventBusSubscriber(value = Dist.CLIENT) 隔离
// 独立服务端不会加载此类
@EventBusSubscriber(modid = AstralWarfare.MOD_ID, value = Dist.CLIENT)
public class JumpInputHandler {

    // 上一 tick 跳跃键是否按下（检测上升沿）
    private static boolean wasJumpKeyDown = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 仅当玩家被虚空禁锢时才检测跳跃输入
        if (!player.hasEffect(ModEffects.VOID_ENTRAPMENT)) {
            wasJumpKeyDown = false;
            return;
        }

        // 检测跳跃键上升沿：上一 tick 没按，这一 tick 按了
        // KeyMapping.isDown() 是 1.21.x 的公开方法（替代了已移除的 isDown 字段）
        boolean isJumpKeyDown = mc.options.keyJump.isDown();
        if (isJumpKeyDown && !wasJumpKeyDown) {
            // 发送跳跃输入包到服务端
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ServerboundJumpInputPacket());
        }
        wasJumpKeyDown = isJumpKeyDown;
    }
}
