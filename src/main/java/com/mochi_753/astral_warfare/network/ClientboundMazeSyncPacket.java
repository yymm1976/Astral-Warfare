package com.mochi_753.astral_warfare.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.mochi_753.astral_warfare.AstralWarfare;

// 星轨迷宫同步网络包：服务端→客户端
// 同步迷宫中心位置、当前激活列组、网格大小
// 客户端 StarTrackMazeRenderer 使用此数据绘制网格线
public record ClientboundMazeSyncPacket(
        double cx,         // 网格中心 X
        double cy,         // 网格中心 Y
        double cz,         // 网格中心 Z
        int activeGroup,   // 当前激活列组（0=偶数列，1=奇数列）
        int gridSize       // 网格大小
) implements CustomPacketPayload {

    public static final Type<ClientboundMazeSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "maze_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMazeSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeDouble(pkt.cx);
                        buf.writeDouble(pkt.cy);
                        buf.writeDouble(pkt.cz);
                        buf.writeInt(pkt.activeGroup);
                        buf.writeInt(pkt.gridSize);
                    },
                    buf -> new ClientboundMazeSyncPacket(
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readInt(), buf.readInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // 客户端接收到迷宫同步包时更新缓存
    // S-07修复：缓存逻辑已移入 MazeDataCache（独立职责 + volatile）
    public void updateClientCache() {
        com.mochi_753.astral_warfare.client.MazeDataCache.setLastMazeData(
                new MazeData(cx, cy, cz, activeGroup, gridSize, System.currentTimeMillis()));
    }

    // 迷宫渲染数据记录
    public record MazeData(double cx, double cy, double cz, int activeGroup, int gridSize, long timestamp) {}
}
