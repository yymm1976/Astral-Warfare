package com.mochi_753.astral_warfare.init;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.entity.AstralCrystalEntity;
import com.mochi_753.astral_warfare.entity.NightfallSingularityEntity;
import com.mochi_753.astral_warfare.entity.StarcoreGolemEntity;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mochi_753.astral_warfare.entity.VoidFissureEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

// 实体类型注册中心
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AstralWarfare.MOD_ID);

    public static final Supplier<EntityType<StellaEvokerEntity>> STELLA_EVOKER =
            ENTITIES.register("stella_evoker",
                    () -> EntityType.Builder.of(StellaEvokerEntity::new, MobCategory.MONSTER)
                            .sized(0.6f, 1.95f)
                            .build("stella_evoker"));

    public static final Supplier<EntityType<StarcoreGolemEntity>> STARCORE_GOLEM =
            ENTITIES.register("starcore_golem",
                    () -> EntityType.Builder.of(StarcoreGolemEntity::new, MobCategory.MONSTER)
                            .sized(0.6f, 1.8f)
                            .build("starcore_golem"));

    public static final Supplier<EntityType<AstralCrystalEntity>> ASTRAL_CRYSTAL =
            ENTITIES.register("astral_crystal",
                    () -> EntityType.Builder.of(AstralCrystalEntity::new, MobCategory.MISC)
                            .sized(0.5f, 1.0f)
                            .fireImmune()
                            .build("astral_crystal"));

    // 夜幕黑洞引力触点实体：法术生成的不可见 Misc 实体
    // 无碰撞箱、不可选中、不可攻击
    public static final Supplier<EntityType<NightfallSingularityEntity>> NIGHTFALL_SINGULARITY =
            ENTITIES.register("nightfall_singularity",
                    () -> EntityType.Builder.<NightfallSingularityEntity>of(NightfallSingularityEntity::new, MobCategory.MISC)
                            .sized(0.1f, 0.1f)
                            .fireImmune()
                            .noSummon()
                            .build("nightfall_singularity"));

    // 虚空裂隙实体：终结技砸地后残留的地面伤害区域
    // 不可见 Misc 实体，粒子效果由 tick 中生成
    public static final Supplier<EntityType<VoidFissureEntity>> VOID_FISSURE =
            ENTITIES.register("void_fissure",
                    () -> EntityType.Builder.<VoidFissureEntity>of(VoidFissureEntity::new, MobCategory.MISC)
                            .sized(0.1f, 0.1f)
                            .fireImmune()
                            .noSummon()
                            .build("void_fissure"));

    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(STELLA_EVOKER.get(), StellaEvokerEntity.createAttributes());
        event.put(STARCORE_GOLEM.get(), StarcoreGolemEntity.createAttributes());
        event.put(ASTRAL_CRYSTAL.get(), AstralCrystalEntity.createAttributes());
    }
}
