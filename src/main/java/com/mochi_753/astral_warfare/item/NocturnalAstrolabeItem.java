package com.mochi_753.astral_warfare.item;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.entity.AstralCrystalEntity;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mochi_753.astral_warfare.init.ModConfig;
import com.mochi_753.astral_warfare.init.ModConstants;
import com.mochi_753.astral_warfare.init.ModEntities;
import com.mochi_753.astral_warfare.worldgen.AstralAltarStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 夜幕星盘 - BOSS 召唤道具
// 右键使用时检测环境条件：必须是夜晚且不能下雨/下雪
// 环境满足后，进一步检测玩家是否处于"星陨祭坛"结构范围内
// 若全部条件满足，在祭坛中心生成 StellaEvokerEntity，并在四角基座生成 AstralCrystalEntity
//
// 多人动态血量：生成 BOSS 时统计祭坛 30 格内玩家数量 N，动态计算最大生命值
// 公式：BaseHP(200) + (N-1) * 100，确保多人联机时同样具备史诗级对抗体验
public class NocturnalAstrolabeItem extends Item {

    private static final Logger LOGGER = LoggerFactory.getLogger(NocturnalAstrolabeItem.class);

    // 水晶相对于祭坛中心的偏移量
    // 引用 AstralAltarStructure.CRYSTAL_OFFSET，确保与结构基座位置一致
    // 修改结构 radius 时只需改 AstralAltarStructure 中的常量，此处自动同步
    private static final int CRYSTAL_OFFSET = AstralAltarStructure.CRYSTAL_OFFSET;
    // 统计玩家数量的范围
    private static final double PLAYER_COUNT_RADIUS = ModConstants.PLAYER_COUNT_RADIUS;

    public NocturnalAstrolabeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 环境判定仅在服务端执行，客户端只播放使用动画
        if (level.isClientSide) {
            return InteractionResultHolder.pass(stack);
        }

        ServerLevel serverLevel = (ServerLevel) level;

        // 判定1：必须是夜晚
        // 使用原版 Level.isNight() API，内部已处理 dayTime 循环和 /time set 等边界情况
        boolean isNight = level.isNight();

        // 判定2：不能下雨/下雪（isRaining 对雨和雪均返回 true）
        boolean isRaining = level.isRaining();

        if (!isNight) {
            player.sendSystemMessage(Component.translatable("item.astral_warfare.nocturnal_astrolabe.fail_night"));
            return InteractionResultHolder.fail(stack);
        }

        if (isRaining) {
            player.sendSystemMessage(Component.translatable("item.astral_warfare.nocturnal_astrolabe.fail_weather"));
            return InteractionResultHolder.fail(stack);
        }

        // 判定3：检测玩家是否处于祭坛结构范围内
        // 使用 StructureManager 的结构检测 API，替代之前的平滑沙石计数方案
        boolean inAltar = isPlayerInAltarStructure(serverLevel, player);

        if (!inAltar) {
            player.sendSystemMessage(Component.translatable("item.astral_warfare.nocturnal_astrolabe.fail_altar"));
            return InteractionResultHolder.fail(stack);
        }

        // 全部条件满足：生成 BOSS 和水晶
        // 【M13修复】道具消耗移到生成成功之后，避免生成失败时道具白白消耗
        // 【M12修复】BOSS 生成位置添加碰撞检测，避免在低矮空间卡入方块

        // 在玩家位置上方 5 格生成 StellaEvokerEntity
        boolean bossSpawned = false;
        try {
            StellaEvokerEntity boss = ModEntities.STELLA_EVOKER.get().create(serverLevel);
            if (boss != null) {
                // 计算生成位置：玩家上方 5 格
                double spawnX = player.getX();
                double spawnY = player.getY() + 5.0;
                double spawnZ = player.getZ();
                boss.moveTo(spawnX, spawnY, spawnZ, player.getYRot(), 0.0F);

                // 【M12修复】碰撞检测：如果生成位置在固体方块内，向上扫描安全位置
                BlockPos spawnPos = boss.blockPosition();
                if (serverLevel.getBlockState(spawnPos).isSolidRender(serverLevel, spawnPos)) {
                    for (int y = spawnPos.getY(); y < spawnPos.getY() + 15; y++) {
                        BlockPos checkPos = new BlockPos(spawnPos.getX(), y, spawnPos.getZ());
                        if (!serverLevel.getBlockState(checkPos).isSolidRender(serverLevel, checkPos)) {
                            boss.moveTo(spawnX, y, spawnZ, player.getYRot(), 0.0F);
                            break;
                        }
                    }
                }

                boss.setPersistenceRequired();

                // 设置祭坛中心坐标（用于防止活塞推走）
                boss.setAltarCenterPos(player.blockPosition());

                // 多人动态血量平衡系统
                int playerCount = countNearbyPlayers(serverLevel, player.blockPosition());
                float effectiveHp = (float) (ModConfig.BASE_HP.get()
                        + Math.max(0, playerCount - 1) * ModConfig.HP_PER_EXTRA_PLAYER.get());

                var healthAttr = boss.getAttribute(Attributes.MAX_HEALTH);
                if (healthAttr != null) {
                    healthAttr.setBaseValue(effectiveHp);
                }
                boss.setHealth(effectiveHp);

                serverLevel.addFreshEntity(boss);
                bossSpawned = true;
                LOGGER.info("StellaEvoker 已生成，位置: [{}, {}, {}]，玩家数: {}，动态血量: {}",
                        player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ(),
                        playerCount, (int) effectiveHp);
            }
        } catch (Exception e) {
            LOGGER.error("StellaEvoker 生成失败", e);
        }

