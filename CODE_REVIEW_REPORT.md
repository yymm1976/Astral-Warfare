# Astral Warfare (1.21.1 NeoForge) 代码审查报告

**审查日期**: 2026-06-03
**项目路径**: `1.21.1-BOSS`
**审查范围**: 全部 57 个 Java 源文件 + 资源/配置文件（共 374 个文件）
**审查原则**: 仅审查，不修改代码

---

## 一、项目概览

本项目是一个 Minecraft NeoForge 1.21.1 的 Boss 战斗模组（Astral Warfare），核心内容包括：一个多阶段 Boss（Stella Evoker，法师形态 + 近战形态 + 死亡演出）、法力系统、终结技、7 种法术、傀儡召唤、黑洞/裂隙等战场实体、自定义结构（星穹祭坛）、专属武器与道具、战斗 BGM、屏幕特效与 HUD 覆盖。

**技术栈**: Java 21, NeoForge 2.0.139, GeckoLib 4.7.3, Lodestone 1.7.0.268, PAL 1.1.4, Curios 9.5.1

**整体评价**: 这是一个完成度较高、代码质量上乘的 Boss 模组项目。组件化设计清晰，注释质量远超同类模组平均水平（几乎每个类、方法、关键逻辑块都有中文注释说明设计意图和权衡取舍）。以下按严重程度组织审查发现。

---

## 二、严重问题（建议优先修复）

### 2.1 `forceDie()` 可能导致战利品重复掉落

**文件**: `StellaEvokerEntity.java`

`forceDie()` 使用 `damageSources().generic()` 调用 `super.die()`。`LivingEntity.die()` 内部可能触发 `dropFromLootTable`，而 `onFinishDyingDropLoot()` 中已经显式调用了一次 `dropFromLootTable`。两者叠加可能导致 Boss 战利品翻倍。

**建议**: 确认 `LivingEntity.die()` 的父类实现是否会自动触发战利品掉落，若会则在 `forceDie()` 前设标志位跳过重复掉落。

### 2.2 死亡演出状态机的 NBT 恢复兜底缺失

**文件**: `StellaDyingStateMachine.java`

`isActive()` 基于 `dyingTimer > 0` 判断，`isDying()` 基于 `entityData.get(DATA_IS_DYING)` 判断。若 NBT 恢复时 `dyingTimer` 为 0（数据损坏），`isDying()` 为 `true` 但 `isActive()` 为 `false`，Boss 将永久卡在"正在死亡"状态，既无法恢复战斗也无法被移除。

**建议**: 在 `tick()` 中增加兜底逻辑——若 `isDying()` 为 `true` 但 `dyingTimer <= 0`，自动触发 `finishDying()`。

### 2.3 `isDeadOrDying()` 重写可能导致实体残留

**文件**: `StellaEvokerEntity.java`

死亡演出期间 `isDeadOrDying()` 返回 `false`，阻止自动移除。若 `finishDying()` 执行过程中抛出异常（如粒子系统出错），Boss 实体可能永久残留为 0 血量的僵尸实体。

**建议**: 在 `tick()` 中增加兜底——若血量 <= 0 且不在死亡演出中，强制移除实体。

### 2.4 `CrystalBeamRenderer` 除零风险

**文件**: `CrystalBeamRenderer.java`

`drawBeam` 中 `length = sqrt(dx*dx + dy*dy + dz*dz)`，当水晶和 Boss 位置完全重合时 `length = 0`，后续除法产生 `NaN`，可能导致渲染管线崩溃。

**建议**: 增加 `if (length < 0.001f) return;` 保护。

### 2.5 `StellaBossBarOverlay` 法力条除零风险

**文件**: `StellaBossBarOverlay.java`

`(float) manaData.getCurrentMana() / manaData.getMaxMana()` 当 `getMaxMana()` 返回 0 时产生 `Infinity`/`NaN`，渲染可能崩溃。

**建议**: 增加 `if (maxMana <= 0) maxMana = 1;` 保护。

### 2.6 `ScreenShakeManager` NPE 风险

**文件**: `ScreenShakeManager.java`

