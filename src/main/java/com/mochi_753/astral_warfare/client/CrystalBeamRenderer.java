package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.entity.AstralCrystalEntity;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.Objects;

// 星光能量射线渲染器
// 在客户端的 AFTER_TRANSLUCENT_BLOCKS 阶段，从每个存活的星界水晶向 BOSS 胸口绘制星空蓝能量射线
// 当前使用原版渲染管线（RenderType.lines()），已使用 Lodestone WorldParticleBuilder 替代原版粒子
// 此事件监听器注册在 GAME_BUS（默认值），属于游戏运行时事件
// bus 参数省略：默认值为 GAME，无需显式指定
@EventBusSubscriber(modid = AstralWarfare.MOD_ID, value = Dist.CLIENT)
public class CrystalBeamRenderer {

    // 射线搜索半径
    private static final double BEAM_SEARCH_RADIUS = 30.0;
    // 射线颜色：星空蓝 (0x3AA6FF)
    private static final float BEAM_RED = 0.23F;
    private static final float BEAM_GREEN = 0.65F;
    private static final float BEAM_BLUE = 1.0F;
    // 射线透明度（基础值，会叠加脉冲效果）
    private static final float BEAM_ALPHA_BASE = 0.5F;
    // 射线脉冲透明度振幅
    private static final float BEAM_ALPHA_PULSE = 0.25F;
    // 射线粒子效果间隔（tick）
    // static 字段：切换维度/世界时不会归零
    // 这不影响渲染正确性——计数器仅控制粒子生成频率，无需与维度绑定
    // 若未来需要精确控制，可改为实例字段并在维度切换时重置
    private static int particleTickCounter = 0;
    // 上一次渲染时的维度资源键，用于检测维度切换并重置计数器
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastDimension = null;

    // 在世界渲染的 AFTER_TRANSLUCENT_BLOCKS 阶段绘制射线
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // 检测维度切换：重置粒子计数器，避免跨维度遗留
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> currentDim = mc.level.dimension();
        // S-05修复：使用 Objects.equals 替代 != 比较维度 ResourceKey
        // != 比较对象引用而非值，维度重载后可能产生不等的新实例
        if (!Objects.equals(lastDimension, currentDim)) {
            particleTickCounter = 0;
        }
        lastDimension = currentDim;

        particleTickCounter++;
        boolean shouldSpawnParticles = (particleTickCounter % 10 == 0);

        // 搜索范围内的水晶和 BOSS
        AABB searchBox = mc.player.getBoundingBox().inflate(64.0);
        List<AstralCrystalEntity> crystals = mc.level.getEntitiesOfClass(
                AstralCrystalEntity.class, searchBox,
                crystal -> crystal.isAlive() && crystal.isCrystalAlive()
        );

        if (crystals.isEmpty()) return;

        List<StellaEvokerEntity> bosses = mc.level.getEntitiesOfClass(
                StellaEvokerEntity.class, searchBox,
                boss -> boss.isAlive() && !boss.isManaSystemDisabled()
        );

        if (bosses.isEmpty()) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        for (AstralCrystalEntity crystal : crystals) {
            StellaEvokerEntity nearestBoss = null;
            double nearestDist = Double.MAX_VALUE;

            for (StellaEvokerEntity boss : bosses) {
                double dist = crystal.distanceTo(boss);
                if (dist < nearestDist && dist < BEAM_SEARCH_RADIUS) {
                    nearestDist = dist;
                    nearestBoss = boss;
                }
            }

            if (nearestBoss != null) {
                Vec3 start = crystal.position().add(0, 0.5, 0);
                Vec3 end = nearestBoss.position().add(0, 1.0, 0);

                drawBeam(poseStack, bufferSource, cameraPos, start, end);

                if (shouldSpawnParticles) {
                    spawnBeamParticles(crystal, nearestBoss);
                }
            }
        }

