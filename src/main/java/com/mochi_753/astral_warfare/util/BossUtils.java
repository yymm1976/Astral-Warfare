package com.mochi_753.astral_warfare.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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
            mutable.set(x, y, z);
            if (!level.getBlockState(mutable).isAir()) {
                return y + 1.0;
            }
        }
        return fallbackY;
    }
}