`Minecraft.getInstance().player.getRandom()` 在极端时序下（玩家刚断开但最后一帧仍在渲染）`mc.player` 可能为 null。

**建议**: 增加 `if (mc.player == null) return;` 保护。

### 2.7 `DespairExecutionGoal.canUse()` 中冷却计时器递减违反 Goal 语义

**文件**: `DespairExecutionGoal.java`

`cooldownTimer` 的递减写在 `canUse()` 中。Minecraft 的 `GoalSelector` 在同一 tick 内可能多次调用 `canUse()`，导致冷却被多递减，实际冷却时间短于预期。

**建议**: 将冷却递减迁移到 `tick()` 中，并在外部驱动未激活时的递减。

### 2.8 `SpellCastGoal.canUse()` 副作用过重

**文件**: `SpellCastGoal.java`

`canUse()` 内修改了 5 个字段（`currentSpell`, `castTick` 等），违反 `canUse()` 应为纯查询的语义约定。多次调用可能导致冷却被多递减。

**建议**: 将状态修改逻辑迁移到 `start()` 中。

### 2.9 `Phase2MeleeGoal` 背刺斩 NPE 风险

**文件**: `Phase2MeleeGoal.java`

`tickBackstabStrike` 中 `this.target.position()` 的调用不在存活性检查的保护范围内。若 `target` 为 null 或已死亡，将抛出 NPE。同时若 Boss 和目标在同一位置，`normalize()` 返回零向量导致 NaN。

**建议**: 增加 null 检查和零向量保护。

---

## 三、中等问题（建议排期修复）

### 3.1 Boss 脱战检测被演出阶段的 `return` 跳过

**文件**: `StellaEvokerEntity.java`

星门涌动、转阶段演出、死亡演出期间的 `return` 会跳过后续的脱战检测（`checkAnchorDespawn`）。若所有玩家在演出期间跑出检测半径或下线，Boss 不会脱战消失。

**建议**: 将脱战检测提前到所有 `return` 之前执行。

### 3.2 `passiveManaRegenTimer` 未持久化

**文件**: `StellaManaSystem.java` / `StellaEvokerEntity.java`

`passiveManaRegenTimer` 在 `addAdditionalSaveData()` 中未保存。区块重载后计时器归零。虽然 `StellaManaSystem` 已有 `getPassiveManaRegenTimer()` / `setPassiveManaRegenTimer()` 方法，但未被使用。

**建议**: 在 NBT 序列化中补充此字段。

### 3.3 转阶段升空速度无上限

**文件**: `StellaTransitionStateMachine.java`

每 tick 累加 0.5 的向上速度，没有上限。Boss 可能以极高速度冲过目标高度后被瞬间制动到 0，产生不自然的"急停"视觉效果。

**建议**: 使用 `Math.min` 限制最大升空速度。

### 3.4 `VoidFissureEntity` 施法者死亡后不自动消散

**文件**: `VoidFissureEntity.java`

与 `NightfallSingularityEntity` 不同，裂隙在施法者死亡后不会自毁。Boss 死亡后裂隙仍可能对玩家造成约 10 秒的无归因伤害。

**建议**: 增加施法者存活检测，死亡后主动 `discard()`。

### 3.5 BGM 交叉淡入淡出的音频泄漏

**文件**: `StellaBattleMusic.java`

若在 `fadingOutBgm` 尚未完成渐隐时再次调用 `crossfadeToPhase`，旧的 `fadingOutBgm` 会被覆盖但不会被停止，导致一个永远不会被清理的音频实例持续播放。

**建议**: 在覆盖前增加 `if (fadingOutBgm != null) mc.getSoundManager().stop(fadingOutBgm);`。

### 3.6 星轨迷宫网络包过于频繁

**文件**: `SpellCastGoal.java`

`ClientboundMazeSyncPacket` 在施法的 120 tick 中每 tick 发送一次，产生约 80 个网络包。对远程服务器可能造成带宽压力。

**建议**: 仅在切换 `mazeActiveGroup` 时发包，其余 tick 靠客户端本地插值渲染。

### 3.7 `DespairExecutionGoal` 的 `COOLDOWN` 状态是死代码

**文件**: `DespairExecutionGoal.java`

