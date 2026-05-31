package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.attachment.ManaData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 客户端法力值数据缓存
// 服务端通过 ClientboundStellaManaPacket 将法力数据同步到客户端后，存储在此类中
// 渲染器（ManaBarRenderer）通过实体 UUID 查询对应的法力数据来绘制法力条
// 使用 ConcurrentHashMap 防止网络线程与渲染线程的并发问题
//
// 内存安全：监听 ClientPlayerNetworkEvent.LoggingOut，玩家退出服务器时自动清空缓存
// 防止玩家频繁换服导致客户端内存溢出（OOM）
// @EventBusSubscriber 必须标注，否则 @SubscribeEvent 方法不会被 NeoForge 自动注册
// bus 参数省略：默认值为 GAME，无需显式指定
@EventBusSubscriber(modid = AstralWarfare.MOD_ID, value = Dist.CLIENT)
public class ClientManaData {

    // 实体 UUID → 法力值数据的映射
    private static final Map<UUID, ManaData> MANA_MAP = new ConcurrentHashMap<>();

    // 设置指定实体的法力数据（由网络包处理器调用）
    public static void setManaData(UUID entityUUID, ManaData data) {
        MANA_MAP.put(entityUUID, data);
    }

    // 获取指定实体的法力数据（由渲染器调用）
    public static ManaData getManaData(UUID entityUUID) {
        return MANA_MAP.get(entityUUID);
    }

    // 移除指定实体的法力数据（实体消失时调用，防止内存泄漏）
    public static void removeManaData(UUID entityUUID) {
        MANA_MAP.remove(entityUUID);
    }

    // 获取所有法力数据（用于批量渲染）
    public static Map<UUID, ManaData> getAllManaData() {
        return Collections.unmodifiableMap(MANA_MAP);
    }

    // 清空所有缓存（玩家退出服务器或世界卸载时调用）
    public static void clear() {
        MANA_MAP.clear();
    }

    // 监听玩家退出服务器事件，强制清空静态 Map
    // ClientPlayerNetworkEvent.LoggingOut 在客户端断开连接时触发
    // 这是防止内存泄漏的关键防线：每次换服/退出世界后，旧世界的缓存数据必须被清除
    @SubscribeEvent
    public static void onClientPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
