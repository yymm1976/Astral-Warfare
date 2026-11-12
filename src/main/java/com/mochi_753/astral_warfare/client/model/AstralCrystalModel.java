package com.mochi_753.astral_warfare.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mochi_753.astral_warfare.entity.AstralCrystalEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 星界水晶模型 - 从 Blockbench 通过 MCP 导出的双四棱锥水晶模型
 * 结构：上下对称的双四棱锥（底面重合），中心发光核心
 * 10个部件：base(底面), up1-3(上半部), top(尖顶), dn1-3(下半部), bot(尖底), glow(核心)
 */
public class AstralCrystalModel extends HierarchicalModel<AstralCrystalEntity> {

    /** 模型层位置，1.21.1 中 ModelLayerLocation 需要两个参数 */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("astral_warfare", "astral_crystal"),
        "main"
    );

    private final ModelPart root;
    private final ModelPart base;
    private final ModelPart up1;
    private final ModelPart up2;
    private final ModelPart up3;
    private final ModelPart top;
    private final ModelPart dn1;
    private final ModelPart dn2;
    private final ModelPart dn3;
    private final ModelPart bot;
    private final ModelPart glow;

    public AstralCrystalModel(ModelPart root) {
        this.root = root;
        this.base = root.getChild("base");
        this.up1 = root.getChild("up1");
        this.up2 = root.getChild("up2");
        this.up3 = root.getChild("up3");
        this.top = root.getChild("top");
        this.dn1 = root.getChild("dn1");
        this.dn2 = root.getChild("dn2");
        this.dn3 = root.getChild("dn3");
        this.bot = root.getChild("bot");
        this.glow = root.getChild("glow");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 底面（最宽层，两个棱锥共用底面）4×1×4
        root.addOrReplaceChild("base",
            CubeListBuilder.create().addBox(-2.0F, -0.5F, -2.0F, 4.0F, 1.0F, 4.0F),
            PartPose.ZERO);

        // 上半部第一层 3×1×3
        root.addOrReplaceChild("up1",
            CubeListBuilder.create().addBox(-1.5F, -1.5F, -1.5F, 3.0F, 1.0F, 3.0F),
            PartPose.ZERO);

        // 上半部第二层 2×1×2
        root.addOrReplaceChild("up2",
            CubeListBuilder.create().addBox(-1.0F, -2.5F, -1.0F, 2.0F, 1.0F, 2.0F),
            PartPose.ZERO);

        // 上半部第三层 1×1×1
        root.addOrReplaceChild("up3",
            CubeListBuilder.create().addBox(-0.5F, -3.5F, -0.5F, 1.0F, 1.0F, 1.0F),
            PartPose.ZERO);

        // 尖顶 0.5×1.25×0.5
        root.addOrReplaceChild("top",
            CubeListBuilder.create().addBox(-0.25F, -4.75F, -0.25F, 0.5F, 1.25F, 0.5F),
            PartPose.ZERO);

        // 下半部第一层 3×1×3
        root.addOrReplaceChild("dn1",
            CubeListBuilder.create().addBox(-1.5F, 0.5F, -1.5F, 3.0F, 1.0F, 3.0F),
            PartPose.ZERO);

        // 下半部第二层 2×1×2
        root.addOrReplaceChild("dn2",
            CubeListBuilder.create().addBox(-1.0F, 1.5F, -1.0F, 2.0F, 1.0F, 2.0F),
            PartPose.ZERO);

        // 下半部第三层 1×1×1
        root.addOrReplaceChild("dn3",
            CubeListBuilder.create().addBox(-0.5F, 2.5F, -0.5F, 1.0F, 1.0F, 1.0F),
            PartPose.ZERO);

        // 尖底 0.5×1.25×0.5
        root.addOrReplaceChild("bot",
            CubeListBuilder.create().addBox(-0.25F, 3.5F, -0.25F, 0.5F, 1.25F, 0.5F),
            PartPose.ZERO);

        // 发光核心 1×1×1
        root.addOrReplaceChild("glow",
            CubeListBuilder.create().addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F),
            PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    /**
     * 水晶动画：上下浮动 + 核心脉动
     * Blockbench 中创建的关键帧动画无法直接导出为 Java 代码，
     * 因此在 setupAnim 中手动实现浮动效果
     */
    @Override
    public void setupAnim(AstralCrystalEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // 整体上下浮动
        float floatOffset = Mth.sin(ageInTicks * 0.1F) * 0.15F;
        this.base.setPos(0, floatOffset, 0);
        this.up1.setPos(0, floatOffset, 0);
        this.up2.setPos(0, floatOffset, 0);
        this.up3.setPos(0, floatOffset, 0);
        this.top.setPos(0, floatOffset, 0);
        this.dn1.setPos(0, floatOffset, 0);
        this.dn2.setPos(0, floatOffset, 0);
        this.dn3.setPos(0, floatOffset, 0);
        this.bot.setPos(0, floatOffset, 0);

        // 核心更频繁的脉动
        float glowOffset = Mth.sin(ageInTicks * 0.15F) * 0.2F;
        this.glow.setPos(0, floatOffset + glowOffset, 0);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
