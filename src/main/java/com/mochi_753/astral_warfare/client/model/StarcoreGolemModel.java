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
 *
 * 【坐标系说明】
 * Minecraft 模型坐标系：Y 向下为正，原点在实体脚底
 * PartPose.offset(x, y, z) 设置部件的旋转轴心（相对 root）
 * addBox 的坐标是相对于轴心点的
 *
 * 之前所有 PartPose.offset 的 Y 值都是负数，导致模型渲染在碰撞箱上方
 * 修复：将所有 Y 值加上 24，使模型对齐到正确的位置
 */
public class StarcoreGolemModel extends HierarchicalModel<StarcoreGolemEntity> {

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

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 身体：轴心在臀部 Y=12（原版 HumanoidModel 的标准位置）
        root.addOrReplaceChild("body",
            CubeListBuilder.create().texOffs(0, 16)
                .addBox(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F),
            PartPose.offset(0.0F, 12.0F, 0.0F));

        // 头部：轴心在脖子 Y=0（头顶位置）
        root.addOrReplaceChild("head",
            CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // 右臂：轴心在右肩 Y=2
        root.addOrReplaceChild("right_arm",
            CubeListBuilder.create().texOffs(24, 0)
                .addBox(-3.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(-5.0F, 2.0F, 0.0F));

        // 左臂：轴心在左肩 Y=2
        root.addOrReplaceChild("left_arm",
            CubeListBuilder.create().texOffs(32, 0)
                .addBox(-1.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(5.0F, 2.0F, 0.0F));

        // 右腿：轴心在右髋 Y=12
        root.addOrReplaceChild("right_leg",
            CubeListBuilder.create().texOffs(0, 32)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(-2.0F, 12.0F, 0.0F));

        // 左腿：轴心在左髋 Y=12
        root.addOrReplaceChild("left_leg",
            CubeListBuilder.create().texOffs(8, 32)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(2.0F, 12.0F, 0.0F));

        // 右肩甲：轴心在肩膀 Y=2
        root.addOrReplaceChild("right_shoulder",
            CubeListBuilder.create().texOffs(16, 32)
                .addBox(-4.0F, -2.0F, -3.0F, 5.0F, 4.0F, 6.0F),
            PartPose.offset(-5.0F, 2.0F, 0.0F));

        // 左肩甲
        root.addOrReplaceChild("left_shoulder",
            CubeListBuilder.create().texOffs(24, 32)
                .addBox(-1.0F, -2.0F, -3.0F, 5.0F, 4.0F, 6.0F),
            PartPose.offset(5.0F, 2.0F, 0.0F));

        // 胸口核心：轴心在身体中心 Y=12
        root.addOrReplaceChild("chest_core",
            CubeListBuilder.create().texOffs(32, 16)
                .addBox(-3.0F, -10.0F, -3.0F, 6.0F, 6.0F, 6.0F),
            PartPose.offset(0.0F, 12.0F, 0.0F));

        // 胸甲
        root.addOrReplaceChild("chest_plate",
            CubeListBuilder.create().texOffs(0, 48)
                .addBox(-5.0F, -8.0F, -3.0F, 10.0F, 6.0F, 2.0F),
            PartPose.offset(0.0F, 12.0F, 0.0F));

        // 腰部
        root.addOrReplaceChild("waist",
            CubeListBuilder.create().texOffs(16, 48)
                .addBox(-4.0F, -1.0F, -2.0F, 8.0F, 3.0F, 4.0F),
            PartPose.offset(0.0F, 12.0F, 0.0F));

        // 右手：轴心在右手腕 Y=14（肩膀 Y=2 + 手臂长度 12）
        root.addOrReplaceChild("right_hand",
            CubeListBuilder.create().texOffs(40, 0)
                .addBox(-3.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F),
            PartPose.offset(-5.0F, 14.0F, 0.0F));

        // 左手：轴心在左手腕 Y=14
        root.addOrReplaceChild("left_hand",
            CubeListBuilder.create().texOffs(48, 0)
                .addBox(-1.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F),
            PartPose.offset(5.0F, 14.0F, 0.0F));

        // 右脚：轴心在右脚踝 Y=24
        root.addOrReplaceChild("right_foot",
            CubeListBuilder.create().texOffs(0, 56)
                .addBox(-2.0F, -3.0F, -3.0F, 4.0F, 3.0F, 6.0F),
            PartPose.offset(-2.0F, 24.0F, 0.0F));

        // 左脚：轴心在左脚踝 Y=24
        root.addOrReplaceChild("left_foot",
            CubeListBuilder.create().texOffs(12, 56)
                .addBox(-2.0F, -3.0F, -3.0F, 4.0F, 3.0F, 6.0F),
            PartPose.offset(2.0F, 24.0F, 0.0F));

        // 脖子：轴心在脖子底部 Y=0
        root.addOrReplaceChild("neck",
            CubeListBuilder.create().texOffs(24, 48)
                .addBox(-2.0F, -1.0F, -2.0F, 4.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // 头顶：轴心在头顶 Y=-8
        root.addOrReplaceChild("head_top",
            CubeListBuilder.create().texOffs(32, 48)
                .addBox(-3.0F, -2.0F, -3.0F, 6.0F, 2.0F, 6.0F),
            PartPose.offset(0.0F, -8.0F, 0.0F));

        // 右眼：轴心在头部 Y=0
        root.addOrReplaceChild("right_eye",
            CubeListBuilder.create().texOffs(40, 48)
                .addBox(-3.0F, -5.0F, -1.0F, 2.0F, 3.0F, 1.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // 左眼
        root.addOrReplaceChild("left_eye",
            CubeListBuilder.create().texOffs(44, 48)
                .addBox(1.0F, -5.0F, -1.0F, 2.0F, 3.0F, 1.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // 嘴部
        root.addOrReplaceChild("mouth",
            CubeListBuilder.create().texOffs(48, 48)
                .addBox(-2.0F, -2.0F, -1.0F, 4.0F, 2.0F, 1.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // 核心发光：轴心在身体中心 Y=12
        root.addOrReplaceChild("core_glow",
            CubeListBuilder.create().texOffs(0, 32)
                .addBox(-1.0F, -9.0F, -4.0F, 2.0F, 4.0F, 8.0F),
            PartPose.offset(0.0F, 12.0F, 0.0F));

        // 右耳：轴心在头部 Y=0
        root.addOrReplaceChild("right_ear",
            CubeListBuilder.create().texOffs(56, 48)
                .addBox(-5.0F, -6.0F, -2.0F, 1.0F, 3.0F, 3.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // 左耳
        root.addOrReplaceChild("left_ear",
            CubeListBuilder.create().texOffs(56, 54)
                .addBox(4.0F, -6.0F, -2.0F, 1.0F, 3.0F, 3.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(StarcoreGolemEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * ((float)Math.PI / 180F);
        this.head.xRot = headPitch * ((float)Math.PI / 180F);

        // 头部子部件手动同步旋转（扁平层级，不会自动跟随 head）
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
        this.rightEar.xRot = this.head.xRot;
        this.leftEar.yRot = this.head.yRot;
        this.leftEar.xRot = this.head.xRot;

        float walkSpeed = 0.6F;
        this.rightArm.xRot = Mth.cos(limbSwing * 0.6662F) * limbSwingAmount * walkSpeed;
        this.leftArm.xRot = Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * limbSwingAmount * walkSpeed;
        this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * limbSwingAmount * walkSpeed;
        this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F) * limbSwingAmount * walkSpeed;
        this.rightHand.xRot = this.rightArm.xRot;
        this.leftHand.xRot = this.leftArm.xRot;
        this.rightFoot.xRot = this.rightLeg.xRot;
        this.leftFoot.xRot = this.leftLeg.xRot;

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
