package com.mochi_753.astral_warfare.network;

import com.mochi_753.astral_warfare.attachment.ManaData;
import com.mochi_753.astral_warfare.client.ClientManaData;
import com.mochi_753.astral_warfare.client.ScreenShakeManager;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// 网络包注册中心
// 在 RegisterPayloadHandlersEvent（MOD_BUS 事件）中注册所有自定义网络包
// NeoForge 1.21.x 使用 PayloadRegistrar 替代旧版 NetworkRegistry/ChannelBuilder
//
// Side 安全：客户端处理逻辑通过 lambda 内联在此处
// NeoForge 的 PayloadRegistrar 会在注册阶段记录 handler，但只在对应 Side 才会执行
// 因此 ClientManaData 的引用在独立服务端不会被类加载器解析
public class ModPayloads {

    // 响应 RegisterPayloadHandlersEvent，注册所有自定义网络包
    // 此事件在 MOD 事件总线上触发，属于模组加载阶段事件
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        // registrar 的版本号用于 NeoForge 连接时的协议协商
        // 同一 modid 下所有包必须使用相同版本，否则 Neo-Neo 连接会失败
        // 版本策略：仅在包结构发生不兼容变更时递增（如新增/删除字段）
        // 纯数值变更（如调整法力上限）不需要递增版本号
        PayloadRegistrar registrar = event.registrar("1");

        // 注册客户端绑定包：服务端→客户端
        // playToClient 指定此包在 Play 阶段从服务端发往客户端
        // 客户端处理逻辑直接写在 lambda 中，避免包类引用客户端专属类
        // PayloadRegistrar 默认将处理器包装在 MainThreadPayloadHandler 中，确保在主线程执行
        //
        // 架构注释：此 lambda 引用了 ClientManaData（客户端专属类）
        // 安全性依赖于 NeoForge 1.21.1 PayloadRegistrar 的延迟解析行为：
        //   - 注册阶段仅记录 handler 引用，不立即解析 lambda 中的类
        //   - 独立服务端不会执行 playToClient 的 handler，因此不会触发 ClientManaData 的类加载
        // 若未来 NeoForge 版本变更此延迟加载行为，需将客户端处理逻辑迁移到
        // 独立的 ClientPacketHandler 类（位于 client 包，通过 @EventBusSubscriber 隔离）
        //
        // 主线程安全：所有修改游戏状态的逻辑必须通过 enqueueWork 包裹
        // 网络包在 Netty 线程上解码，直接操作游戏状态会导致并发问题
        // enqueueWork 将任务调度到主线程执行，确保线程安全
        registrar.playToClient(
                ClientboundStellaManaPacket.TYPE,
                ClientboundStellaManaPacket.STREAM_CODEC,
                (ClientboundStellaManaPacket pkt, IPayloadContext ctx) ->
                        ctx.enqueueWork(() ->
                                ClientManaData.setManaData(pkt.entityUUID(), new ManaData(pkt.currentMana(), pkt.maxMana(), pkt.manaSystemDisabled()))
                        )
        );

        // 法力值移除包：BOSS 消失时通知客户端清除缓存
        // 防止法力条在 BOSS 脱战消失或死亡消散后仍残留在屏幕上
        registrar.playToClient(
                ClientboundStellaManaRemovePacket.TYPE,
                ClientboundStellaManaRemovePacket.STREAM_CODEC,
                (ClientboundStellaManaRemovePacket pkt, IPayloadContext ctx) ->
                        ctx.enqueueWork(() ->
                                ClientManaData.removeManaData(pkt.entityUUID())
                        )
        );

        // Lodestone 粒子特效包：服务端→客户端
        // 服务端发送粒子生成指令，客户端根据 particleTypeId 选择对应的 Lodestone 粒子效果
        // Side 安全：StellaParticles 和 ClientLevel 是客户端专属类
        // 安全性依赖于 NeoForge PayloadRegistrar 的延迟解析行为（同上方法力包说明）
        registrar.playToClient(
                ClientboundLodestoneParticlePacket.TYPE,
                ClientboundLodestoneParticlePacket.STREAM_CODEC,
                (ClientboundLodestoneParticlePacket pkt, IPayloadContext ctx) ->
                        ctx.enqueueWork(() -> {
                            Minecraft mc = Minecraft.getInstance();
                            ClientLevel level = mc.level;
                            if (level == null) return;
                            StellaParticles.handlePacket(
                                    level, pkt.particleTypeId(),
                                    pkt.x(), pkt.y(), pkt.z(), pkt.extraData()
                            );
                        })
        );

        // 屏幕震动包：服务端→客户端
        // 用于处决砸地等场景的屏幕震动效果
        // Side 安全：ScreenShakeManager 是客户端专属类
        // 安全性依赖于 NeoForge PayloadRegistrar 的延迟解析行为（同上方法力包说明）
        registrar.playToClient(
                ClientboundScreenShakePacket.TYPE,
                ClientboundScreenShakePacket.STREAM_CODEC,
                (ClientboundScreenShakePacket pkt, IPayloadContext ctx) ->
                        ctx.enqueueWork(() ->
                                ScreenShakeManager.triggerShake(pkt.intensity(), pkt.duration(), pkt.falloff())
                        )
        );

        // 粒子批量包：服务端→客户端
        // 将多个同类型粒子合并为单个网络包发送，显著降低网络开销
        // 替代逐粒子发送的 ClientboundLodestoneParticlePacket 模式
        // 客户端处理：遍历批次中的每个粒子条目，调用 StellaParticles.handlePacket 生成粒子
        registrar.playToClient(
                ClientboundParticleBatchPacket.TYPE,
                ClientboundParticleBatchPacket.STREAM_CODEC,
                (ClientboundParticleBatchPacket pkt, IPayloadContext ctx) ->
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
                        })
        );

        // 星轨迷宫同步包：服务端→客户端
        // 同步迷宫中心位置、激活列组、网格大小
        // 客户端 StarTrackMazeRenderer 使用此数据绘制网格线
        registrar.playToClient(
                ClientboundMazeSyncPacket.TYPE,
                ClientboundMazeSyncPacket.STREAM_CODEC,
                (ClientboundMazeSyncPacket pkt, IPayloadContext ctx) ->
                        ctx.enqueueWork(() -> pkt.updateClientCache())
        );
    }
}
