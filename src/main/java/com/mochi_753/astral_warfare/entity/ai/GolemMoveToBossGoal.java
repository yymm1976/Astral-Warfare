package com.mochi_753.astral_warfare.entity.ai;

import com.mochi_753.astral_warfare.entity.StarcoreGolemEntity;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// 傀儡向 BOSS 聚拢 AI Goal
// 蓝图约定：傀儡的核心职责是奔向 BOSS 为其恢复法力，不攻击玩家
// 无论是否充能，傀儡都会向 BOSS 移动
// 充能状态下移动更快（速度修饰符由 StarcoreGolemEntity 管理）
public class GolemMoveToBossGoal extends Goal {

    private final StarcoreGolemEntity golem;
    private StellaEvokerEntity targetBoss;
    private int pathRecalcTimer = 0;
    private static final double BOSS_SEARCH_RANGE = 30.0;
    private static final double ARRIVAL_DIST = 3.0;

    public GolemMoveToBossGoal(StarcoreGolemEntity golem) {
        this.golem = golem;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.golem.level().isClientSide) return false;

        this.targetBoss = this.golem.level().getEntitiesOfClass(
                StellaEvokerEntity.class,
                this.golem.getBoundingBox().inflate(BOSS_SEARCH_RANGE),
                boss -> boss.isAlive() && boss.getCombatPhase() == StellaEvokerEntity.PHASE_1_CASTER
        ).stream().findFirst().orElse(null);

        return this.targetBoss != null && this.golem.distanceTo(this.targetBoss) > ARRIVAL_DIST;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetBoss == null || !this.targetBoss.isAlive()) return false;
        return this.golem.distanceTo(this.targetBoss) > ARRIVAL_DIST;
    }

    @Override
    public void start() {
        this.pathRecalcTimer = 0;
    }

    @Override
    public void stop() {
        this.targetBoss = null;
        this.golem.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.targetBoss == null) return;

        this.golem.getLookControl().setLookAt(this.targetBoss, 30.0F, 30.0F);

        this.pathRecalcTimer--;
        if (this.pathRecalcTimer <= 0) {
            this.pathRecalcTimer = 6;
            this.golem.getNavigation().moveTo(this.targetBoss, 1.2);
        }
    }
}
