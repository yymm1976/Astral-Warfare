package com.mochi_753.astral_warfare.network;

import com.mochi_753.astral_warfare.AstralWarfare;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

// 服务端→客户端 Lodestone 粒子批量同步包
// 将同一 tick 内多个同类型粒子合并为单个网络包发送，显著降低网络开销
// 替代原有的 ClientboundLodestoneParticlePacket 单粒子包模式
//
// 性能对比（以处决砸地 120 个粒子为例）：
//   旧模式：120 个独立包 × 40 字节 = 4800 字节 + 120 倍包头开销
//   新模式：1 个批量包 × (4 + 120×32) 字节 ≈ 3844 字节 + 1 倍包头开销
//   节省：约 95% 的网络包数量和 20-30% 的带宽
//
// Side 安全：此类不引用任何客户端专属类
// 客户端处理逻辑通过 ModPayloads 中的 playToClient handler 间接引用
public record ClientboundParticleBatchPacket(int particleTypeId, List<ParticleEntry> entries) implements CustomPacketPayload {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientboundParticleBatchPacket.class);

    // 包的唯一标识符
    public static final Type<ClientboundParticleBatchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "particle_batch"));

    // 单个粒子条目：位置 + 额外数据
    // 使用 record 保证不可变性，避免并发修改问题
    public record ParticleEntry(double x, double y, double z, int extraData) {
    }

    // StreamCodec：负责批量数据的序列化/反序列化
    // 格式：[int 粒子类型ID][int 数量][N × (double x, double y, double z, int extraData)]
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundParticleBatchPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundParticleBatchPacket::encode, ClientboundParticleBatchPacket::decode);

    // 编码：先写粒子类型ID，再写条目数量，然后逐个写条目数据
    private static void encode(RegistryFriendlyByteBuf buf, ClientboundParticleBatchPacket pkt) {
        buf.writeInt(pkt.particleTypeId);
        buf.writeInt(pkt.entries.size());
        for (ParticleEntry entry : pkt.entries) {
            buf.writeDouble(entry.x);
            buf.writeDouble(entry.y);
            buf.writeDouble(entry.z);
            buf.writeInt(entry.extraData);
        }
    }

    // 解码：按相同顺序读取数据
    // Phase 32：上限从 512 提升到 1024，超过时截断+日志警告而非抛异常
    // 原因：Phase 28 将单批粒子数推到 360，某些法术同类型粒子累积超过 512 导致崩溃
    private static final int MAX_PARTICLES_PER_BATCH = 1024;

    private static ClientboundParticleBatchPacket decode(RegistryFriendlyByteBuf buf) {
        int particleTypeId = buf.readInt();
        int count = buf.readInt();
        if (count < 0) {
            throw new IllegalArgumentException("Invalid particle batch count: " + count);
        }
        if (count > MAX_PARTICLES_PER_BATCH) {
            LOGGER.warn("Particle batch count {} exceeds limit {}, truncating", count, MAX_PARTICLES_PER_BATCH);
            count = MAX_PARTICLES_PER_BATCH;
        }
        List<ParticleEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new ParticleEntry(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt()
            ));
        }
        return new ClientboundParticleBatchPacket(particleTypeId, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
