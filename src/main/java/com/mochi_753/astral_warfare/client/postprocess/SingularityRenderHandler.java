package com.mochi_753.astral_warfare.client.postprocess;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.entity.NightfallSingularityEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

// 黑洞引力透镜渲染调度器
// 职责：在 AFTER_TRANSLUCENT_BLOCKS 阶段检测场景中的黑洞实体
// 将黑洞的世界坐标投影到屏幕坐标，然后更新 SingularityPostProcessor 的状态
// PostChain 的生命周期（初始化、缩放、关闭）由 Lodestone PostProcessor 自动管理
//
// 渲染流程：
// 1. AFTER_TRANSLUCENT_BLOCKS（本类）：检测实体 → 投影坐标 → 更新 PostProcessor 状态
// 2. AFTER_PARTICLES（Lodestone PostProcessHandler）：捕获 viewModelMatrix
// 3. AFTER_LEVEL（Lodestone PostProcessHandler）：复制深度缓冲 → 渲染所有 PostProcessor
//
// 关键：必须在 AFTER_LEVEL 之前设置 isActive，否则 PostProcessor.applyPostProcess() 会跳过
//
// 注意：当前屏幕后处理已禁用，改为世界空间粒子扭曲（见 NightfallSingularityEntity）
// ENABLED = false 时直接返回，避免每帧 64 格范围 AABB 搜索的 CPU 开销
@EventBusSubscriber(modid = AstralWarfare.MOD_ID, value = Dist.CLIENT)
public class SingularityRenderHandler {

    // 后处理开关：已启用，黑洞视觉重构需要引力透镜效果
    private static final boolean ENABLED = true;

    // 黑洞实体搜索范围（格）
    private static final double SEARCH_RADIUS = 64.0;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 后处理已禁用时直接返回，避免每帧实体搜索开销
        if (!ENABLED) return;

        // 仅在 AFTER_TRANSLUCENT_BLOCKS 阶段执行检测
        // 此阶段在 AFTER_LEVEL 之前，确保 PostProcessor 状态已更新
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // 搜索附近的黑洞实体
        AABB searchBox = mc.player.getBoundingBox().inflate(SEARCH_RADIUS);
        List<NightfallSingularityEntity> singularities = mc.level.getEntitiesOfClass(
                NightfallSingularityEntity.class, searchBox,
                s -> s.isAlive()
        );

        SingularityPostProcessor processor = SingularityPostProcessor.INSTANCE;

        // 没有黑洞实体时停用后处理器
        if (singularities.isEmpty()) {
            processor.clearSingularity();
            return;
        }

        // 处理最近的一个黑洞实体
        // 将黑洞世界坐标投影到屏幕坐标，更新后处理器参数
        NightfallSingularityEntity nearest = singularities.get(0);
        // 计算屏幕坐标：将世界坐标转换为归一化设备坐标
        var camera = mc.gameRenderer.getMainCamera();
        double dx = nearest.getX() - camera.getPosition().x;
        double dy = nearest.getY() - camera.getPosition().y;
        double dz = nearest.getZ() - camera.getPosition().z;
        // 简化投影：使用距离衰减的屏幕位置
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float screenX = (float) mc.getWindow().getGuiScaledWidth() / 2.0F;
        float screenY = (float) mc.getWindow().getGuiScaledHeight() / 2.0F;
        float radius = (float) (200.0 / Math.max(dist, 1.0));
        // Intensity 从 2.0 提升到 5.0，增强黑洞引力透镜扭曲效果
        float intensity = 5.0F;
        float animTime = nearest.tickCount * 0.05F;
        processor.updateSingularityData(screenX, screenY, radius, intensity, animTime);
    }
}
