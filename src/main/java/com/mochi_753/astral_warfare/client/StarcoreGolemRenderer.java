package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.client.model.StarcoreGolemModel;
import com.mochi_753.astral_warfare.entity.StarcoreGolemEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 星核傀儡专属渲染器
 * 使用自定义的 StarcoreGolemModel 替代原版 HumanoidModel 占位
 * 注意：ItemInHandLayer 要求 LivingEntityModel，而 StarcoreGolemModel 继承 HierarchicalModel，
 * 因此不添加 ItemInHandLayer。傀儡不需要手持物品渲染。
 */
public class StarcoreGolemRenderer extends MobRenderer<StarcoreGolemEntity, StarcoreGolemModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "textures/entity/starcore_golem.png");

    public StarcoreGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new StarcoreGolemModel(context.bakeLayer(StarcoreGolemModel.LAYER)), 0.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(StarcoreGolemEntity entity) {
        return TEXTURE;
    }
}
