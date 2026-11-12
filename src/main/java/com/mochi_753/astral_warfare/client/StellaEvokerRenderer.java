package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mochi_753.astral_warfare.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 星穹唤星者 GeckoLib 渲染器
 * 从 MobRenderer + HumanoidModel 迁移到 GeoEntityRenderer + GeoModel
 *
 * GeckoLib 接管骨骼动画驱动，不再需要手动 setupAnim() + lerp 插值
 * 紫色光环和虚空长戟作为 GeoRenderLayer 叠加渲染
 *
 * GeckoLib 4.7.3 的 GeoEntityRenderer 只有 1 个泛型参数 <T>，
 * 继承 EntityRenderer<T>，不涉及 RenderState（1.21.2+ 才引入）
 */
public class StellaEvokerRenderer extends GeoEntityRenderer<StellaEvokerEntity> {

    private static final ResourceLocation GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "textures/entity/void_glow.png");

    public StellaEvokerRenderer(EntityRendererProvider.Context context) {
        super(context, new StellaEvokerModel());
        this.addRenderLayer(new AstralGlowLayer(this));
        this.addRenderLayer(new VoidHalberdLayer(this, context));
    }

    // ==================== 紫色星空光环渲染层 ====================

    // 参考原版 ChargedCreeperLayer 机制
    // 使用 energySwirl 渲染类型，在实体表面覆盖流动的能量光环
    // 迁移自原 MobRenderer 的 RenderLayer，改为 GeoRenderLayer 适配 GeckoLib 管线
    private static class AstralGlowLayer extends GeoRenderLayer<StellaEvokerEntity> {

        public AstralGlowLayer(GeoEntityRenderer<StellaEvokerEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, StellaEvokerEntity entity, BakedGeoModel bakedModel,
                           RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            float dyingAlpha = 1.0F;
            if (entity.isDying()) {
                float progress = entity.getDyingFSM().getProgress();
                if (progress > 0.6F) {
                    dyingAlpha = 1.0F - (progress - 0.6F) / 0.4F;
                    dyingAlpha = Math.max(0, Math.min(1, dyingAlpha));
                }
            }

            float scrollSpeed = entity.getCombatPhase() == StellaEvokerEntity.PHASE_1_CASTER ? 0.02F : 0.008F;

            if (entity.isDying()) {
                scrollSpeed = 0.03F + (float) Math.sin(entity.tickCount * 0.3F) * 0.02F;
            }

            RenderType glowRenderType = RenderType.energySwirl(GLOW_TEXTURE,
                    entity.tickCount * scrollSpeed, entity.tickCount * scrollSpeed * 0.7F);

            VertexConsumer glowBuffer = bufferSource.getBuffer(glowRenderType);

            if (dyingAlpha < 1.0F) {
                float scale = 0.99F + dyingAlpha * 0.01F;
                poseStack.pushPose();
                poseStack.scale(scale, scale, scale);
            }

            // GeckoLib 渲染层：使用 actuallyRender 重新渲染模型，叠加能量光环效果
            // 参数：poseStack, entity, bakedModel, renderType, bufferSource, buffer,
            //       isReRender, partialTick, packedLight, packedOverlay, colorOverlay
            // 15728880 = 满亮度自发光，OverlayTexture.NO_OVERLAY = 无叠加色，-1 = 无颜色叠加
            this.getRenderer().actuallyRender(poseStack, entity, bakedModel, glowRenderType,
                    bufferSource, glowBuffer, false, partialTick, 15728880, OverlayTexture.NO_OVERLAY, -1);

            if (dyingAlpha < 1.0F) {
                poseStack.popPose();
            }
        }
    }

    // ==================== 虚空长戟手持渲染层 ====================

    // 二阶段时，在 BOSS 右手渲染虚空长戟
    // 迁移自原 MobRenderer 的 RenderLayer，改为 GeoRenderLayer
    private static class VoidHalberdLayer extends GeoRenderLayer<StellaEvokerEntity> {

        private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;
        // 【L17修复】缓存 ItemStack 为字段，避免每帧 new ItemStack() 造成 GC 压力
        private final ItemStack halberdStack;

        public VoidHalberdLayer(GeoEntityRenderer<StellaEvokerEntity> renderer,
                                EntityRendererProvider.Context context) {
            super(renderer);
            this.itemRenderer = context.getItemRenderer();
            this.halberdStack = new ItemStack(ModItems.VOID_HALBERD.get());
        }

        @Override
        public void render(PoseStack poseStack, StellaEvokerEntity entity, BakedGeoModel bakedModel,
                           RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            if (entity.getCombatPhase() != StellaEvokerEntity.PHASE_2_MELEE) return;
            if (entity.isDying()) return;

            poseStack.pushPose();

            // 定位到右手骨骼位置
            // 使用与原渲染器相同的变换参数
            poseStack.translate(0.35, 1.1, -0.4);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-45.0F));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
            poseStack.scale(0.9F, 0.9F, 0.9F);

            this.itemRenderer.renderStatic(entity, this.halberdStack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    false, poseStack, bufferSource, entity.level(), 15728880,
                    OverlayTexture.NO_OVERLAY,
                    entity.getId() + 1);

            poseStack.popPose();
        }
    }
}
