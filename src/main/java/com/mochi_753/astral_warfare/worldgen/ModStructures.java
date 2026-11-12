package com.mochi_753.astral_warfare.worldgen;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.worldgen.AstralAltarStructure.AstralAltarPiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

// 结构注册中心
// 注册自定义 StructureType 和 StructurePieceType
// NeoForge 1.21.1 要求所有注册通过 DeferredRegister 完成
public class ModStructures {

    // StructureType 注册：定义结构如何从 JSON 反序列化
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, AstralWarfare.MOD_ID);

    // StructurePieceType 注册：定义结构片段如何从 NBT 反序列化
    // 1.21.1 中 StructurePieceType 位于 pieces 子包
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, AstralWarfare.MOD_ID);

    // 星陨祭坛结构类型
    // 1.21.1 中 StructureType 是接口（不是类），用 lambda 实现 codec() 方法
    public static final Supplier<StructureType<AstralAltarStructure>> ASTRAL_ALTAR_TYPE =
            STRUCTURE_TYPES.register("astral_altar",
                    () -> () -> AstralAltarStructure.CODEC);

    // 星陨祭坛结构片段类型
    // 使用 ContextlessType（不需要 StructurePieceSerializationContext 的简单片段）
    // AstralAltarPiece 的 (CompoundTag) 构造函数匹配 ContextlessType.load(CompoundTag) 签名
    public static final Supplier<StructurePieceType> ASTRAL_ALTAR_PIECE =
            STRUCTURE_PIECE_TYPES.register("astral_altar_piece",
                    () -> (StructurePieceType.ContextlessType) AstralAltarPiece::new);
}
