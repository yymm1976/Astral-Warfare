package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.attachment.ManaData;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.network.ClientboundLodestoneParticlePacket;
import com.mochi_753.astral_warfare.network.ClientboundMazeSyncPacket;
import com.mochi_753.astral_warfare.network.ClientboundParticleBatchPacket;
import com.mochi_753.astral_warfare.network.ClientboundScreenShakePacket;
import com.mochi_753.astral_warfare.network.ClientboundStellaManaPacket;
import com.mochi_753.astral_warfare.network.ClientboundStellaManaRemovePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// 客户端网络包处理器（仅在 Dist.CLIENT 环境下加载）
// 将 ModPayloads 中所有客户端专属处理逻辑集中到此隔离类
// 防止 ModPayloads 在独立服务端类加载时触发客户端类引用
//
// 每个 handle* 方法对应一种 Clientbound 包类型
// 所有方法内部已通过 enqueueWork 保证在主线程执行
@net.neoforged.api.distmarker.OnlyIn(Dist.CLIENT)
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {}

    // 法力值同步包处理：更新客户端法力缓存
    public static void handleManaPacket(ClientboundStellaManaPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                ClientManaData.setManaData(pkt.entityUUID(), new ManaData(pkt.currentMana(), pkt.maxMana(), pkt.manaSystemDisabled()))
        );
    }

    // 法力值移除包处理：BOSS 消失时清除缓存
    public static void handleManaRemovePacket(ClientboundStellaManaRemovePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                ClientManaData.removeManaData(pkt.entityUUID())
        );
    }

    // Lodestone 粒子包处理：根据粒子类型ID生成对应粒子效果
    public static void handleLodestoneParticlePacket(ClientboundLodestoneParticlePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) return;
            StellaParticles.handlePacket(
                    level, pkt.particleTypeId(),
                    pkt.x(), pkt.y(), pkt.z(), pkt.extraData()
            );
        });
    }

    // 屏幕震动包处理：触发相机震动效果
    public static void handleScreenShakePacket(ClientboundScreenShakePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                ScreenShakeManager.triggerShake(pkt.intensity(), pkt.duration(), pkt.falloff())
        );
    }

    // 粒子批量包处理：遍历批次条目，逐条生成粒子效果
    public static void handleParticleBatchPacket(ClientboundParticleBatchPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) return;
            for (ClientboundParticleBatchPacket.ParticleEntry entry : pkt.entries()) {
                StellaParticles.handlePacket(
                        level, pkt.particleTypeId(),
                        entry.x(), entry.y(), entry.z(), entry.extraData()
                );
            }
        });
    }

    // 星轨迷宫同步包处理：更新客户端迷宫缓存数据
    public static void handleMazeSyncPacket(ClientboundMazeSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                MazeDataCache.setLastMazeData(new ClientboundMazeSyncPacket.MazeData(
                        pkt.cx(), pkt.cy(), pkt.cz(), pkt.activeGroup(), pkt.gridSize(), System.currentTimeMillis()))
        );
    }
}
