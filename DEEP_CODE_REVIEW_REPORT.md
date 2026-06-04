# Astral Warfare 深度代码审查报告

**项目**: Astral Warfare (星穹战争) — Minecraft 1.21.1 NeoForge BOSS Mod  
**审查日期**: 2026-06-04  
**审查范围**: 全量源码（实体、AI、网络、渲染、物品、效果、配置、资源）  
**审查重点**: 功能完成度、潜在缺陷、可优化处

---

## 一、总体评价

这是一个**完成度极高**的 BOSS 战模组，代码组织合理、注释详尽、架构分层清晰。核心战斗循环（一阶段法术→法力枯竭坠落→二阶段近战→终结技）完整闭环，状态机设计严谨，NBT 持久化全面，网络同步有脏数据判差优化。在同类 BOSS 模组中属于上乘水准。

**亮点**：
- 组件化架构（StellaManaSystem / StellaTransitionStateMachine / StellaDyingStateMachine / StellaGateSurgeAbility）职责单一、易于维护
- 脏数据判差法力同步，避免网络风暴
- 全面的 NBT 持久化，覆盖所有内部状态
- 策略模式法术枚举（SpellType），扩展性好
- 详细的注释和设计意图文档，几乎每行关键代码都有注释说明

---

## 二、🔴 阻塞级问题（Blocker — 必须修复）

### 🔴 B-1: Phase2MeleeGoal 伤害值使用 static final 初始化 ModConfig，配置热重载不生效

**位置**: `Phase2MeleeGoal.java` L95-98

```java
private static final float SLASH_DAMAGE = ModConfig.SLASH_DAMAGE.get().floatValue();
private static final float THRUST_DAMAGE = ModConfig.THRUST_DAMAGE.get().floatValue();
private static final float BACKSTAB_DAMAGE = ModConfig.BACKSTAB_DAMAGE.get().floatValue();
private static final float PULL_PUNCH_DAMAGE = ModConfig.PULL_PUNCH_DAMAGE.get().floatValue();
```

**问题**: `static final` 在类加载时初始化一次，之后 `ModConfig` 值变更不会反映。服主修改 toml 后需要重启服务器才能生效，与 `ModConfig.onConfigReload` 注释的"无需重启"矛盾。而 `DespairExecutionGoal` 中的 `getExecutionDamage()` 方法正确使用了运行时 `.get()` 读取。

**建议**: 改为实例方法（与 `DespairExecutionGoal.getExecutionDamage()` 一致）：

```java
private float getSlashDamage() { return ModConfig.SLASH_DAMAGE.get().floatValue(); }
private float getThrustDamage() { return ModConfig.THRUST_DAMAGE.get().floatValue(); }
private float getBackstabDamage() { return ModConfig.BACKSTAB_DAMAGE.get().floatValue(); }
private float getPullPunchDamage() { return ModConfig.PULL_PUNCH_DAMAGE.get().floatValue(); }
```

---

### 🔴 B-2: 夜幕黑洞施法结束后过早丢弃实体，导致最后几 tick 吸引力丢失

**位置**: `SpellCastGoal.java` L296-300

```java
private void cleanupSpell() {
    if (this.singularity != null) {
        this.singularity.discard();
        this.singularity = null;
    }
```

**问题**: `cleanupSpell()` 在施法 `tick()` 结束时调用（`castTick >= castDuration`），但 `NightfallSingularityEntity` 自身已有 `MAX_LIFETIME = 200 tick` 的超时自毁机制。施法时长 80 tick 结束后立即丢弃黑洞，黑洞还剩 120 tick 的自然生命周期，本应继续吸引和禁锢玩家。这导致黑洞的实际存续时间只有施法时长，而非设计中的自然生命周期。

**建议**: 移除 `cleanupSpell()` 中的 `singularity.discard()`，让黑洞自然超时消散。或改为仅在非黑洞法术时清理：

```java
if (this.currentSpell != SpellType.NIGHTFALL_SINGULARITY && this.singularity != null) {
    this.singularity.discard();
}
this.singularity = null;
```

---

### 🔴 B-3: 转阶段冲击波和法力枯竭冲击波无距离安全检查，可能对极近距离目标产生 NaN 击退

**位置**: `StellaTransitionStateMachine.java` L157

```java
Vec3 knockbackDir = target.position().subtract(evoker.position()).normalize();
target.knockback(2.5F, -knockbackDir.x, -knockbackDir.z);
```

**问题**: 如果目标恰好在 BOSS 位置重合（`distanceToSqr ≈ 0`），`normalize()` 会产生 `NaN`，导致实体被踢出世界。`StellaManaSystem.triggerImpactShockwave()` 已有此修复（L134-135 检查 `distSq < 0.01`），但转阶段冲击波遗漏。

