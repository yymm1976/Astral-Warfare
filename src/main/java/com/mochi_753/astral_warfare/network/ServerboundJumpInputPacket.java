package com.mochi_753.astral_warfare.network;

import com.mochi_753.astral_warfare.AstralWarfare;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 客户端→服务端 跳跃输入同步包
// 当玩家被虚空禁锢时，客户端检测到跳跃键按下，发送此包通知服务端
// 服务端收到后累加跳跃计数，达到阈值时挣脱禁锢
//
// 为什么需要此包：
//   原版 player.jumping 字段是 protected 且不会从客户端同步到服务端
//   常规跳跃由客户端本地处理，服务端只看到位置变化
//   但虚空禁锢锁死了玩家移动，服务端无法从位置变化推断跳跃意图
//   因此需要客户端主动上报跳跃按键事件
//
// Side 安全：此类不引用任何客户端专属类
public record ServerboundJumpInputPacket() implements CustomPacketPayload {

    // 包的唯一标识符
    public static final Type<ServerboundJumpInputPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "jump_input"));

    // StreamCodec：此包无数据字段，仅作为事件信号使用
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundJumpInputPacket> STREAM_CODEC =
            StreamCodec.of(ServerboundJumpInputPacket::encode, ServerboundJumpInputPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundJumpInputPacket pkt) {
        // 无数据需要编码
    }

    private static ServerboundJumpInputPacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerboundJumpInputPacket();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
