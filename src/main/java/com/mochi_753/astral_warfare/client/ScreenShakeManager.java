package com.mochi_753.astral_warfare.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 客户端屏幕震动管理器
// 通过修改相机角度实现屏幕震动效果
// 使用 NeoForge 的 ViewPortEvent.ComputeCameraAngles 事件修改相机偏移
// 纯客户端类：仅在 GAME_BUS + CLIENT 环境下注册
//
// 使用方式：服务端发送 ClientboundScreenShakePacket → 客户端调用 triggerShake()
// 震动参数：intensity（强度）+ duration（持续tick）+ falloff（每tick衰减）
@EventBusSubscriber(modid = "astral_warfare", value = Dist.CLIENT)
public class ScreenShakeManager {

    private static float intensity = 0;
    private static int remainingTicks = 0;
    private static float falloff = 0;

    // 触发屏幕震动
    // intensity: 偏移相机的最大角度（度）
    // duration: 震动持续 tick 数
    // falloff: 每 tick 衰减量
    public static void triggerShake(float intensity, int duration, float falloff) {
        ScreenShakeManager.intensity = intensity;
        ScreenShakeManager.remainingTicks = duration;
        ScreenShakeManager.falloff = falloff;
    }

    // 在相机角度计算事件中注入随机偏移
    // ViewPortEvent.ComputeCameraAngles 允许修改 yaw/pitch/roll
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (remainingTicks > 0 && intensity > 0) {
            RandomSource random = Minecraft.getInstance().player.getRandom();
            float yawOffset = (random.nextFloat() - 0.5f) * 2 * intensity;
            float pitchOffset = (random.nextFloat() - 0.5f) * 2 * intensity;
            event.setYaw(event.getYaw() + yawOffset);
            event.setPitch(event.getPitch() + pitchOffset);
            intensity -= falloff;
            if (intensity < 0) intensity = 0;
            remainingTicks--;
        }
    }
}
