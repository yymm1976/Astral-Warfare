package com.mochi_753.astral_warfare.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
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
        if (entity.level().getBlockState(tpPos).isSolidRender(entity.level(), tpPos)) {
            for (int y = tpPos.getY(); y < tpPos.getY() + 10; y++) {
                BlockPos checkPos = new BlockPos(tpPos.getX(), y, tpPos.getZ());
                if (!entity.level().getBlockState(checkPos).isSolidRender(entity.level(), checkPos)) {
                    entity.teleportTo(tpPos.getX(), y, tpPos.getZ());
                    break;
                }
            }
        }
    }
}
