package com.mochi_753.astral_warfare.network;

import com.mochi_753.astral_warfare.AstralWarfare;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// 服务端→客户端 法力值同步包
// 当 BOSS 的法力值在服务端发生变化时，通过此包向周围客户端广播最新数据
// 使用 NeoForge 1.21.x 的 CustomPacketPayload 系统，替代旧版 SimpleImpl 网络方案
//
// Side 安全：此类不引用任何客户端专属类（如 ClientManaData）
// 客户端处理逻辑通过 ModPayloads 中的 clientHandler lambda 间接引用
// 独立服务端环境下不会触发客户端类的加载，避免 NoClassDefFoundError
public record ClientboundStellaManaPacket(UUID entityUUID, int currentMana, int maxMana, boolean manaSystemDisabled) implements CustomPacketPayload {

    // 包的唯一标识符，NeoForge 通过此 ID 路由到对应的处理器
    public static final Type<ClientboundStellaManaPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "stella_mana"));

    // StreamCodec 负责将包数据写入/读出网络缓冲区
    // RegistryFriendlyByteBuf 是 NeoForge 1.21.x Play 阶段网络包的标准缓冲区类型
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundStellaManaPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundStellaManaPacket::encode, ClientboundStellaManaPacket::decode);

    // 将包数据编码到网络缓冲区
    private static void encode(RegistryFriendlyByteBuf buf, ClientboundStellaManaPacket pkt) {
        buf.writeUUID(pkt.entityUUID);
        buf.writeInt(pkt.currentMana);
        buf.writeInt(pkt.maxMana);
        buf.writeBoolean(pkt.manaSystemDisabled);
    }

    // 从网络缓冲区解码包数据
    private static ClientboundStellaManaPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundStellaManaPacket(buf.readUUID(), buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
