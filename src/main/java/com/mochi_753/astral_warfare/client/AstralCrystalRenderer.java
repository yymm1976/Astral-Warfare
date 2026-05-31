package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.client.model.AstralCrystalModel;
import com.mochi_753.astral_warfare.entity.AstralCrystalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 星界水晶实体渲染器
 * 使用从 Blockbench MCP 导出的双四棱锥水晶模型
 * 包含浮动效果、自转动画和发光渲染
 */
public class AstralCrystalRenderer extends EntityRenderer<AstralCrystalEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "textures/entity/astral_crystal.png");

    private final AstralCrystalModel model;

    public AstralCrystalRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new AstralCrystalModel(context.bakeLayer(AstralCrystalModel.LAYER));
    }

    @Override
    public ResourceLocation getTextureLocation(AstralCrystalEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(AstralCrystalEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (!entity.isCrystalAlive()) {
            return;
        }

        poseStack.pushPose();

        // 垂直浮动效果
        float floatOffset = Mth.sin(entity.tickCount * 0.2F + partialTick) * 0.15F + 0.5F;
        poseStack.translate(0.0, floatOffset + 0.25, 0.0);

        // 整体缓慢自转
        float rotation = entity.tickCount * 2.0F + partialTick;
        poseStack.mulPose(com.mojang.math.Axis.YP.rotation(rotation * 0.05F));

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucentCull(TEXTURE));

        // 使用发光光照值 15728880 产生自发光效果
        this.model.renderToBuffer(poseStack, vertexConsumer, 15728880, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
