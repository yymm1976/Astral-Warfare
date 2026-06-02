package com.mochi_753.astral_warfare.network;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

// 服务端粒子批量发射器
// 收集同一 tick 内的同类型粒子，在 tick 结束时一次性发送批量包
// 替代原有的逐粒子 sendToPlayersTrackingEntityAndSelf 调用
//
// 使用方式：
//   ParticleEmitter emitter = new ParticleEmitter(entity);
//   emitter.add(particleTypeId, x, y, z, extraData);
//   emitter.add(particleTypeId, x2, y2, z2, extraData);
//   emitter.flush(); // 发送批量包
//
// 或者使用 try-with-resources 自动 flush：
//   try (ParticleEmitter emitter = new ParticleEmitter(entity)) {
//       emitter.add(...);
//   }
//
// 蓝图对应：所有 BOSS 法术粒子、死亡演出粒子、转阶段粒子、处决粒子
public class ParticleEmitter implements AutoCloseable {

    // 追踪目标实体（用于确定包的发送范围）
    private final Entity trackingEntity;
    // 当前收集的粒子条目，按 particleTypeId 分组
    // 使用 ArrayList 而非 Map，因为同一 tick 内粒子类型通常不多（< 6 种）
    private final List<ClientboundParticleBatchPacket.ParticleEntry> currentBatch = new ArrayList<>();
    // 当前批次的粒子类型ID
    private int currentTypeId = -1;

    public ParticleEmitter(Entity trackingEntity) {
        this.trackingEntity = trackingEntity;
    }

    // 添加一个粒子到当前批次
    // 如果粒子类型与当前批次不同，先发送当前批次，再开启新批次
    public void add(int particleTypeId, double x, double y, double z, int extraData) {
        if (particleTypeId != currentTypeId && !currentBatch.isEmpty()) {
            flush();
        }
        currentTypeId = particleTypeId;
        currentBatch.add(new ClientboundParticleBatchPacket.ParticleEntry(x, y, z, extraData));
    }

    // 发送当前收集的粒子批次
    // 空批次时不发送网络包，避免浪费带宽
    // try-catch：网络发送失败时静默丢弃当前批次，不破坏调用方事务
    public void flush() {
        if (currentBatch.isEmpty() || currentTypeId < 0) {
            return;
        }
        try {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    trackingEntity,
                    new ClientboundParticleBatchPacket(currentTypeId, new ArrayList<>(currentBatch))
            );
        } catch (Exception t) {
            // 网络发送失败，静默丢弃当前批次
            // 【L15修复】改为 catch(Exception)，不再吞没 OutOfMemoryError 等 Error
            // OOM 等严重错误应向上传播，让 JVM 有机会处理
        }
        currentBatch.clear();
        currentTypeId = -1;
    }

    // AutoCloseable 实现：确保在 try-with-resources 中自动 flush
    // try-catch：网络异常时静默丢弃，不破坏调用方事务
    @Override
    public void close() {
        try {
            flush();
        } catch (Exception t) {
            // 网络异常时静默丢弃，不破坏调用方事务
        }
    }
}
