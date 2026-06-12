package com.mochi_753.astral_warfare.network;

import com.mochi_753.astral_warfare.effect.VoidEntrapmentEffect;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.lang.reflect.InvocationTargetException;

// 网络包注册中心
// 在 RegisterPayloadHandlersEvent（MOD_BUS 事件）中注册所有自定义网络包
// NeoForge 1.21.x 使用 PayloadRegistrar 替代旧版 NetworkRegistry/ChannelBuilder
//
// Side 安全：所有客户端处理逻辑仍隔离在 ClientPayloadHandler（client 包）
// 此 common 类不直接 import 客户端类，独立服务端只注册包类型，不解析客户端专属类
public class ModPayloads {

    // 响应 RegisterPayloadHandlersEvent，注册所有自定义网络包
    // 此事件在 MOD 事件总线上触发，属于模组加载阶段事件
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        // registrar 的版本号用于 NeoForge 连接时的协议协商
        // 同一 modid 下所有包必须使用相同版本，否则 Neo-Neo 连接会失败
        // 版本策略：仅在包结构发生不兼容变更时递增（如新增/删除字段）
        // 纯数值变更（如调整法力上限）不需要递增版本号
        PayloadRegistrar registrar = event.registrar("1");

        registerClientboundPayloads(registrar);

        // 跳跃输入包：客户端→服务端
        // 当玩家被虚空禁锢时，客户端检测跳跃键按下并发送此包
        // 服务端收到后累加跳跃计数，达到阈值（3秒10次）时挣脱禁锢
        // Side 安全：VoidEntrapmentEffect.handleJumpInput 是通用代码，不引用客户端类
        registrar.playToServer(
                ServerboundJumpInputPacket.TYPE,
                ServerboundJumpInputPacket.STREAM_CODEC,
                (ServerboundJumpInputPacket pkt, IPayloadContext ctx) ->
                        ctx.enqueueWork(() -> {
                            // IPayloadContext.player() 返回发送此包的玩家
                            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                                VoidEntrapmentEffect.handleJumpInput(serverPlayer);
                            }
                        })
        );
    }

    // 注册所有服务端→客户端的网络包
    // NeoForge 连接协商要求发送端和接收端都知道 payload 类型，因此独立服务端也必须注册这些包
    // 真正的客户端处理器通过反射延迟到客户端执行，避免 common 类直接引用 net.minecraft.client 相关类
    private static void registerClientboundPayloads(PayloadRegistrar registrar) {
        // 法力值同步包：服务端→客户端
        // 客户端处理：更新 ClientManaData 缓存
        registrar.playToClient(
                ClientboundStellaManaPacket.TYPE,
                ClientboundStellaManaPacket.STREAM_CODEC,
                (ClientboundStellaManaPacket pkt, IPayloadContext ctx) ->
                        handleClientboundOnClient("handleManaPacket", ClientboundStellaManaPacket.class, pkt, ctx)
        );

        // 法力值移除包：BOSS 消失时通知客户端清除缓存
        // 防止法力条在 BOSS 脱战消失或死亡消散后仍残留在屏幕上
        registrar.playToClient(
                ClientboundStellaManaRemovePacket.TYPE,
                ClientboundStellaManaRemovePacket.STREAM_CODEC,
                (ClientboundStellaManaRemovePacket pkt, IPayloadContext ctx) ->
                        handleClientboundOnClient("handleManaRemovePacket", ClientboundStellaManaRemovePacket.class, pkt, ctx)
        );

        // Lodestone 粒子特效包：服务端→客户端
        // 客户端处理：根据 particleTypeId 调用 StellaParticles 生成粒子
        registrar.playToClient(
                ClientboundLodestoneParticlePacket.TYPE,
                ClientboundLodestoneParticlePacket.STREAM_CODEC,
                (ClientboundLodestoneParticlePacket pkt, IPayloadContext ctx) ->
                        handleClientboundOnClient("handleLodestoneParticlePacket", ClientboundLodestoneParticlePacket.class, pkt, ctx)
        );

        // 屏幕震动包：服务端→客户端
        // 客户端处理：调用 ScreenShakeManager 触发相机震动
        registrar.playToClient(
                ClientboundScreenShakePacket.TYPE,
                ClientboundScreenShakePacket.STREAM_CODEC,
                (ClientboundScreenShakePacket pkt, IPayloadContext ctx) ->
                        handleClientboundOnClient("handleScreenShakePacket", ClientboundScreenShakePacket.class, pkt, ctx)
        );

        // 粒子批量包：服务端→客户端
        // 将多个同类型粒子合并为单个网络包发送，显著降低网络开销
        // 客户端处理：遍历批次条目，调用 StellaParticles.handlePacket 生成粒子
        registrar.playToClient(
                ClientboundParticleBatchPacket.TYPE,
                ClientboundParticleBatchPacket.STREAM_CODEC,
                (ClientboundParticleBatchPacket pkt, IPayloadContext ctx) ->
                        handleClientboundOnClient("handleParticleBatchPacket", ClientboundParticleBatchPacket.class, pkt, ctx)
        );

        // 星轨迷宫同步包：服务端→客户端
        // 同步迷宫中心位置、激活列组、网格大小
        // 客户端处理：更新 MazeDataCache 缓存供 StarTrackMazeRenderer 使用
        registrar.playToClient(
                ClientboundMazeSyncPacket.TYPE,
                ClientboundMazeSyncPacket.STREAM_CODEC,
                (ClientboundMazeSyncPacket pkt, IPayloadContext ctx) ->
                        handleClientboundOnClient("handleMazeSyncPacket", ClientboundMazeSyncPacket.class, pkt, ctx)
        );
    }

    // 客户端包处理器桥接方法
    // FMLEnvironment.dist 是 NeoForge 1.21.1 的环境字段；只有客户端才反射加载 client 包中的处理类
    private static void handleClientboundOnClient(String methodName, Class<?> packetClass, Object packet, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        try {
            Class<?> handlerClass = Class.forName("com.mochi_753.astral_warfare.client.ClientPayloadHandler");
            handlerClass.getMethod(methodName, packetClass, IPayloadContext.class).invoke(null, packet, ctx);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Client payload handler is not available: " + methodName, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Client payload handler failed: " + methodName, cause);
        }
    }
}