枚举值 `State.COOLDOWN` 存在但状态机从未转入此状态。`stop()` 方法也未完整清理状态——若 Goal 在终结技进行中被抢占，冷却不会被设置，终结技可立即无冷却再次触发。

**建议**: 移除死代码，完善 `stop()` 的状态清理逻辑。

### 3.8 粒子生成频率与帧率耦合

**文件**: `CrystalBeamRenderer.java`, `VoidSigilRenderer.java`, `StarTrackMazeRenderer.java`

三个渲染器的 `particleTickCounter` 在渲染帧（而非游戏 tick）中递增。60 FPS 和 240 FPS 玩家的粒子密度差 4 倍。

**建议**: 改用 `ClientTickEvent` 控制粒子生成计时。

### 3.9 `NocturnalAstrolabeItem` 水晶与 Boss 生成不一致

**文件**: `NocturnalAstrolabeItem.java`

Boss 碰撞检测循环无安全出口——若 15 格内全是固体方块，Boss 可能卡在方块里。另外水晶生成在 `bossSpawned` 判定之外，Boss 生成失败时水晶仍会散落。

**建议**: 增加 fallback 逻辑，将水晶生成移入 `if (bossSpawned)` 条件块。

### 3.10 `GolemMoveToBossGoal.canUse()` 不选最近的 Boss

**文件**: `GolemMoveToBossGoal.java`

使用 `findFirst()` 获取 Boss 但不保证距离最近。`canContinueToUse()` 也未检查 Boss 阶段，进入二阶段后傀儡仍会继续追踪。

---

## 四、低优先级问题（可后续迭代处理）

### 4.1 代码重复（DRY 违反）

| 重复内容 | 涉及文件 | 建议 |
|---------|---------|------|
| `findGroundY()` 方法完全相同 | `StellaTransitionStateMachine` + `StellaGateSurgeAbility` | 提取到 `BossUtils` 工具类 |
| 传送碰撞检测逻辑重复 3 次 | `DespairExecutionGoal` + `Phase2MeleeGoal` (x2) | 提取到工具类 |
| 停止所有 Goal 代码段相同 | `StellaEvokerEntity.die()` + `readAdditionalSaveData()` | 提取为私有方法 |
| 冲击波伤害逻辑相似 | `StellaManaSystem` + `StellaTransitionStateMachine` | 提取共享方法 |
| `isInCone` 方法重复 | `Phase2MeleeGoal` + `SpellCastGoal` | 提取到工具类 |
| `findNearestSurvivalPlayer` 重复 | `SpellCastGoal` + `StellaFlyingMoveControl` | 提取到工具类 |
| 空渲染器结构相同 | `NightfallSingularityRenderer` + `VoidFissureRenderer` | 提取通用基类 |
| `bufferSource.endBatch()` 无参数调用 | 3 个渲染器 | 改为仅刷新使用的 `RenderType.lines()` |

### 4.2 硬编码 vs 配置化不一致

`DespairExecutionGoal` 已将伤害值迁移到 `ModConfig`，但 `Phase2MeleeGoal` 的 `SLASH_DAMAGE`、`THRUST_DAMAGE`、`BACKSTAB_DAMAGE` 等仍是 `static final` 硬编码。建议统一迁移到 `ModConfig` 或 `ModConstants`。

### 4.3 部分类体积过大

`SpellCastGoal`（1173 行）和 `StellaEvokerEntity`（约 990 行）承担了过多职责。建议将各法术的 tick/execute 逻辑拆分到独立的策略类中，将 Boss 的 NBT 序列化和动画注册提取到独立组件。

### 4.4 状态值使用 int 而非枚举

`StellaGateSurgeAbility.gateSurgeState` 使用 `0/1/2/3` 表示状态。建议使用枚举提高可读性和类型安全。

### 4.5 冷却递减策略不统一

`DespairExecutionGoal` 和 `SpellCastGoal` 在 `canUse()` 中递减，`Phase2MeleeGoal` 在 `tick()` 中递减。建议统一策略并在项目文档中说明。

### 4.6 其他小问题

