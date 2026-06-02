package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.entity.VoidFissureEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

// 虚空裂隙实体渲染器 — 空实现
// VoidFissureEntity 是不可见的 Misc 实体，视觉效果完全由服务端粒子驱动
// 缺少渲染器会导致 EntityRendererManager 查找返回 null，触发 NullPointerException 崩溃
public class VoidFissureRenderer extends EntityRenderer<VoidFissureEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public VoidFissureRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidFissureEntity entity) {
        return TEXTURE;
    }
}
