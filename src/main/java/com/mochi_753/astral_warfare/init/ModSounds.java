package com.mochi_753.astral_warfare.init;

import com.mochi_753.astral_warfare.AstralWarfare;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

// 音效注册中心
// 使用 DeferredRegister 在正确的注册阶段完成 SoundEvent 注册
// 所有音效 ID 必须与 sounds.json 中的条目一一对应
public class ModSounds {

    // DeferredRegister 用于注册所有自定义 SoundEvent
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, AstralWarfare.MOD_ID);

    // 一阶段战斗 BGM：Cathedral Rupture
    // createVariableRangeEvent 允许音量随距离衰减，适合 BGM 的空间感
    public static final Supplier<SoundEvent> STELLA_EVOKER_PHASE1 = SOUND_EVENTS.register(
            "stella_evoker_phase1",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "stella_evoker_phase1")
            )
    );

    // 二阶段战斗 BGM：Ashen Catacomb
    // 转阶段时由 StellaBattleMusic 自动交叉淡入淡出切换
    public static final Supplier<SoundEvent> STELLA_EVOKER_PHASE2 = SOUND_EVENTS.register(
            "stella_evoker_phase2",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "stella_evoker_phase2")
            )
    );
}
