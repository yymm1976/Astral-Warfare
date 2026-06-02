package com.mochi_753.astral_warfare.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

// StellaEvoker 专属飞行移动控制器
// 替代默认的 MoveControl，使 BOSS 保持在玩家头顶 5-8 格的空中悬浮/滑行状态
// 核心原理：每帧计算目标位置，平滑插值移动，禁用重力影响
//
// 目标玩家缓存：避免每帧执行 getNearestPlayer 的全实体扫描开销
// 缓存引用在目标失效（死亡、脱离范围）时重新搜索
public class StellaFlyingMoveControl extends MoveControl {

    // 飞行目标高度（距玩家头顶固定值）
    private static final float TARGET_HEIGHT = 6.0f;
    // 水平接近最小距离（小于此值时停止水平移动）
    private static final float MIN_HORIZONTAL_DIST = 3.0f;
    // 目标玩家搜索范围
    private static final double TARGET_SEARCH_RANGE = 64.0;
    // 缓存失效检测间隔（每 20 tick 检查一次缓存目标是否仍有效）
    private static final int CACHE_VALIDATE_INTERVAL = 20;

    // 缓存的目标玩家引用，避免每帧执行 getNearestPlayer
    @Nullable
    private Player cachedTarget;
    // 缓存验证计时器
    private int cacheValidateTimer = 0;

    public StellaFlyingMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        // 如果当前有外部 AI Goal 正在设置移动目标（通过 setWantedPosition），
        // 执行标准的寻路移动逻辑
        if (this.operation == MoveControl.Operation.MOVE_TO) {
            double dx = this.wantedX - this.mob.getX();
            double dy = this.wantedY - this.mob.getY();
            double dz = this.wantedZ - this.mob.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq < 0.01) {
                this.operation = MoveControl.Operation.WAIT;
                return;
            }

            double speed = this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * this.speedModifier;

            // 计算移动向量并标准化
            double mag = Math.sqrt(dx * dx + dz * dz);
            // 除零守卫：当 BOSS 正好在目标正上方/下方时，水平幅度为 0
            // 此时只做垂直移动，不施加水平速度（避免 NaN 导致实体消失）
            double moveX = mag > 0.001 ? (dx / mag) * speed : 0;
            double moveZ = mag > 0.001 ? (dz / mag) * speed : 0;
            double moveY = Math.signum(dy) * Math.min(Math.abs(dy) * 0.1, speed);

            this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(moveX * 0.1, moveY * 0.1, moveZ * 0.1));

            // 设置朝向：面向移动方向
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f, 90.0f));
            this.mob.setYHeadRot(this.mob.getYRot());
            this.operation = MoveControl.Operation.WAIT;
            return;
        }

        // 无外部移动指令时，执行自动悬浮逻辑：保持在玩家头顶
        // Side 安全：悬浮逻辑涉及 setDeltaMovement 等状态变更，仅应在服务端执行
        if (this.mob.level().isClientSide) {
            return;
        }

        this.operation = MoveControl.Operation.WAIT;

        // 缓存目标玩家：避免每帧执行 getNearestPlayer 的全实体扫描
        cacheValidateTimer++;
        if (cachedTarget == null || cacheValidateTimer >= CACHE_VALIDATE_INTERVAL) {
            cacheValidateTimer = 0;
            if (cachedTarget != null &&
                    cachedTarget.isAlive() && !cachedTarget.isSpectator() &&
                    this.mob.distanceTo(cachedTarget) <= TARGET_SEARCH_RANGE) {
                // 缓存目标仍有效
            } else {
                cachedTarget = this.mob.level().getNearestPlayer(this.mob, TARGET_SEARCH_RANGE);
            }
        }

        Player target = cachedTarget;
        if (target == null) {
            // 无目标时缓慢减速悬停，不应用任何移动
            this.mob.setDeltaMovement(this.mob.getDeltaMovement().scale(0.8));
            return;
        }

        // 计算目标悬浮位置：玩家头顶固定高度
        // 使用固定高度而非随机高度，避免每帧随机值不同导致持续向上漂移
        double playerX = target.getX();
        double playerY = target.getY();
        double playerZ = target.getZ();

        double idealY = playerY + TARGET_HEIGHT;

        // 水平距离检查
        double dx = playerX - this.mob.getX();
        double dz = playerZ - this.mob.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // 速度系数：基于 MOVEMENT_SPEED 属性计算
        // 一阶段 MOVEMENT_SPEED = 0.25，乘以 1.0 基础系数 = 0.25
        // 水平移动 = 0.25 * 0.8 = 0.2 blocks/tick ≈ 4 格/秒（与玩家行走速度相当）
        double speed = this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.0;

        double moveX, moveZ;
        if (horizontalDist > MIN_HORIZONTAL_DIST) {
            // 距离太远，靠近玩家
            moveX = (dx / horizontalDist) * speed * 0.8;
            moveZ = (dz / horizontalDist) * speed * 0.8;
        } else {
            // 距离适中，缓慢减速
            moveX = this.mob.getDeltaMovement().x * 0.8;
            moveZ = this.mob.getDeltaMovement().z * 0.8;
        }

        // 垂直方向：平滑趋近目标高度
        // 0.15 趋近系数 + 0.5 最大速度，确保 BOSS 能快速到达目标高度
        double dy = idealY - this.mob.getY();
        double moveY = Math.clamp(dy * 0.15, -speed * 0.5, speed * 0.5);

        this.mob.setDeltaMovement(moveX, moveY, moveZ);

        // 设置朝向：始终面向玩家
        float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), targetYaw, 10.0f));
        this.mob.setYHeadRot(this.mob.getYRot());
    }
}