        // M-06修复：指定 RenderType 避免 flush 所有缓冲
        bufferSource.endBatch(RenderType.lines());
    }

    // 绘制从起点到终点的能量射线（优化版）
    // 1.21.1 中 VertexConsumer.vertex() 已更名为 addVertex()
    // RenderType.lines() 的顶点格式为 POSITION_COLOR_NORMAL
    // 新增：脉冲透明度效果（基于游戏时间正弦波动），增强能量流动感
    // 新增：核心+外晕双层绘制，增强射线体积感
    private static void drawBeam(PoseStack poseStack, MultiBufferSource bufferSource,
                                  Vec3 cameraPos, Vec3 start, Vec3 end) {
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();

        float dx = (float) (end.x - start.x);
        float dy = (float) (end.y - start.y);
        float dz = (float) (end.z - start.z);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        // 除零保护：起点终点重合时跳过绘制，避免法线除零崩溃
        if (length < 0.001f) {
            poseStack.popPose();
            return;
        }
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;

        // 脉冲透明度：基于游戏时间的正弦波动，营造能量脉动感
        // 周期约 1.5 秒（20 TPS * 1.5 = 30 tick 周期）
        Minecraft mc = Minecraft.getInstance();
        float pulsePhase = (mc.level != null ? mc.level.getGameTime() % 30 : 0) / 30.0f * (float) Math.PI * 2;
        float pulseAlpha = BEAM_ALPHA_BASE + (float) Math.sin(pulsePhase) * BEAM_ALPHA_PULSE;

        // 核心射线（较亮较细）
        consumer.addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
                .setColor(BEAM_RED, BEAM_GREEN, BEAM_BLUE, pulseAlpha)
                .setNormal(pose, nx, ny, nz);

        consumer.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
                .setColor(BEAM_RED, BEAM_GREEN, BEAM_BLUE, pulseAlpha)
                .setNormal(pose, nx, ny, nz);

        // 外晕射线（较暗较宽，通过额外顶点模拟）
        // 使用稍偏移的坐标和更低的透明度创建光晕效果
        float glowAlpha = pulseAlpha * 0.35f;
        float glowOffset = 0.015f;

        // 垂直于射线方向的偏移向量（简化计算：使用世界 Y 轴叉乘）
        float perpX = nz;
        float perpZ = -nx;
        float perpLen = (float) Math.sqrt(perpX * perpX + perpZ * perpZ);
        if (perpLen > 0.001f) {
            perpX /= perpLen;
            perpZ /= perpLen;
        }

        // 光晕左上
        consumer.addVertex(pose, (float) start.x + perpX * glowOffset, (float) start.y + glowOffset, (float) start.z + perpZ * glowOffset)
                .setColor(BEAM_RED * 0.6f, BEAM_GREEN * 0.7f, BEAM_BLUE * 0.9f, glowAlpha)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) end.x + perpX * glowOffset, (float) end.y + glowOffset, (float) end.z + perpZ * glowOffset)
                .setColor(BEAM_RED * 0.6f, BEAM_GREEN * 0.7f, BEAM_BLUE * 0.9f, glowAlpha)
                .setNormal(pose, nx, ny, nz);

        // 光晕右下
        consumer.addVertex(pose, (float) start.x - perpX * glowOffset, (float) start.y - glowOffset, (float) start.z - perpZ * glowOffset)
                .setColor(BEAM_RED * 0.6f, BEAM_GREEN * 0.7f, BEAM_BLUE * 0.9f, glowAlpha)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) end.x - perpX * glowOffset, (float) end.y - glowOffset, (float) end.z - perpZ * glowOffset)
                .setColor(BEAM_RED * 0.6f, BEAM_GREEN * 0.7f, BEAM_BLUE * 0.9f, glowAlpha)
                .setNormal(pose, nx, ny, nz);

        poseStack.popPose();
    }

    // 沿射线路径生成粒子效果
    // 使用原版 ParticleTypes.END_ROD 替代 Lodestone WorldParticleBuilder
    // 原因：WorldParticleBuilder 在客户端渲染事件中调用可能导致服务端类加载问题
    // END_ROD 粒子自带星空蓝光效，与射线主题一致
    private static void spawnBeamParticles(AstralCrystalEntity crystal, StellaEvokerEntity boss) {
        Vec3 start = crystal.position().add(0, 0.5, 0);
        Vec3 end = boss.position().add(0, 1.0, 0);
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        direction = direction.normalize();

        // 固定粒子数量，步进 = 总距离 / 粒子数，确保均匀分布
        int particleCount = 8;
        double step = distance / particleCount;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (int i = 1; i <= particleCount; i++) {
            double t = i * step;
            if (t > distance) break;

            double px = start.x + direction.x * t;
            double py = start.y + direction.y * t;
            double pz = start.z + direction.z * t;

            double offsetX = (crystal.getRandom().nextDouble() - 0.5) * 0.3;
            double offsetY = (crystal.getRandom().nextDouble() - 0.5) * 0.3;
            double offsetZ = (crystal.getRandom().nextDouble() - 0.5) * 0.3;

            // 使用原版 END_ROD 粒子替代 Lodestone WorldParticleBuilder
            mc.level.addParticle(ParticleTypes.END_ROD,
                    px + offsetX, py + offsetY, pz + offsetZ,
                    0, 0.02, 0);
        }
    }
}
