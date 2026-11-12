# 粒子亮度调优与终结技范围扩大 Spec

## Why
粒子特效过于刺眼（高亮度+堆砌数量），影响视觉舒适度；终结技击飞范围（5格）太小，玩家几乎不需要移动就能跑出范围，缺乏逼迫感。

## What Changes
- 降低 StellaParticles 中所有粒子类型的初始透明度和颜色亮度，减少"闪眼"感
- 精简 Phase2MeleeGoal 和 DespairExecutionGoal 中重复堆砌的粒子数量，用合适的粒子类型代替数量
- 大幅增大终结技击飞范围（LAUNCH_RANGE），逼迫玩家必须迅速远离才能躲避
- 同步增大终结技前摇预警圈半径，与新的 LAUNCH_RANGE 匹配

## Impact
- Affected code:
  - `StellaParticles.java` — 6种粒子的透明度/颜色亮度/尺寸参数
  - `Phase2MeleeGoal.java` — 三段普攻+拉人的粒子数量精简
  - `DespairExecutionGoal.java` — 终结技粒子数量精简 + 预警圈半径
  - `ModConstants.java` — EXECUTION_LAUNCH_RANGE 常量值

## ADDED Requirements

### Requirement: 粒子亮度与数量调优
系统 SHALL 将所有 Lodestone 粒子的初始透明度降低至 0.6~0.75 区间（当前为 0.85~1.0），将颜色亮度降低约 30%（RGB 各通道乘以 0.7），将初始尺寸缩小约 20%，使粒子视觉效果柔和而非刺眼。

#### Scenario: 玩家观察BOSS技能特效
- **WHEN** BOSS释放任意技能产生粒子特效
- **THEN** 粒子亮度柔和、不闪眼，每种粒子类型有清晰的视觉辨识度而非白色一片

### Requirement: 粒子数量精简
系统 SHALL 将 Phase2MeleeGoal 和 DespairExecutionGoal 中每个技能阶段的粒子生成数量减少约 40-50%，优先保留视觉辨识度高的核心粒子，删除重复/冗余的同类粒子堆砌。

#### Scenario: BOSS释放三段普攻
- **WHEN** BOSS执行三段普攻连招
- **THEN** 每段攻击的粒子数量适中（单阶段不超过 15-20 个粒子包），视觉清晰不杂乱

### Requirement: 终结技击飞范围大幅扩大
系统 SHALL 将终结技击飞范围（LAUNCH_RANGE）从 5.0 增大到 10.0，使玩家必须在前摇期间跑出 10 格才能躲避，大幅增加逼迫感。

#### Scenario: 终结技前摇期间玩家闪避
- **WHEN** 终结技前摇开始，玩家在 BOSS 附近
- **THEN** 玩家需要跑出 10 格范围才能完全躲避击飞，而非当前的 5 格

## MODIFIED Requirements

### Requirement: 终结技前摇预警圈
终结技前摇预警圈半径 SHALL 与 LAUNCH_RANGE 一致（10.0 格），当前为 5.0 格。预警圈使用 DYING_EMBER 粒子绘制，段数从 24 增加到 32 以适配更大的半径。

### Requirement: 终结技触发范围
终结技触发范围（TRIGGER_RANGE）从 10.0 增大到 15.0，与新的 LAUNCH_RANGE 匹配，确保玩家在击飞范围内时终结技可以触发。

## REMOVED Requirements
无