**建议**: 添加距离检查，与 `StellaManaSystem` 保持一致：

```java
double distSq = target.distanceToSqr(evoker);
if (distSq < 0.01) continue;
Vec3 knockbackDir = target.position().subtract(evoker.position()).normalize();
```

---

### 🔴 B-4: 星命锁链 FATE_LINK_MAX_DIST 从 12 格被放大到 48 格，严重破坏战斗平衡

**位置**: `ModConstants.java` L92

```java
public static final double FATE_LINK_MAX_DIST = 48.0;
```

**问题**: Phase 28 将星命锁链最大距离从 18.0 放大到 48.0。原设计是"3秒内跑出12格即可挣脱"，48格意味着玩家几乎不可能在3秒内跑出范围，星命锁链变成了必中技能。注释中说的是"12格"，但常量值是48。这要么是 Phase 28 批量放大的疏忽，要么缺少配套的施法时长调整。

**建议**: 
- 如果意图是 12 格判定距离，改回 12.0 或至少降低到一个3秒内可达的距离（如 24-30）
- 如果意图是 48 格，则需要同步增加施法时长，否则锁链斩杀变为无条件命中

---

## 三、🟡 建议级问题（Suggestion — 应该修复）

### 🟡 S-1: findNearestSurvivalPlayer 重复实现三处，应提取为工具方法

**位置**:
- `StellaEvokerEntity.java` L999-1008
- `SpellCastGoal.java` L321-335
- `SpellCastGoal.java` 的 `findNearestSurvivalPlayer` 重载

**问题**: 同一个"查找最近非创造/非旁观玩家"逻辑被复制了3次，搜索范围硬编码 64.0 不一致（有的用 `inflate(64.0)`，有的用 `getBoundingBox().inflate(64.0)`）。

**建议**: 提取到 `BossUtils.findNearestSurvivalPlayer(level, center, range)` 统一调用。

---

### 🟡 S-2: NightfallSingularity 和 VoidFissure 的 `caster` 字段在区块重载后可能长期为 null

**位置**: `NightfallSingularityEntity.java` L122-127

```java
if (this.caster == null && this.casterUUID != null) {
    Entity entity = serverLevel.getEntity(this.casterUUID);
    if (entity instanceof LivingEntity living) {
        this.caster = living;
    }
}
```

**问题**: 如果施法者不在同一区块或已卸载，`getEntity(UUID)` 返回 null，但 `casterUUID` 仍存在。下一 tick 又会重试，但始终失败。此时 L131 的检查 `this.caster == null` 会直接 `discard()` 黑洞——即使施法者只是暂时不在范围内。对于 BOSS 战场景，BOSS 不太可能离开同一维度，但如果区块卸载再加载，短暂找不到施法者就会导致黑洞提前消散。

**建议**: 改为连续 N tick（如 100 tick = 5秒）找不到施法者才自毁，而非首 tick 就丢弃：

```java
private int casterMissingTicks = 0;
// ...
if (this.caster == null || !this.caster.isAlive() || this.caster.level() != this.level()) {
    casterMissingTicks++;
    if (casterMissingTicks > 100) {
        this.discard();
    }
    return;
}
casterMissingTicks = 0;
```

---

### 🟡 S-3: DespairExecutionGoal COOLDOWN 状态在 Goal 停止时被跳过

**位置**: `DespairExecutionGoal.java` L656-669

**问题**: `stop()` 方法直接将状态设为 IDLE，跳过了 COOLDOWN 状态。但 `canUse()` 中检查 `cooldownTimer > 0` 时直接返回 false 并递减。问题在于如果 Goal 被 `stop()` 后 `cooldownTimer` 已设置为 `COOLDOWN_TICKS * 3 / 4`，但 `canUse()` 在递减到 0 前不会执行其他逻辑。这实际上不影响功能，但 `tickCooldown()` 中的 `cooldownTimer` 递减逻辑永远不会被执行（因为 COOLDOWN 状态只在 `executeSlamImpact` 后设置，而此时 Goal 还在活跃状态，会通过 `tick()` 调用 `tickCooldown()`）。这是正确的行为，但代码路径不清晰。

**建议**: 添加注释说明 `stop()` 是异常终止路径，COOLDOWN 状态只在正常完成时进入。

---

### 🟡 S-4: StellaEvokerEntity.tick() 中星轨迷宫施法和星门涌动的优先级冲突

**位置**: `StellaEvokerEntity.java` L664-684

**问题**: 当血量同时满足 80%（迷宫触发）和 75%（星门涌动触发）时，迷宫和星门涌动可能在同一 tick 触发。当前代码先检查星门涌动（L664-672），如果触发了星门涌动则在 `gateSurgeAbility.tick()` 后 `return`，迷宫不会在同一 tick 触发。但如果星门涌动未触发（例如已触发过），迷宫就会触发。逻辑上是正确的，但依赖代码顺序隐式保证优先级，容易在重构时破坏。

