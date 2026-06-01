package com.mochi_753.astral_warfare.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

// 虚空长戟 - BOSS 专属战利品
// 继承 SwordItem 使其具备攻击属性（攻击伤害 + 攻击速度）
// 使用自定义 VOID_TIER：9.0 攻击伤害加成，2031 耐久（与下界合金剑相同），0 附魔能力
// uses=0 在 Minecraft 中会导致物品一击就碎，必须设置正数耐久值
public class VoidHalberdItem extends SwordItem {

    // 虚空长戟专属品质
    // 1.21.1 中 SimpleTier 首个参数为 TagKey<Block>（非旧版的 int 挖掘等级）
    // INCORRECT_FOR_NETHERITE_TOOL 对应挖掘等级 4（下界合金级别）
    // uses=2031：与下界合金剑相同的耐久度；enchantmentValue=0：不可附魔；repairIngredient=EMPTY：不可修复
    public static final Tier VOID_TIER = new SimpleTier(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2031,
            1.0F,
            9.0F,
            0,
            () -> Ingredient.EMPTY
    );

    public VoidHalberdItem() {
        super(VOID_TIER, new Properties()
                .rarity(Rarity.EPIC)
                .stacksTo(1)
                .fireResistant()
                .attributes(SwordItem.createAttributes(VOID_TIER, 5, -2.4F))
        );
    }
}
