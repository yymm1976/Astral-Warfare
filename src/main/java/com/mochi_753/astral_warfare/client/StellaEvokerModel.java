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

    // 动画文件路径：assets/astral_warfare/animations/stella_evoker.animation.json
    // 所有动画合并到单一文件中，因为 GeckoLib 4.7.3 的 AnimationController
    // 通过 RawAnimation 按名称查找动画时，只在 getAnimationResource() 返回的文件中搜索
    // 如果动画分散在多个文件中，Controller 找不到非当前文件中的动画，导致 BOSS 无动作
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(
            AstralWarfare.MOD_ID, "animations/stella_evoker.animation.json");

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
