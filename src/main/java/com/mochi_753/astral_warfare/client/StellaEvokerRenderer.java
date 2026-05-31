package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mochi_753.astral_warfare.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 星穹唤星者专属渲染器
 * 使用 HumanoidModel（标准人形模型）+ 自定义史蒂夫格式皮肤
 *
 * 原先继承 IllagerRenderer 使用 IllagerModel，现改为 MobRenderer + HumanoidModel
 * 原因：BOSS 使用自定义史蒂夫格式皮肤，HumanoidModel 完美兼容该格式
 *
 * 手臂姿态通过自定义 StellaEvokerModel（HumanoidModel 子类）驱动：
 *   - CROSSED: 双手交叉（待机）
 *   - SPELLCASTING: 施法举手（一阶段施法时）
 *   - ATTACKING: 持武器攻击（二阶段近战）
 *
 * 自定义渲染层：
 *   1. AstralGlowLayer - 紫色虚空光环
 *   2. VoidHalberdLayer - 二阶段手持虚空长戟
 */
public class StellaEvokerRenderer extends MobRenderer<StellaEvokerEntity, StellaEvokerRenderer.StellaEvokerModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "textures/entity/stella_evoker.png");

    private static final ResourceLocation GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "textures/entity/void_glow.png");

    public StellaEvokerRenderer(EntityRendererProvider.Context context) {
        super(context, new StellaEvokerModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new AstralGlowLayer(this));
        this.addLayer(new VoidHalberdLayer(this, context));
    }

    @Override
    public ResourceLocation getTextureLocation(StellaEvokerEntity entity) {
        return TEXTURE;
    }

    /**
     * StellaEvoker 专用 HumanoidModel 子类
     * 重写 setupAnim 以手动映射 IllagerArmPose 到手臂旋转
     * HumanoidModel 不原生支持 IllagerArmPose，需在此手动处理
     */
    public static class StellaEvokerModel extends HumanoidModel<StellaEvokerEntity> {

        public StellaEvokerModel(net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(StellaEvokerEntity entity, float limbSwing, float limbSwingAmount,
                               float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            var armPose = entity.getArmPose();

            switch (armPose) {
                case SPELLCASTING:
                    this.rightArm.xRot = -2.0F;
                    this.rightArm.yRot = -0.3F;
                    this.leftArm.xRot = -2.0F;
                    this.leftArm.yRot = 0.3F;
                    break;
                case ATTACKING:
                    this.rightArm.xRot = -1.5F;
                    this.rightArm.yRot = 0.0F;
                    this.leftArm.xRot = -0.5F;
                    this.leftArm.yRot = 0.5F;
                    break;
                case CROSSED:
                    this.rightArm.xRot = -0.5F;
                    this.rightArm.yRot = 0.4F;
                    this.leftArm.xRot = -0.5F;
                    this.leftArm.yRot = -0.4F;
                    break;
                default:
                    break;
            }

            if (entity.isDying()) {
                this.rightArm.xRot = -1.2F;
                this.rightArm.yRot = 0.3F;
                this.leftArm.xRot = -1.2F;
                this.leftArm.yRot = -0.3F;
                this.head.xRot = 0.5F;
                this.body.xRot = 0.3F;
            }
        }
    }

    /**
     * 紫色星空光环渲染层
     * 参考闪电苦力怕的 ChargedCreeperLayer 机制
     * 使用 energySwirl 渲染类型，在实体表面覆盖流动的能量光环
     */
    private static class AstralGlowLayer extends RenderLayer<StellaEvokerEntity, StellaEvokerModel> {

        public AstralGlowLayer(StellaEvokerRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                           StellaEvokerEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
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

            VertexConsumer consumer = bufferSource.getBuffer(RenderType.energySwirl(GLOW_TEXTURE,
                    entity.tickCount * scrollSpeed, entity.tickCount * scrollSpeed * 0.7F));

            if (dyingAlpha < 1.0F) {
                float scale = 0.99F + dyingAlpha * 0.01F;
                poseStack.pushPose();
                poseStack.scale(scale, scale, scale);
            }

            this.getParentModel().renderToBuffer(poseStack, consumer, 15728880,
                    OverlayTexture.NO_OVERLAY);

            if (dyingAlpha < 1.0F) {
                poseStack.popPose();
            }
        }
    }

    /**
     * 虚空长戟手持渲染层
     * 二阶段时，在 BOSS 右手渲染虚空长戟
     */
    private static class VoidHalberdLayer extends RenderLayer<StellaEvokerEntity, StellaEvokerModel> {

        private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;

        public VoidHalberdLayer(StellaEvokerRenderer renderer, EntityRendererProvider.Context context) {
            super(renderer);
            this.itemRenderer = context.getItemRenderer();
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                           StellaEvokerEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.getCombatPhase() != StellaEvokerEntity.PHASE_2_MELEE) {
                return;
            }
            if (entity.isDying()) {
                return;
            }

            poseStack.pushPose();

            poseStack.translate(0.35, 1.1, -0.4);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-45.0F));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
            poseStack.scale(0.9F, 0.9F, 0.9F);

            ItemStack halberd = new ItemStack(ModItems.VOID_HALBERD.get());

            this.itemRenderer.renderStatic(entity, halberd, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    false, poseStack, bufferSource, entity.level(), 15728880,
                    OverlayTexture.NO_OVERLAY,
                    entity.getId() + 1);

            poseStack.popPose();
        }
    }
}
