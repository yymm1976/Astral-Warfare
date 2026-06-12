package com.mochi_753.astral_warfare.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

// BOSS 通用工具类
// 提取多个组件共用的工具方法，消除代码重复（DRY 原则）
public class BossUtils {

    // 从指定坐标向下搜索地面 Y 坐标
    // 从 startY 向下扫描，遇到第一个非空气方块返回其上方 Y+1
    // 如果一直扫描到世界底部仍无实体方块，返回 fallbackY
    // 用于：转阶段演出落地、星门涌动傀儡生成等需要确定地面高度的场景
    public static double findGroundY(Level level, double x, double z, double startY, double fallbackY) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int startBlockY = BlockPos.containing(x, startY, z).getY();
        for (int y = startBlockY; y > level.getMinBuildHeight(); y--) {
            // S-34修复：显式 Math.floor 截断，避免隐式 double→int 转换歧义
            // S-35修复：排除水和岩浆，BOSS 不应站在液面上
            mutable.set((int) Math.floor(x), y, (int) Math.floor(z));
            if (!level.getBlockState(mutable).isAir()
                    && !level.getFluidState(mutable).is(Fluids.WATER)
                    && !level.getFluidState(mutable).is(Fluids.LAVA)) {
                return y + 1.0;
            }
        }
        return fallbackY;
    }

    // 简化重载：从世界最高点向下搜索地面 Y 坐标
    // 适用于不确定起始高度的场景（如地面粒子锚定）
    // 性能注意：比指定 startY 的重载更慢，仅在必要时使用
    // 内部显式传参 level.getMinBuildHeight() 作为 fallbackY，与搜索下界一致
    public static double findGroundY(Level level, double x, double z) {
        return findGroundY(level, x, z, level.getMaxBuildHeight(), level.getMinBuildHeight());
    }

    // S-27修复：合并 Phase2MeleeGoal 和 DespairExecutionGoal 中的重复传送碰撞检测逻辑
    // 传送后如果实体卡在固体方块中，向上扫描最多 10 格寻找安全位置
    // 如果找不到安全位置，保持原位（不会把实体卡得更深）
    // 使用方式：entity.teleportTo(x, y, z) 后调用此方法
    public static void findSafeTeleportPosition(Entity entity) {
        BlockPos tpPos = entity.blockPosition();
        BlockPos headPos = tpPos.above();
        // I-10修复：同时检查脚部和头部位置，确保两格高实体不会被卡住
        if (entity.level().getBlockState(tpPos).isSolidRender(entity.level(), tpPos)
                || entity.level().getBlockState(headPos).isSolidRender(entity.level(), headPos)) {
            for (int y = tpPos.getY(); y < tpPos.getY() + 10; y++) {
                BlockPos feetCheck = new BlockPos(tpPos.getX(), y, tpPos.getZ());
                BlockPos headCheck = feetCheck.above();
                // I-10修复：检查两格垂直净空（适配两格高实体）+ 危险方块检测
                if (!entity.level().getBlockState(feetCheck).isSolidRender(entity.level(), feetCheck)
                        && !entity.level().getBlockState(headCheck).isSolidRender(entity.level(), headCheck)
                        && !isHazardous(entity.level(), feetCheck)
                        && !isHazardous(entity.level(), headCheck)) {
                    // I-10修复：保留实体的小数 X/Z 坐标，避免传送后位置偏移
                    entity.teleportTo(entity.getX(), y, entity.getZ());
                    break;
                }
            }
        }
    }

    // I-10修复：检测指定位置是否包含危险方块（岩浆、火焰、岩浆块、营火）
    // 用于 findSafeTeleportPosition 避免将实体传送到危险位置
    private static boolean isHazardous(Level level, BlockPos pos) {
        if (level.getFluidState(pos).is(Fluids.LAVA)) return true;
        var block = level.getBlockState(pos).getBlock();
        return block == Blocks.FIRE
                || block == Blocks.SOUL_FIRE
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE;
    }
}
