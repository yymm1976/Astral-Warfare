package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.init.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import team.lodestar.lodestone.registry.common.particle.LodestoneParticleTypes;
import team.lodestar.lodestone.systems.easing.Easing;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;
import team.lodestar.lodestone.systems.particle.data.spin.SpinParticleData;

import java.awt.Color;
import java.util.Objects;

// 虚空禁锢脚下星阵纹路渲染器
// 在被禁锢的玩家脚下渲染旋转发光的暗紫色虚空星阵
// 纯客户端类：通过 GAME_BUS + CLIENT 环境注册
//
// 视觉设计：
//   - 双层旋转六芒星（内外环反向旋转）
//   - 暗紫色核心 + 边缘渐变透明
//   - 脉动发光效果（亮度随时间正弦波动）
//   - 地面投影（Y 轴偏移 +0.02 防止 Z-fighting）
//   - 旋转的虚空火花粒子（限流：每 2 帧生成一次，避免渲染帧率过高导致粒子过多）
//
// 实现原理：
//   使用 RenderType.lines() 绘制线段组成的六芒星图案
//   通过 PoseStack 旋转实现动画效果
//   不使用纹理贴图，纯几何线条渲染
//   Lodestone 粒子仅在 tick 计数器匹配时生成（而非每帧），确保粒子密度与 tick 率对齐
@EventBusSubscriber(modid = AstralWarfare.MOD_ID, value = Dist.CLIENT)
public class VoidSigilRenderer {

    // 星阵颜色：暗紫色
    private static final float SIGIL_R = 0.35F;
    private static final float SIGIL_G = 0.0F;
    private static final float SIGIL_B = 0.55F;
    // 星阵基础半径
    private static final float INNER_RADIUS = 0.6F;
    private static final float OUTER_RADIUS = 1.2F;
    // 星阵 Y 轴偏移（防止 Z-fighting）
    private static final float Y_OFFSET = 0.02F;

    // M-04修复：缓存 Color 实例，避免渲染热路径中每帧分配新对象
    private static final Color INNER_PARTICLE_START = new Color(80, 10, 120);
    private static final Color INNER_PARTICLE_END = new Color(160, 50, 200);
    private static final Color OUTER_PARTICLE_START = new Color(160, 60, 240);
    private static final Color OUTER_PARTICLE_END = new Color(230, 160, 255);
    private static final Color CENTER_PARTICLE_START = new Color(120, 0, 200);
    private static final Color CENTER_PARTICLE_END = new Color(200, 80, 255);

    // 粒子生成限流：每 2 帧生成一次粒子（约等于每 tick 一次）
    // 渲染帧率（60-240 FPS）远高于 tick 率（20 TPS），必须限流避免粒子过多
    private static int particleTickCounter = 0;
    // 上一次渲染时的维度资源键，用于检测维度切换并重置计数器
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastDimension = null;

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
        if (!Objects.equals(lastDimension, currentDim)) {
            particleTickCounter = 0;
        }
        lastDimension = currentDim;

        // 粒子限流：每 2 帧生成一次粒子（约等于每 tick 一次）
        // 渲染帧率（60-240 FPS）远高于 tick 率（20 TPS），必须限流避免粒子过多
        particleTickCounter++;
        boolean shouldSpawnParticles = (particleTickCounter % 2 == 0);

