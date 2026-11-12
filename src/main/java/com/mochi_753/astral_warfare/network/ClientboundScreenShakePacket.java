package com.mochi_753.astral_warfare.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.mochi_753.astral_warfare.AstralWarfare;

// 屏幕震动网络包：服务端→客户端
// 用于二阶段处决砸地等场景的屏幕震动效果
// Lodestone 1.7.0 没有内置屏幕震动功能，需要自行实现
public record ClientboundScreenShakePacket(
        float intensity,   // 震动强度（偏移相机的最大角度）
        int duration,      // 震动持续 tick 数
        float falloff      // 每 tick 衰减量
) implements CustomPacketPayload {

    public static final Type<ClientboundScreenShakePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "screen_shake"));

    // 序列化：写入 intensity + duration + falloff
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundScreenShakePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeFloat(pkt.intensity);
                        buf.writeInt(pkt.duration);
                        buf.writeFloat(pkt.falloff);
                    },
                    buf -> new ClientboundScreenShakePacket(buf.readFloat(), buf.readInt(), buf.readFloat())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
