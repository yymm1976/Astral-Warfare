package com.mochi_753.astral_warfare.network;

import com.mochi_753.astral_warfare.AstralWarfare;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 服务端→客户端 Lodestone 粒子特效同步包
// 服务端通过此包通知客户端在指定位置生成 Lodestone 粒子
// 使用 particleTypeId（int）而非直接传递 ParticleType 对象，避免注册表引用的跨端一致性问题
// extraData 用于传递颜色变体等额外参数，不同粒子类型可按需解读
//
// Side 安全：此类不引用任何客户端专属类（如 StellaParticles）
// 客户端处理逻辑通过 ModPayloads 中的 playToClient handler 间接引用
public record ClientboundLodestoneParticlePacket(int particleTypeId, double x, double y, double z, int extraData) implements CustomPacketPayload {

    // 包的唯一标识符，NeoForge 通过此 ID 路由到对应的处理器
    public static final Type<ClientboundLodestoneParticlePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "lodestone_particle"));

    // StreamCodec 负责将包数据写入/读出网络缓冲区
    // RegistryFriendlyByteBuf 是 NeoForge 1.21.x Play 阶段网络包的标准缓冲区类型
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLodestoneParticlePacket> STREAM_CODEC =
            StreamCodec.of(ClientboundLodestoneParticlePacket::encode, ClientboundLodestoneParticlePacket::decode);

    // 将包数据编码到网络缓冲区
    private static void encode(RegistryFriendlyByteBuf buf, ClientboundLodestoneParticlePacket pkt) {
        buf.writeInt(pkt.particleTypeId);
        buf.writeDouble(pkt.x);
        buf.writeDouble(pkt.y);
        buf.writeDouble(pkt.z);
        buf.writeInt(pkt.extraData);
    }

    // 从网络缓冲区解码包数据
    private static ClientboundLodestoneParticlePacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundLodestoneParticlePacket(
                buf.readInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
