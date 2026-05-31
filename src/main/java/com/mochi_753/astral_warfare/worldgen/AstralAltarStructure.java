package com.mochi_753.astral_warfare.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.material.Fluids;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 星陨祭坛结构 - BOSS 战的舞台
// 程序化生成祭坛方块，替代 .nbt 结构文件
// 祭坛布局：17x1x17 平滑沙石平台 + 四角 3 格高的雕纹沙石柱（水晶基座）+ 中心抛光花岗岩标记
// 结构中心和四角留有特定方块作为"星界水晶机关"的生成定位点
public class AstralAltarStructure extends Structure {

    private static final Logger LOGGER = LoggerFactory.getLogger(AstralAltarStructure.class);

    // 水晶基座相对于祭坛中心的偏移量（radius - 1）
    // 公开常量，供 NocturnalAstrolabeItem 引用，确保水晶生成位置与结构基座一致
    // 此值必须等于 JSON 配置中 radius - 1，构造函数中会校验一致性
    public static final int CRYSTAL_OFFSET = 7;

    // Codec 用于 JSON 反序列化，Structure.DIRECT_CODEC 处理通用字段（biomes, step 等）
    public static final MapCodec<AstralAltarStructure> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Structure.settingsCodec(instance),
                    Codec.INT.fieldOf("radius").forGetter(s -> s.radius)
            ).apply(instance, AstralAltarStructure::new)
    );

    // 祭坛半径（方块数），从中心到边缘
    private final int radius;

    public AstralAltarStructure(StructureSettings settings, int radius) {
        super(settings);
        this.radius = radius;
        // 校验 CRYSTAL_OFFSET 与 radius 的一致性
        // CRYSTAL_OFFSET 必须等于 radius - 1，否则水晶位置与基座错位
        if (CRYSTAL_OFFSET != radius - 1) {
            LOGGER.warn("AstralAltarStructure: CRYSTAL_OFFSET({}) != radius - 1({})，水晶位置可能错位！"
                    + "请同步修改 CRYSTAL_OFFSET 或 JSON 中的 radius", CRYSTAL_OFFSET, radius - 1);
        }
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.ASTRAL_ALTAR_TYPE.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // 在区块中心的地表上方寻找生成点
        // 1.21.1 中使用 StructurePiecesBuilder（旧名 StructurePieceBuilder 已重命名）
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, pieces -> {
            pieces.addPiece(new AstralAltarPiece(
                    context.chunkPos(),
                    context.heightAccessor().getMinBuildHeight(),
                    radius,
                    context.random()
            ));
        });
    }

    // 祭坛结构片段 - 负责实际放置方块
    // 继承 StructurePiece，在 postProcess 中程序化放置祭坛方块
    public static class AstralAltarPiece extends StructurePiece {

        private final int radius;

        // 程序化生成时调用的构造函数
        public AstralAltarPiece(ChunkPos chunkPos, int minY, int radius, RandomSource random) {
            super(ModStructures.ASTRAL_ALTAR_PIECE.get(), 0, makeBoundingBox(chunkPos, minY, radius));
            this.radius = radius;
        }

        // NBT 反序列化构造函数，StructurePieceType.ContextlessType 使用此签名
        // 1.21.1 中 StructurePiece 的 NBT 构造函数签名为 (StructurePieceType, CompoundTag)
        public AstralAltarPiece(CompoundTag tag) {
            super(ModStructures.ASTRAL_ALTAR_PIECE.get(), tag);
            this.radius = tag.contains("radius") ? tag.getInt("radius") : 8;
        }

        // 计算祭坛的包围盒
        private static BoundingBox makeBoundingBox(ChunkPos chunkPos, int minY, int radius) {
            int centerX = chunkPos.getMinBlockX() + 8;
            int centerZ = chunkPos.getMinBlockZ() + 8;
            return new BoundingBox(
                    centerX - radius, minY, centerZ - radius,
                    centerX + radius, minY + 4, centerZ + radius
            );
        }

        // 1.21.1 中 addAdditionalSaveData 签名为 (StructurePieceSerializationContext, CompoundTag)
        // 注意：不是旧版的 (CompoundTag, HolderLookup.Provider)
        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("radius", this.radius);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager,
                                ChunkGenerator generator, RandomSource random, BoundingBox box,
                                ChunkPos chunkPos, BlockPos pos) {
            // 获取祭坛中心坐标（使用包围盒中心）
            int centerX = this.boundingBox.getCenter().getX();
            int centerZ = this.boundingBox.getCenter().getZ();

            // 获取地表高度
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);

            // 避开水下生成：如果地表方块是水，向上寻找第一个非水非空气方块
            // 防止祭坛在海洋生物群系中生成在水下
            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(centerX, surfaceY, centerZ);
            BlockState stateAtSurface = level.getBlockState(mutablePos);
            while (stateAtSurface.getFluidState().is(Fluids.WATER) || stateAtSurface.getFluidState().is(Fluids.FLOWING_WATER)) {
                surfaceY++;
                mutablePos.setY(surfaceY);
                stateAtSurface = level.getBlockState(mutablePos);
                // 安全上限：避免无限循环
                if (surfaceY > level.getMaxBuildHeight()) break;
            }

            // 放置祭坛平台：radius × 1 × radius 的平滑沙石
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos placePos = new BlockPos(centerX + dx, surfaceY - 1, centerZ + dz);
                    level.setBlock(placePos, Blocks.SMOOTH_SANDSTONE.defaultBlockState(), 2);
                }
            }

            // 放置四角水晶基座：3 格高的雕纹沙石柱
            // 这四个位置是未来"星界水晶"的生成定位点
            int[][] corners = {
                    {-radius + 1, -radius + 1},
                    {radius - 1, -radius + 1},
                    {-radius + 1, radius - 1},
                    {radius - 1, radius - 1}
            };
            for (int[] corner : corners) {
                for (int dy = 0; dy < 3; dy++) {
                    BlockPos pillarPos = new BlockPos(
                            centerX + corner[0],
                            surfaceY + dy,
                            centerZ + corner[1]
                    );
                    level.setBlock(pillarPos, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 2);
                }
                // 基座顶部放置平滑石半砖作为装饰
                BlockPos topPos = new BlockPos(
                        centerX + corner[0],
                        surfaceY + 3,
                        centerZ + corner[1]
                );
                level.setBlock(topPos, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 2);
            }

            // 放置中心标记：抛光花岗岩（BOSS 生成定位点）
            BlockPos centerPos = new BlockPos(centerX, surfaceY, centerZ);
            level.setBlock(centerPos, Blocks.POLISHED_GRANITE.defaultBlockState(), 2);

            // 中心周围放置切制沙石台阶装饰
            BlockPos[] centerDecor = {
                    new BlockPos(centerX + 1, surfaceY, centerZ),
                    new BlockPos(centerX - 1, surfaceY, centerZ),
                    new BlockPos(centerX, surfaceY, centerZ + 1),
                    new BlockPos(centerX, surfaceY, centerZ - 1)
            };
            for (BlockPos decorPos : centerDecor) {
                level.setBlock(decorPos, Blocks.SANDSTONE_SLAB.defaultBlockState(), 2);
            }
        }
    }
}