**建议**: 添加显式注释说明血量阈值交叉时的优先级规则，或将血量阈值调整到不交叉的范围。

---

### 🟡 S-5: AstralCrystalEntity 水晶死亡后仍保留为 LivingEntity，持续消耗 tick 开销

**位置**: `AstralCrystalEntity.java` L121-140

**问题**: 水晶被击碎后调用 `super.die(source)`，`LivingEntity.die()` 设置 `dead = true` 但实体仍存在于世界中，后续每 tick 仍执行 `LivingEntity.tick()` 的存活检测逻辑。对于不可移动的装饰性实体，更好的做法是在死亡后直接 `discard()` 或在 `tick()` 中对死亡水晶短路返回。

**建议**: 在 `tick()` 开头添加：

```java
if (!isCrystalAlive()) return;
```

---

### 🟡 S-6: ModConstants 中存在 @Deprecated 常量仍在被引用

**位置**: `ModConstants.java` L159

```java
@Deprecated
public static final float EXECUTION_DAMAGE = 30.0F;
```

**问题**: 注释说"代码不再引用"，但 `@Deprecated` 常量仍在类中占用空间，且值（30.0F）与 `ModConfig` 默认值（80.0）不一致，可能误导开发者。

**建议**: 完全移除此常量，或改为注释 `// 已迁移至 ModConfig.EXECUTION_DAMAGE（默认 80.0）`。

---

### 🟡 S-7: 星界发散光束粒子数量过大，可能造成低端客户端卡顿

**位置**: `SpellCastGoal.java` L511-559

**问题**: 每tick 生成 32（核心线）+ 180（锥形扩散）+ 160（地面投影）= 372 个粒子，施法持续 60 tick。总计约 22,320 个粒子。这对低配电脑是严重负担。

**建议**: 添加粒子密度控制配置项，或降低每tick粒子数量。至少锥形扩散的 180 个可以降到 60-80。

---

### 🟡 S-8: GolemMoveToBossGoal 中未检查 BOSS 是否存活

**位置**: `entity/ai/GolemMoveToBossGoal.java`（推测）

**问题**: 傀儡的寻路目标指向 BOSS，但如果 BOSS 已死亡或被移除，傀儡应停止移动。当前未看到对 BOSS 存活状态的检查。

**建议**: 在 `canUse()` / `canContinueToUse()` 中添加 BOSS 存活检查。

---

### 🟡 S-9: VoidFissureEntity 不对创造模式玩家造成伤害是正确的，但不对骑乘实体上的玩家造成伤害可能不正确

**位置**: `VoidFissureEntity.java` L151-159

**问题**: 伤害判定使用 `getEntitiesOfClass(Player.class, ...)` 只查找 Player 实体。如果玩家骑在马上，玩家的碰撞箱可能不在裂隙范围内（因为马抬高了玩家的碰撞箱中心）。裂隙的伤害范围 Y 轴只有 `getY() - 0.5` 到 `getY() + 2.0`，骑乘玩家可能超出范围。

**建议**: 扩大 Y 轴判定范围到 `+3.0` 或 `+4.0`，覆盖骑乘状态。

---

### 🟡 S-10: StellaEvokerEntity 没有注册 Phase 2 的 AI Goal 到 targetSelector

**位置**: `StellaEvokerEntity.java` L302-1316

**问题**: `registerGoals()` 中注册了 `NearestAttackableTargetGoal` 和 `HurtByTargetGoal`，但 `restorePhase2State()` 清空了所有 `goalSelector` 中的 Goal 后没有重新注册 `targetSelector` 的目标选择 Goal。虽然 `targetSelector` 未被清空（只清了 `goalSelector`），这意味着二阶段仍保留一阶段的目标选择器。但 `HurtByTargetGoal` 可能在一阶段产生意外的目标切换行为。

**建议**: 确认 `targetSelector` 在二阶段的行为是否符合预期，必要时在 `restorePhase2State()` 中也重新注册目标选择器。

---

## 四、💭 改善级建议（Nit — 锦上添花）

### 💭 N-1: StellaEvokerEntity 类体积过大（1332 行），考虑进一步拆分

**建议**: 星轨迷宫逻辑（L126-990）可拆分为 `StellaMazeAbility` 组件，与 `StellaGateSurgeAbility` 对称。

### 💭 N-2: ModConstants 与 ModConfig 存在大量重复配置

**问题**: 法术伤害值同时存在于 `ModConstants`（硬编码）和 `ModConfig`（可配置），部分值已迁移但大部分仍只在 `ModConstants`。服主无法通过配置调整星陨矩阵伤害、黑洞伤害等。