- `StarcoreGolemEntity` 中 `ResourceLocation` 使用了硬编码命名空间 `"astral_warfare"` 而非 `AstralWarfare.MOD_ID` 常量。
- `ModConfig.onConfigReload()` 方法体为空，注册一个空监听器没有实际意义，应移除。
- `NocturnalAstrolabeItem` 注释中 HP 公式 `BaseHP(200) + (N-1) * 100` 与 `ModConfig` 实际默认值（`BASE_HP=1000.0`, `HP_PER_EXTRA_PLAYER=400.0`）不一致，注释过时。
- `ModConstants.EXECUTION_DAMAGE` 标注 `@Deprecated` 但仍保留，死代码应移除。
- `VoidHalberdItem` 的 `SimpleTier.attackDamageBonus(9.0F)` 与 `createAttributes(5, -2.4F)` 叠加后最终攻击伤害为 14 点，建议核实是否符合设计预期并注释最终面板值。
- `StellaFlyingMoveControl` 的 `moveY` 使用 `Math.signum` 在目标高度附近可能产生微小抖动，建议使用死区处理。
- `PlayerAnimationHandler.wasEntrapped` 静态字段在玩家重生/维度切换时无重置机制。
- `VoidEntrapmentEffect` 和 `DespairExecutionGoal` 中使用了全限定类名（如 `net.minecraft.world.phys.Vec3`），而文件头部已有对应 import，属于冗余写法。
- `StellaBossBarOverlay` 使用 `Math.random()` 而非 `RandomSource`，不符合 Minecraft Mod 编码惯例。

---

## 五、NBT 持久化完整性审查

| 状态字段 | 是否持久化 | 备注 |
|---------|:---------:|------|
| CombatPhase | 是 | |
| HasTransitioned | 是 | |
| ManaSystemDisabled | 是 | |
| WeakenedTicks | 是 | |
| IsFallingFromExhaustion | 是 | |
| ImpactTriggered | 是 | |
| CrystalManaRecoverTimer | 是 | |
| **PassiveManaRegenTimer** | **否** | 有 getter/setter 但未在序列化中使用 |
| AnchorCheckTimer | 是 | |
| AltarCenterPos | 是 | |
| IsTransitioning + TransitionTimer | 是 | |
| IsDying + DyingTimer | 是 | |
| ConfigHpInjected | 是 | |
| GateSurge 全部状态 | 是 | |
| StarcoreGolem 充能状态 | 是 | |
| AstralCrystal 存活状态 | 是 | |
| NightfallSingularity casterUUID | 是 | |
| VoidFissure casterUUID | 是 | |

---

## 六、资源文件完整性审查

| 资源类型 | 状态 | 备注 |
|---------|------|------|
| 物品模型 (5 个 JSON) | 完整 | |
| 物品纹理 (3 个 PNG) | 完整 | |
| 实体纹理 (4 个 PNG) | 完整 | |
| 音效文件 (2 个 OGG) | 完整 | stream: true 已配置 |
| sounds.json | 完整 | |
| 伤害类型 JSON | 完整 | |
| 结构 JSON + 生物群系标签 | 完整 | |
| 战利品表 | 基本完整 | **`starcore_golem.json` pools 为空数组** |
| 多语言 | 基本完整 | **缺少 `entity.astral_warfare.void_fissule` 翻译键** |
| GeckoLib 模型/动画 | 完整 | |
| PAL 动画 | 完整 | |
| 着色器 | 完整 | |
| Access Transformer | 存在 | |
| neoforge.mods.toml | 完整 | 依赖声明齐全（NeoForge, Minecraft, Lodestone, Curios, PAL, GeckoLib） |

---

## 七、架构层面评价

### 优势

- **组件化设计**: `StellaTransitionStateMachine`、`StellaDyingStateMachine`、`StellaGateSurgeAbility`、`StellaManaSystem` 四个组件将 Boss 的复杂行为解耦，每个组件职责单一。
- **策略模式**: `SpellType` 枚举通过 `executor` 实现法术执行策略，扩展性好。
- **`ParticleEmitter` 的 try-with-resources 模式**: 优雅解决了粒子批量发送的资源管理问题。
- **`ManaData` 的 synchronized + Codec 快照设计**: 体现了对并发安全性的思考。
- **客户端/服务端隔离**: 正确使用 `@EventBusSubscriber(Dist.CLIENT)`、`FMLEnvironment.dist` 判断和 `level.isClientSide` 检查，Side 安全。
- **防御性编程**: 多处使用 try-catch 包裹实体生成、碰撞检测等高风险操作，`LOGGER.error` 记录异常。
- **网络包设计**: 所有网络包处理器使用 `ctx.enqueueWork()` 调度到主线程，`ClientboundParticleBatchPacket` 包含 count 上限验证防 OOM。

