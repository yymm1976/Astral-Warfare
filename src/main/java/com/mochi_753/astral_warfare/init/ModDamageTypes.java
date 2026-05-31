package com.mochi_753.astral_warfare.init;

import com.mochi_753.astral_warfare.AstralWarfare;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.level.ServerLevel;

// 自定义伤害类型注册
// Minecraft 1.21.1 的伤害类型是数据驱动的：
//   1. JSON 文件定义伤害类型属性（data/astral_warfare/damage_type/void_bleed.json）
//   2. ResourceKey 引用该 JSON，通过注册表获取 Holder<DamageType>
//   3. 用 Holder 构造 DamageSource 实例
public class ModDamageTypes {

    // 虚空流血伤害类型：绕过盔甲的虚空侵蚀伤害
    // 与魔法伤害(magic)不同，这是虚空本源的力量，不属于任何魔法体系
    // 死亡消息：death.attack.astral_warfare.void_bleed
    public static final ResourceKey<DamageType> VOID_BLEED = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "void_bleed")
    );

    // 创建虚空流血伤害源
    // 无实体归因：虚空流血是"效果伤害"，类似原版中毒/凋零
    // 死亡消息只显示"被虚空侵蚀而亡"，不归因于特定实体
    public static DamageSource voidBleed(ServerLevel level) {
        return new DamageSource(
                level.registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(VOID_BLEED)
        );
    }
}