        // 搜索附近被虚空禁锢的实体
        net.minecraft.world.phys.AABB searchBox = mc.player.getBoundingBox().inflate(32.0);
        java.util.List<LivingEntity> trappedEntities = mc.level.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                entity -> entity.hasEffect(ModEffects.VOID_ENTRAPMENT)
        );

        if (trappedEntities.isEmpty()) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        for (LivingEntity entity : trappedEntities) {
            drawVoidSigil(poseStack, bufferSource, cameraPos, entity, partialTick, shouldSpawnParticles);
        }

        // M-06修复：指定 RenderType 避免 flush 所有缓冲
        bufferSource.endBatch(RenderType.lines());
    }

    // 在实体脚下绘制虚空星阵
    // shouldSpawnParticles：由渲染事件控制，每 2 帧为 true，避免渲染帧率过高导致粒子过多
    private static void drawVoidSigil(PoseStack poseStack, MultiBufferSource bufferSource,
                                       Vec3 cameraPos, LivingEntity entity, float partialTick, boolean shouldSpawnParticles) {
        double entityX = entity.getX() - cameraPos.x;
        double entityY = entity.getY() - cameraPos.y + Y_OFFSET;
        double entityZ = entity.getZ() - cameraPos.z;

        // 脉动亮度：正弦波动
        float time = (entity.tickCount + partialTick) * 0.05F;
        float pulse = 0.6F + 0.4F * (float) Math.sin(time * 2.0F);
        float alpha = pulse * 0.8F;

        poseStack.pushPose();
        poseStack.translate(entityX, entityY, entityZ);

        // 内环：顺时针旋转
        float innerRotation = time * 30.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(innerRotation));
        drawHexagram(poseStack, bufferSource, INNER_RADIUS, alpha, 1.0F);

        poseStack.popPose();

        // 外环：逆时针旋转
        poseStack.pushPose();
        poseStack.translate(entityX, entityY, entityZ);

        float outerRotation = -time * 20.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(outerRotation));
        drawHexagram(poseStack, bufferSource, OUTER_RADIUS, alpha * 0.6F, 0.7F);

        poseStack.popPose();

        // 外圈圆环
        poseStack.pushPose();
        poseStack.translate(entityX, entityY, entityZ);
        drawCircle(poseStack, bufferSource, OUTER_RADIUS * 1.1F, alpha * 0.4F);
        poseStack.popPose();

        // 使用 Lodestone 粒子增强禁锢脚下视觉效果
        // 在星阵周围生成旋转的虚空火花粒子
        // 关键限流：仅在 shouldSpawnParticles 为 true 时生成（每 2 帧一次）
        // 渲染帧率（60-240 FPS）远高于 tick 率（20 TPS），必须限流避免粒子过多
        if (shouldSpawnParticles && entity.level().isClientSide) {
            net.minecraft.client.multiplayer.ClientLevel clientLevel =
                    (net.minecraft.client.multiplayer.ClientLevel) entity.level();
            float particleTime = (entity.tickCount + partialTick) * 0.05F;

            // 内环粒子：4个旋转的虚空火花
            for (int i = 0; i < 4; i++) {
                double angle = particleTime * 3.0 + i * Math.PI * 0.5;
                double px = entity.getX() + Math.cos(angle) * INNER_RADIUS;
                double pz = entity.getZ() + Math.sin(angle) * INNER_RADIUS;
                WorldParticleBuilder.create(LodestoneParticleTypes.SPARKLE_PARTICLE)
                    .setColorData(ColorParticleData.create(
                        INNER_PARTICLE_START, INNER_PARTICLE_END).build())
                    .setScaleData(GenericParticleData.create(
                        0.4f, 0.1f).setEasing(Easing.QUINTIC_OUT).build())
                    .setTransparencyData(GenericParticleData.create(
                        0.9f, 0f).setEasing(Easing.CUBIC_OUT).build())
                    .setSpinData(SpinParticleData.create(
                        0.3f, 0f).setEasing(Easing.QUINTIC_OUT).build())
                    .setLifetime(15)
                    .spawn(clientLevel, px, entity.getY() + 0.1, pz);
            }

            // 外环粒子：6个旋转的星穹微光
            for (int i = 0; i < 6; i++) {
                double angle = -particleTime * 2.0 + i * Math.PI / 3.0;
                double px = entity.getX() + Math.cos(angle) * OUTER_RADIUS;
                double pz = entity.getZ() + Math.sin(angle) * OUTER_RADIUS;
                WorldParticleBuilder.create(LodestoneParticleTypes.WISP_PARTICLE)
                    .setColorData(ColorParticleData.create(
                        OUTER_PARTICLE_START, OUTER_PARTICLE_END).build())
                    .setScaleData(GenericParticleData.create(
                        0.3f, 0.05f).setEasing(Easing.QUINTIC_OUT).build())
                    .setTransparencyData(GenericParticleData.create(
                        0.7f, 0f).setEasing(Easing.CUBIC_OUT).build())
                    .setLifetime(12)
                    .spawn(clientLevel, px, entity.getY() + 0.15, pz);
            }

            // 中心上升粒子：2个向上飘的虚空火花
            for (int i = 0; i < 2; i++) {
                double ox = (entity.getRandom().nextDouble() - 0.5) * 0.4;
                double oz = (entity.getRandom().nextDouble() - 0.5) * 0.4;
                WorldParticleBuilder.create(LodestoneParticleTypes.SPARKLE_PARTICLE)
                    .setColorData(ColorParticleData.create(
                        CENTER_PARTICLE_START, CENTER_PARTICLE_END).build())
                    .setScaleData(GenericParticleData.create(
                        0.25f, 0f).setEasing(Easing.QUINTIC_OUT).build())
                    .setTransparencyData(GenericParticleData.create(
                        0.8f, 0f).setEasing(Easing.CUBIC_OUT).build())
                    .setLifetime(20)
                    .addMotion(0, 0.03, 0)
                    .spawn(clientLevel, entity.getX() + ox, entity.getY() + 0.05, entity.getZ() + oz);
            }
        }
    }

    // 绘制六芒星（两个叠加的等边三角形）
    private static void drawHexagram(PoseStack poseStack, MultiBufferSource bufferSource,
                                      float radius, float alpha, float brightness) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();

        float r = SIGIL_R * brightness + (1 - brightness) * 0.1F;
        float g = SIGIL_G * brightness;
        float b = SIGIL_B * brightness + (1 - brightness) * 0.2F;

        // 第一个三角形（0°, 120°, 240°）
        drawTriangle(consumer, pose, radius, alpha, r, g, b, 0.0F);

        // 第二个三角形（60°, 180°, 300°）— 倒三角
        drawTriangle(consumer, pose, radius, alpha, r, g, b, 60.0F);
    }

    // 绘制一个等边三角形
    // M-05修复：内联顶点计算，避免每次调用分配 float[3][3] 数组
    private static void drawTriangle(VertexConsumer consumer, PoseStack.Pose pose,
                                      float radius, float alpha, float r, float g, float b,
                                      float rotationOffset) {
        // 预计算三个顶点的 x/z 坐标，无需堆分配
        float x0 = (float) Math.cos(Math.toRadians(rotationOffset)) * radius;
        float z0 = (float) Math.sin(Math.toRadians(rotationOffset)) * radius;
        float x1 = (float) Math.cos(Math.toRadians(rotationOffset + 120.0F)) * radius;
        float z1 = (float) Math.sin(Math.toRadians(rotationOffset + 120.0F)) * radius;
        float x2 = (float) Math.cos(Math.toRadians(rotationOffset + 240.0F)) * radius;
        float z2 = (float) Math.sin(Math.toRadians(rotationOffset + 240.0F)) * radius;

        // 边 0→1
        consumer.addVertex(pose, x0, 0, z0).setColor(r, g, b, alpha).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, x1, 0, z1).setColor(r, g, b, alpha).setNormal(pose, 0, 1, 0);
        // 边 1→2
        consumer.addVertex(pose, x1, 0, z1).setColor(r, g, b, alpha).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, x2, 0, z2).setColor(r, g, b, alpha).setNormal(pose, 0, 1, 0);
        // 边 2→0
        consumer.addVertex(pose, x2, 0, z2).setColor(r, g, b, alpha).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, x0, 0, z0).setColor(r, g, b, alpha).setNormal(pose, 0, 1, 0);
    }

    // 绘制圆环（多边形近似）
    private static void drawCircle(PoseStack poseStack, MultiBufferSource bufferSource,
                                    float radius, float alpha) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();

        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double angle1 = (i / (double) segments) * Math.PI * 2;
            double angle2 = ((i + 1) / (double) segments) * Math.PI * 2;

            float x1 = (float) Math.cos(angle1) * radius;
            float z1 = (float) Math.sin(angle1) * radius;
            float x2 = (float) Math.cos(angle2) * radius;
            float z2 = (float) Math.sin(angle2) * radius;

            consumer.addVertex(pose, x1, 0, z1)
                    .setColor(SIGIL_R, SIGIL_G, SIGIL_B, alpha)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 0, z2)
                    .setColor(SIGIL_R, SIGIL_G, SIGIL_B, alpha)
                    .setNormal(pose, 0, 1, 0);
        }
    }
}
