package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.network.ClientboundMazeSyncPacket;

// 星轨迷宫客户端数据缓存（独立职责）
// 从 ClientboundMazeSyncPacket 中剥离缓存逻辑，遵循 SRP 原则
// volatile 确保网络线程写入与渲染线程读取的可见性
public class MazeDataCache {

    // volatile：网络包处理线程写入，渲染线程读取，确保跨线程可见性
    private static volatile ClientboundMazeSyncPacket.MazeData lastMazeData = null;

    public static ClientboundMazeSyncPacket.MazeData getLastMazeData() {
        return lastMazeData;
    }

    public static void setLastMazeData(ClientboundMazeSyncPacket.MazeData data) {
        lastMazeData = data;
    }

    public static void clear() {
        lastMazeData = null;
    }
}