        // 【M13修复】只有 BOSS 生成成功才消耗道具
        if (bossSpawned && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        if (bossSpawned) {
            player.sendSystemMessage(Component.translatable("item.astral_warfare.nocturnal_astrolabe.resonance"));
        }

        // 在祭坛四角基座位置生成 4 个星界水晶
        // 水晶位置与 AstralAltarStructure 中的四角基座对齐
        try {
            spawnCrystalsAtAltarCorners(serverLevel, player.blockPosition());
        } catch (Exception e) {
            LOGGER.error("星界水晶生成失败", e);
        }

        // 播放空灵音效（末影龙死亡音效变调，营造仪式感）
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.5F);

        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    // 统计祭坛附近玩家数量（用于动态血量计算）
    private int countNearbyPlayers(ServerLevel level, BlockPos center) {
        return level.getEntitiesOfClass(
                Player.class,
                new AABB(center).inflate(PLAYER_COUNT_RADIUS),
                p -> p.isAlive() && !p.isSpectator()
        ).size();
    }

    // 在祭坛四角生成星界水晶
    // 使用 getHeightmapPos(WORLD_SURFACE) 动态获取每个角落的真实地表高度
    // 确保水晶完美贴地，不会因斜坡地形而悬空或埋入地下
    // 支撑方块检测：若地表方块被破坏导致水晶悬空，跳过该位置避免视觉错误
    private void spawnCrystalsAtAltarCorners(ServerLevel level, BlockPos center) {
        int[][] offsets = {
                {-CRYSTAL_OFFSET, -CRYSTAL_OFFSET},
                {CRYSTAL_OFFSET, -CRYSTAL_OFFSET},
                {-CRYSTAL_OFFSET, CRYSTAL_OFFSET},
                {CRYSTAL_OFFSET, CRYSTAL_OFFSET}
        };

        for (int[] offset : offsets) {
            // 使用高度图获取该角落的真实地表空气接触面高度
            // WORLD_SURFACE 是最高非空气方块的高度，确保水晶站在地面上
            BlockPos basePos = new BlockPos(center.getX() + offset[0], 0, center.getZ() + offset[1]);
            BlockPos surfacePos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, basePos);

            // 支撑方块检测：确保地表下方有实体方块支撑
            // 若祭坛底座被破坏导致水晶悬空，跳过该位置避免视觉错误
            BlockPos belowSurface = surfacePos.below();
            if (level.getBlockState(belowSurface).isAir()) {
                continue;
            }

            AstralCrystalEntity crystal = ModEntities.ASTRAL_CRYSTAL.get().create(level);
            if (crystal != null) {
                // 水晶生成在地表上方（基座柱顶半砖上方）
                crystal.moveTo(
                        surfacePos.getX() + 0.5,
                        surfacePos.getY() + 1.0,
                        surfacePos.getZ() + 0.5,
                        0, 0
                );
                // AstralCrystalEntity 继承 LivingEntity 而非 Mob，没有 setPersistenceRequired()
                // 通过设置不可自然移除实现等效持久化
                level.addFreshEntity(crystal);
            }
        }
    }

    // 检测玩家是否处于星陨祭坛结构范围内
    // 使用 StructureManager.getStructureWithPieceAt() 单次精确查询
    // 替代 getAllStructuresAt() 的线性遍历，逻辑等价且性能更优
    //
    // 关键：Structure 注册在 Registries.STRUCTURE 中（非 StructureType），
    // 需要通过 ResourceKey 从注册表查找 Structure 实例，再传给 getStructureWithPieceAt
    // StructureStart.EMPTY 表示该位置没有目标结构
    private boolean isPlayerInAltarStructure(ServerLevel level, Player player) {
        BlockPos playerPos = player.blockPosition();

        // 从注册表获取祭坛 Structure 实例
        var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> altarKey = ResourceKey.create(
                Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "astral_altar")
        );
        Structure altarStructure = structureRegistry.get(altarKey);
        if (altarStructure == null) {
            return false;
        }

        // 使用 getStructureWithPieceAt 单次精确查询，替代 getAllStructuresAt 的线性遍历
        StructureStart start = level.structureManager().getStructureWithPieceAt(playerPos, altarStructure);
        return start != StructureStart.INVALID_START;
    }
}
