package com.mochi_753.astral_warfare.network;

import com.mochi_753.astral_warfare.AstralWarfare;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// 服务端→客户端 法力值移除包
// 当 BOSS 实体被移除（脱战消失、死亡消散等）时，通知客户端清除对应的法力数据缓存
// 防止法力条在 BOSS 消失后仍残留在屏幕上
//
// Side 安全：此类不引用任何客户端专属类（如 ClientManaData）
// 客户端处理逻辑通过 ModPayloads 中的 clientHandler lambda 间接引用
public record ClientboundStellaManaRemovePacket(UUID entityUUID) implements CustomPacketPayload {

    public static final Type<ClientboundStellaManaRemovePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "stella_mana_remove"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundStellaManaRemovePacket> STREAM_CODEC =
            StreamCodec.of(ClientboundStellaManaRemovePacket::encode, ClientboundStellaManaRemovePacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundStellaManaRemovePacket pkt) {
        buf.writeUUID(pkt.entityUUID);
    }

    private static ClientboundStellaManaRemovePacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundStellaManaRemovePacket(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
