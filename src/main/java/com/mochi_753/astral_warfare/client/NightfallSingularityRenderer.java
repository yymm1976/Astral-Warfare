package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.entity.NightfallSingularityEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

// 夜幕黑洞实体渲染器 — 空实现
// NightfallSingularityEntity 是不可见的 Misc 实体，视觉效果完全由服务端粒子驱动
// 但 Minecraft 渲染系统要求每个实体类型必须有对应的 EntityRenderer
// 缺少渲染器会导致 EntityRendererManager 查找返回 null，触发 NullPointerException 崩溃
// 此渲染器不绘制任何内容，仅满足渲染系统的非空约束
public class NightfallSingularityRenderer extends EntityRenderer<NightfallSingularityEntity> {

    // 不可见实体使用默认纹理位置（不会被实际采样）
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public NightfallSingularityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(NightfallSingularityEntity entity) {
        return TEXTURE;
    }
}
