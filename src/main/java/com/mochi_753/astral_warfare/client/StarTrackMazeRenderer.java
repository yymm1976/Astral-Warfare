package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.network.ClientboundMazeSyncPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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

import java.awt.Color;

// 星轨迷宫客户端渲染器
// 在地面绘制网格线 + 激活列高亮 + 交点发光粒子
// 纯客户端类：通过 GAME_BUS + CLIENT 环境注册
//
// 渲染流程：
//   1. 接收 ClientboundMazeSyncPacket 同步的 activeGroup 和 center
//   2. 在 AFTER_TRANSLUCENT_BLOCKS 阶段绘制网格线
//   3. 激活列用亮蓝色粗线，非激活列用暗色细线
//   4. 交点处用 Lodestone SPARKLE_PARTICLE 粒子发光
@EventBusSubscriber(modid = AstralWarfare.MOD_ID, value = Dist.CLIENT)
public class StarTrackMazeRenderer {

    private static final double CELL_SIZE = 2.0;
    private static final float ACTIVE_R = 0.3F;
    private static final float ACTIVE_G = 0.6F;
    private static final float ACTIVE_B = 1.0F;
    private static final float INACTIVE_R = 0.15F;
    private static final float INACTIVE_G = 0.1F;
    private static final float INACTIVE_B = 0.25F;
    private static final double Y_OFFSET = 0.03;
    private static int particleTickCounter = 0;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        ClientboundMazeSyncPacket.MazeData mazeData = ClientboundMazeSyncPacket.getLastMazeData();
        if (mazeData == null) return;

        if (System.currentTimeMillis() - mazeData.timestamp() > 6000) {
            ClientboundMazeSyncPacket.clearMazeData();
            return;
        }

        double distSq = mc.player.distanceToSqr(mazeData.cx(), mazeData.cy(), mazeData.cz());
        if (distSq > 64.0 * 64.0) return;

        particleTickCounter++;
        boolean shouldSpawnParticles = (particleTickCounter % 3 == 0);

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        drawMaze(poseStack, bufferSource, cameraPos, mazeData, shouldSpawnParticles, mc);

        bufferSource.endBatch();
    }

    private static void drawMaze(PoseStack poseStack, MultiBufferSource bufferSource,
                                  Vec3 cameraPos, ClientboundMazeSyncPacket.MazeData mazeData,
                                  boolean shouldSpawnParticles, Minecraft mc) {
        double cx = mazeData.cx() - cameraPos.x;
        double cy = mazeData.cy() - cameraPos.y + Y_OFFSET;
        double cz = mazeData.cz() - cameraPos.z;
        int activeGroup = mazeData.activeGroup();
        int gridSize = mazeData.gridSize();
        int halfGrid = gridSize / 2;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();

        // 绘制纵向线（沿 Z 轴）
        for (int col = -halfGrid; col <= halfGrid; col++) {
            boolean isActive = ((col + halfGrid) % 2 == activeGroup);
            float r = isActive ? ACTIVE_R : INACTIVE_R;
            float g = isActive ? ACTIVE_G : INACTIVE_G;
            float b = isActive ? ACTIVE_B : INACTIVE_B;
            float alpha = isActive ? 0.9F : 0.3F;

            float x = (float)(cx + col * CELL_SIZE);
            float zStart = (float)(cz - halfGrid * CELL_SIZE);
            float zEnd = (float)(cz + halfGrid * CELL_SIZE);

            // 1.21.1 使用 addVertex + setColor + setNormal
            consumer.addVertex(pose, x, (float)cy, zStart)
                    .setColor(r, g, b, alpha)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x, (float)cy, zEnd)
                    .setColor(r, g, b, alpha)
                    .setNormal(pose, 0, 1, 0);
        }

        // 绘制横向线（沿 X 轴）
        for (int row = -halfGrid; row <= halfGrid; row++) {
            float z = (float)(cz + row * CELL_SIZE);
            float xStart = (float)(cx - halfGrid * CELL_SIZE);
            float xEnd = (float)(cx + halfGrid * CELL_SIZE);

            consumer.addVertex(pose, xStart, (float)cy, z)
                    .setColor(INACTIVE_R, INACTIVE_G, INACTIVE_B, 0.3F)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, xEnd, (float)cy, z)
                    .setColor(INACTIVE_R, INACTIVE_G, INACTIVE_B, 0.3F)
                    .setNormal(pose, 0, 1, 0);
        }

        // 激活列交点处生成 Lodestone SPARKLE_PARTICLE 粒子
        if (shouldSpawnParticles && mc.level != null) {
            Color activeColor = new Color(ACTIVE_R, ACTIVE_G, ACTIVE_B);
            for (int col = -halfGrid; col <= halfGrid; col++) {
                boolean isActive = ((col + halfGrid) % 2 == activeGroup);
                if (!isActive) continue;

                double worldX = mazeData.cx() + col * CELL_SIZE;
                for (int row = -halfGrid; row <= halfGrid; row++) {
                    double worldZ = mazeData.cz() + row * CELL_SIZE;
                    double worldY = mazeData.cy() + 0.1;

                    if (mc.level.random.nextFloat() < 0.3F) {
                        WorldParticleBuilder.create(LodestoneParticleTypes.SPARKLE_PARTICLE)
                                .setScaleData(GenericParticleData.create(0.3F, 0.0F).setEasing(Easing.QUAD_IN_OUT).build())
                                .setTransparencyData(GenericParticleData.create(0.8F, 0.0F).setEasing(Easing.QUAD_IN_OUT).build())
                                .setColorData(ColorParticleData.create(activeColor, activeColor).build())
                                .setLifetime(15 + mc.level.random.nextInt(10))
                                .setMotion(0, 0.02, 0)
                                .spawn(mc.level, worldX, worldY, worldZ);
                    }
                }
            }
        }
    }
}