### 不足

- 组件直接依赖 `StellaEvokerEntity` 的具体类型而非接口，增加了测试难度。
- `StellaEvokerEntity` 仍承担过多职责（约 990 行），可进一步拆分。
- 静态字段大量用于客户端状态存储（BGM、震动、法力数据、粒子计时器），在 LAN 服务器和模组热重载场景下可能出问题。
- 缺少统一的工具类，导致代码重复散布在多个文件中。

---

## 八、修复优先级排序

| 优先级 | 问题 | 文件 |
|:------:|------|------|
| P0 | `forceDie()` 战利品重复掉落风险 | `StellaEvokerEntity.java` |
| P0 | 死亡演出 NBT 恢复兜底缺失 | `StellaDyingStateMachine.java` |
| P0 | `isDeadOrDying()` 实体残留风险 | `StellaEvokerEntity.java` |
| P0 | `CrystalBeamRenderer` 除零风险 | `CrystalBeamRenderer.java` |
| P0 | 法力条 HUD 除零风险 | `StellaBossBarOverlay.java` |
| P0 | `ScreenShakeManager` NPE 风险 | `ScreenShakeManager.java` |
| P1 | `DespairExecutionGoal.canUse()` 冷却语义错误 | `DespairExecutionGoal.java` |
| P1 | `SpellCastGoal.canUse()` 副作用过重 | `SpellCastGoal.java` |
| P1 | `Phase2MeleeGoal` 背刺 NPE | `Phase2MeleeGoal.java` |
| P1 | Boss 脱战检测被演出跳过 | `StellaEvokerEntity.java` |
| P1 | `passiveManaRegenTimer` 未持久化 | `StellaEvokerEntity.java` |
| P1 | BGM 音频泄漏 | `StellaBattleMusic.java` |
| P2 | 转阶段升空速度无上限 | `StellaTransitionStateMachine.java` |
| P2 | 裂隙施法者死亡后不自毁 | `VoidFissureEntity.java` |
| P2 | 迷宫网络包过于频繁 | `SpellCastGoal.java` |
| P2 | 粒子频率与帧率耦合 | 3 个渲染器 |
| P2 | 召唤道具碰撞检测无 fallback | `NocturnalAstrolabeItem.java` |
| P3 | 代码重复（DRY 违反） | 多文件 |
| P3 | 硬编码伤害值配置化 | `Phase2MeleeGoal.java` 等 |
| P3 | 大文件拆分 | `SpellCastGoal`, `StellaEvokerEntity` |

---

## 九、总结评分

| 维度 | 评分 | 说明 |
|------|:----:|------|
| 功能完成度 | 9/10 | 核心功能完整，个别次要翻译键和空战利品表待补 |
| 代码健壮性 | 7/10 | 多处缺少边界保护（除零、NPE、兜底机制） |
| 性能与效率 | 7.5/10 | 粒子帧率耦合、网络包频率偏高、重复代码散布 |
| 编码规范 | 8.5/10 | 注释和命名优秀，个别全限定类名和死代码待清理 |
| 资源完整性 | 9/10 | 纹理/音效/模型/JSON 基本齐全，小问题不影响运行 |
| 架构设计 | 8/10 | 组件化清晰，但缺乏接口抽象和工具类收敛 |

**综合**: 这是一个质量较高的模组项目，代码组织清晰，注释极其详尽。主要风险点集中在边界情况保护（除零、NPE、NBT 兜底）和状态管理的一致性上。建议优先修复 P0 级别的 6 个问题以确保稳定性，其余可在后续迭代中逐步处理。

---

*本报告仅列出审查发现，未对项目代码做任何修改。*