**建议**: 逐步将所有战斗数值迁移到 `ModConfig`，`ModConstants` 只保留纯结构性常量（如网格大小、持续时间）。

### 💭 N-3: 6种法术的冷却递减在 `canUse()` 中每tick遍历所有法术

**位置**: `SpellCastGoal.java` L104-109

**建议**: 可以优化为只在 `nextCastAttempt == 0` 时才遍历冷却列表，减少不必要的 EnumMap 查询。

### 💭 N-4: 星轨切割激光方向未对齐到格子线

**建议**: 如果激光方向能对齐到 8 方向（45度间隔），迷宫效果会更有规律感，玩家也更容易判断安全路径。

### 💭 N-5: 缺少日志输出关键状态转换

**建议**: 在转阶段、死亡演出、终结技执行等关键状态转换处添加 `LOGGER.info()`，便于排查线上问题。当前只有 `die()` 和 `forceDie()` 有日志。

### 💭 N-6: BossBar 颜色在死亡时切换为 PINK 但未在死亡结束后恢复

**位置**: `StellaEvokerEntity.java` L466-468

**问题**: 死亡演出期间 BossBar 颜色设为 PINK，但死亡结束后 `bossEvent.removeAllPlayers()` 直接移除，颜色不会恢复。这不是 bug（因为玩家已经不再追踪），但如果有其他模组查询 BossBar 状态，可能看到不一致。

### 💭 N-7: 语言文件缺少部分法术提示翻译键

**建议**: 确认 `en_us.json` 和 `zh_cn.json` 包含所有 `entity.astral_warfare.stella_evoker.spell.*` 和 `entity.astral_warfare.stella_evoker.despawn` 等翻译键。

---

## 五、功能完成度评估

| 模块 | 完成度 | 说明 |
|------|--------|------|
| BOSS 核心实体 | ★★★★★ | 两阶段战斗完整，状态机严谨 |
| 一阶段法术系统 | ★★★★★ | 6种法术+星轨迷宫+星门涌动，策略模式可扩展 |
| 法力值系统 | ★★★★★ | 消耗/恢复/枯竭坠落/虚弱，全链路闭环 |
| 二阶段近战 | ★★★★★ | 三段连招+拉人+终结技，互斥机制完善 |
| 死亡演出 | ★★★★★ | 5秒演出+战利品+全服公告，NBT持久化 |
| 网络同步 | ★★★★☆ | 脏数据判差优秀，但部分粒子包数量偏大 |
| 配置系统 | ★★★☆☆ | 近战伤害和部分法术已配置化，但大量常量仍硬编码 |
| NBT持久化 | ★★★★★ | 覆盖所有内部状态，防止区块重载后状态丢失 |
| 客户端渲染 | ★★★★☆ | GeckoLib动画+自定义BossBar+后处理+粒子，但缺少部分粒子密度控制 |
| 资源文件 | ★★★★☆ | 语言文件、战利品表、结构生成齐全 |

---

## 六、修复优先级建议

1. **🔴 B-1** — Phase2MeleeGoal 伤害配置热重载失效（影响服主体验）
2. **🔴 B-3** — 转阶段冲击波 NaN 击退（可能导致崩溃）
3. **🔴 B-2** — 黑洞过早丢弃（影响战斗体验）
4. **🔴 B-4** — 星命锁链距离过大（影响战斗平衡）
5. **🟡 S-7** — 粒子数量过大（影响低配客户端）
6. **🟡 S-5** — 水晶死亡后仍消耗 tick（影响性能）
7. **🟡 S-1** — 重复代码提取（提高可维护性）
8. 其余 🟡 和 💭 按需修复

---

## 七、架构评价

项目采用了**组件化状态机架构**，将 BOSS 的复杂行为拆分为独立的组件类：

```
StellaEvokerEntity (核心实体)
├── StellaManaSystem (法力系统)
├── StellaTransitionStateMachine (转阶段演出)
├── StellaDyingStateMachine (死亡演出)
├── StellaGateSurgeAbility (星门涌动)
├── SpellCastGoal (一阶段法术AI)
├── Phase2MeleeGoal (二阶段近战AI)
└── DespairExecutionGoal (终结技AI)
```

这种设计的好处是每个组件职责单一，可以独立测试和修改。但组件间通过 `StellaEvokerEntity` 的包可见方法通信，耦合度仍然存在。如果未来需要支持多个 BOSS，可能需要引入接口层。

**总体而言，这是一个工程质量很高的模组项目，核心战斗逻辑完整且严谨。修复上述 4 个 Blocker 和 10 个 Suggestion 后，即可达到生产级质量。**
