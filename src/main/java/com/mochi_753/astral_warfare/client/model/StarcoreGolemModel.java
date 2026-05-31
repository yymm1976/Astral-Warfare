package com.mochi_753.astral_warfare.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mochi_753.astral_warfare.entity.StarcoreGolemEntity;
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
 * 星核傀儡的自定义模型类
 * 基于 Blockbench 通过 MCP 创建的模型数据生成
 * 模型特征：魁梧双足人形，带有发光核心和肩部护甲
 */
public class StarcoreGolemModel extends HierarchicalModel<StarcoreGolemEntity> {

    /** 模型层位置，用于客户端注册。1.21.1 中 ModelLayerLocation 需要两个参数：主路径和层名称 */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("astral_warfare", "starcore_golem"),
        "main"
    );

    private final ModelPart root;
    private final ModelPart head;
    @SuppressWarnings("unused")
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightShoulder;
    private final ModelPart leftShoulder;
    private final ModelPart coreGlow;
    private final ModelPart neck;
    private final ModelPart headTop;
    private final ModelPart rightEye;
    private final ModelPart leftEye;
    private final ModelPart mouth;
    private final ModelPart rightEar;
    private final ModelPart leftEar;
    private final ModelPart rightHand;
    private final ModelPart leftHand;
    private final ModelPart rightFoot;
    private final ModelPart leftFoot;

    public StarcoreGolemModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.rightShoulder = root.getChild("right_shoulder");
        this.leftShoulder = root.getChild("left_shoulder");
        this.coreGlow = root.getChild("core_glow");
        this.neck = root.getChild("neck");
        this.headTop = root.getChild("head_top");
        this.rightEye = root.getChild("right_eye");
        this.leftEye = root.getChild("left_eye");
        this.mouth = root.getChild("mouth");
        this.rightEar = root.getChild("right_ear");
        this.leftEar = root.getChild("left_ear");
        this.rightHand = root.getChild("right_hand");
        this.leftHand = root.getChild("left_hand");
        this.rightFoot = root.getChild("right_foot");
        this.leftFoot = root.getChild("left_foot");
    }

    /**
     * 创建层定义，用于注册模型层
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("head",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -32.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("body",
            CubeListBuilder.create().texOffs(0, 16)
                .addBox(-4.0F, -24.0F, -2.0F, 8.0F, 12.0F, 4.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("right_arm",
            CubeListBuilder.create().texOffs(24, 0)
                .addBox(-8.0F, -24.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("left_arm",
            CubeListBuilder.create().texOffs(32, 0)
                .addBox(4.0F, -24.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("right_leg",
            CubeListBuilder.create().texOffs(0, 32)
                .addBox(-4.0F, -12.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("left_leg",
            CubeListBuilder.create().texOffs(8, 32)
                .addBox(0.0F, -12.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("right_shoulder",
            CubeListBuilder.create().texOffs(16, 32)
                .addBox(-9.0F, -26.0F, -3.0F, 5.0F, 4.0F, 6.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("left_shoulder",
            CubeListBuilder.create().texOffs(24, 32)
                .addBox(4.0F, -26.0F, -3.0F, 5.0F, 4.0F, 6.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("chest_core",
            CubeListBuilder.create().texOffs(32, 16)
                .addBox(-3.0F, -22.0F, -3.0F, 6.0F, 6.0F, 6.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("chest_plate",
            CubeListBuilder.create().texOffs(0, 48)
                .addBox(-5.0F, -20.0F, -3.0F, 10.0F, 6.0F, 2.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("waist",
            CubeListBuilder.create().texOffs(16, 48)
                .addBox(-4.0F, -13.0F, -2.0F, 8.0F, 3.0F, 4.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("right_hand",
            CubeListBuilder.create().texOffs(40, 0)
                .addBox(-8.0F, -12.0F, -2.0F, 4.0F, 8.0F, 4.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("left_hand",
            CubeListBuilder.create().texOffs(48, 0)
                .addBox(4.0F, -12.0F, -2.0F, 4.0F, 8.0F, 4.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("right_foot",
            CubeListBuilder.create().texOffs(0, 56)
                .addBox(-4.0F, -3.0F, -3.0F, 4.0F, 3.0F, 6.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("left_foot",
            CubeListBuilder.create().texOffs(12, 56)
                .addBox(0.0F, -3.0F, -3.0F, 4.0F, 3.0F, 6.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("neck",
            CubeListBuilder.create().texOffs(24, 48)
                .addBox(-2.0F, -25.0F, -2.0F, 4.0F, 2.0F, 4.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("head_top",
            CubeListBuilder.create().texOffs(32, 48)
                .addBox(-3.0F, -34.0F, -3.0F, 6.0F, 2.0F, 6.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("right_eye",
            CubeListBuilder.create().texOffs(40, 48)
                .addBox(-3.0F, -29.0F, -5.0F, 2.0F, 3.0F, 1.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("left_eye",
            CubeListBuilder.create().texOffs(44, 48)
                .addBox(1.0F, -29.0F, -5.0F, 2.0F, 3.0F, 1.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("mouth",
            CubeListBuilder.create().texOffs(48, 48)
                .addBox(-2.0F, -26.0F, -5.0F, 4.0F, 2.0F, 1.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("core_glow",
            CubeListBuilder.create().texOffs(0, 32)
                .addBox(-1.0F, -21.0F, -4.0F, 2.0F, 4.0F, 8.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("right_ear",
            CubeListBuilder.create().texOffs(56, 48)
                .addBox(-5.0F, -30.0F, -2.0F, 1.0F, 3.0F, 3.0F),
            PartPose.ZERO);

        root.addOrReplaceChild("left_ear",
            CubeListBuilder.create().texOffs(56, 54)
                .addBox(4.0F, -30.0F, -2.0F, 1.0F, 3.0F, 3.0F),
            PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    /**
     * 设置模型的动画状态
     * 包括头部跟随、行走摆动、充能脉冲等效果
     */
    @Override
    public void setupAnim(StarcoreGolemEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * ((float)Math.PI / 180F);
        this.head.xRot = headPitch * ((float)Math.PI / 180F);

        this.neck.yRot = this.head.yRot;
        this.neck.xRot = this.head.xRot;
        this.headTop.yRot = this.head.yRot;
        this.headTop.xRot = this.head.xRot;
        this.rightEye.yRot = this.head.yRot;
        this.rightEye.xRot = this.head.xRot;
        this.leftEye.yRot = this.head.yRot;
        this.leftEye.xRot = this.head.xRot;
        this.mouth.yRot = this.head.yRot;
        this.mouth.xRot = this.head.xRot;
        this.rightEar.yRot = this.head.yRot;
        this.leftEar.yRot = this.head.yRot;

        float walkSpeed = 0.6F;
        this.rightArm.xRot = Mth.cos(limbSwing * 0.6662F) * limbSwingAmount * walkSpeed;
        this.leftArm.xRot = Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * limbSwingAmount * walkSpeed;
        this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * limbSwingAmount * walkSpeed;
        this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F) * limbSwingAmount * walkSpeed;
        this.rightHand.xRot = this.rightArm.xRot;
        this.leftHand.xRot = this.leftArm.xRot;
        this.rightFoot.xRot = this.rightLeg.xRot;
        this.leftFoot.xRot = this.leftLeg.xRot;

        // 充能核心脉冲动画：使用 isCharged() 判断充能状态
        if (entity.isCharged()) {
            float pulse = Mth.sin(ageInTicks * 0.15F) * 0.15F;
            this.coreGlow.xRot = pulse;
            this.coreGlow.yRot = pulse;
        } else {
            this.coreGlow.xRot = 0.0F;
            this.coreGlow.yRot = 0.0F;
        }

        this.rightShoulder.yRot = Mth.cos(limbSwing * 0.6662F) * limbSwingAmount * 0.1F;
        this.leftShoulder.yRot = Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * limbSwingAmount * 0.1F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
