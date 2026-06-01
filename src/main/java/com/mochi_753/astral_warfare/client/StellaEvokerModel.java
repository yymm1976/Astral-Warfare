package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

// 星穹唤星者 GeckoLib 模型定义
// 负责告诉 GeckoLib 渲染器去哪里找模型、纹理和动画文件
// 替代原先的 HumanoidModel（原版玩家模型），使用自定义 .geo.json 骨骼结构
public class StellaEvokerModel extends GeoModel<StellaEvokerEntity> {

    // 模型文件路径：assets/astral_warfare/geo/stella_evoker.geo.json
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            AstralWarfare.MOD_ID, "geo/stella_evoker.geo.json");

    // 纹理文件路径：assets/astral_warfare/textures/entity/stella_evoker.png
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AstralWarfare.MOD_ID, "textures/entity/stella_evoker.png");

    // 动画文件路径：assets/astral_warfare/animations/stella_evoker_idle_phase1.animation.json
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(
            AstralWarfare.MOD_ID, "animations/stella_evoker_idle_phase1.animation.json");

    // GeckoLib 4.7.3：三个抽象方法参数均为 T（实体类型）
    // 与 4.7.7+ 版本不同，4.7.3 没有引入 GeoRenderState
    @Override
    public ResourceLocation getModelResource(StellaEvokerEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(StellaEvokerEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(StellaEvokerEntity animatable) {
        return ANIMATIONS;
    }
}
