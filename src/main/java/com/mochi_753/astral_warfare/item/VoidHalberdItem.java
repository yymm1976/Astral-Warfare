package com.mochi_753.astral_warfare.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

// 虚空长戟 - BOSS 专属战利品
// 继承 SwordItem 使其具备攻击属性（攻击伤害 + 攻击速度）
// 使用自定义 VOID_TIER：9.0 攻击伤害加成，0 耐久（不可损坏/不可修复），0 附魔能力
// 0 耐久意味着物品 maxDamage=0，Minecraft 中此类物品不会损耗耐久，也无法修复
public class VoidHalberdItem extends SwordItem {

    // 虚空长戟专属品质
    // 1.21.1 中 SimpleTier 首个参数为 TagKey<Block>（非旧版的 int 挖掘等级）
    // INCORRECT_FOR_NETHERITE_TOOL 对应挖掘等级 4（下界合金级别）
    // uses=0：物品不可损坏；enchantmentValue=0：不可附魔；repairIngredient=EMPTY：不可修复
    public static final Tier VOID_TIER = new SimpleTier(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            0,
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
