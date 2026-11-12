# Astral Warfare — 深度代码审查报告

> **项目**: Astral Warfare (NeoForge 1.21.1 BOSS 模组)
> **审查日期**: 2026-06-04
> **审查范围**: 全部 Java 源码 (48 文件) + 资源文件 (22 文件)
> **审查重点**: 功能完成度、潜在缺陷、可优化处

---

## 📊 审查总览

| 严重程度 | 数量 | 说明 |
|---------|------|------|
| 🔴 阻塞性 | 14 | 必须修复，影响正确性或安全性 |
| 🟡 建议修复 | 52 | 应当修复，影响健壮性或可维护性 |
| 💭 小改进 | 40+ | 可选改进，提升代码质量 |

---

## 🔴 阻塞性问题 (Blockers)

### B-01: 黑洞后处理屏幕坐标投影错误
**文件**: `client/postprocess/SingularityRenderHandler.java`
**行号**: 76-77

`screenX` 和 `screenY` 始终设为窗口中心（`getGuiScaledWidth() / 2.0F`），无论黑洞在世界中的实际位置。这意味着后处理扭曲效果永远以屏幕中心为原点，当黑洞不在屏幕正中时扭曲位置完全错误。

**建议**: 使用 `WorldToScreen` 矩阵变换将黑洞的世界坐标正确投影到屏幕坐标。

---

### B-02: BGM 两阶段曲目分配疑似反转
**文件**: `client/StellaBattleMusic.java`
**行号**: 209-213

`getSoundForPhase()` 中，当 `phase == PHASE_2_MELEE` 时返回 `STELLA_EVOKER_PHASE1`，否则返回 `STELLA_EVOKER_PHASE2`。注释说"用户反馈两阶段 BGM 播反了，交换返回值"——但交换后一阶段播放 PHASE2 的 BGM、二阶段播放 PHASE1 的 BGM，需确认音频文件内容是否与命名一致。

---

### B-03: 水晶浮动动画重复叠加
**文件**: `client/AstralCrystalRenderer.java:48` + `client/model/AstralCrystalModel.java:126`

渲染器 `render()` 中基于 `tickCount + partialTick` 计算浮动偏移，模型 `setupAnim()` 中基于 `ageInTicks`（同样 = `tickCount + partialTick`）也计算浮动偏移。两处浮动会叠加，导致实际浮动幅度是预期的两倍。

**建议**: 只保留一处浮动实现。

---

### B-04: 傀儡耳朵缺少 xRot 赋值
**文件**: `client/model/StarcoreGolemModel.java`
**行号**: 251-252

```java
this.rightEar.yRot = this.head.yRot;  // 缺少 xRot
this.leftEar.yRot = this.head.yRot;   // 缺少 xRot
```

耳朵不会跟随头部上下点头（俯仰），只跟随左右转动。

---

### B-05: 屏幕震动 `intensity` 计算顺序错误
**文件**: `client/ScreenShakeManager.java`
**行号**: 44-49

偏移值基于当前 `intensity` 计算（第44-45行），然后 `intensity` 才衰减（第48行）。如果 `falloff > intensity`，当前帧的偏移值基于尚未衰减的 `intensity`，可能在下一帧突然归零导致视觉跳变。更重要的是，`intensity -= falloff` 可能产生负值，而 `if (intensity < 0) intensity = 0` 在负值偏移已经产生之后才执行。

**建议**: 先衰减 intensity，再计算偏移。

---

### B-06: `NocturnalAstrolabeItem` 无论 BOSS 是否生成成功都返回 `sidedSuccess`
**文件**: `item/NocturnalAstrolabeItem.java`
**行号**: 165

当 BOSS 生成失败但水晶成功生成时，客户端会播放"成功"动画（手臂摆动），这是不正确的。应在 `bossSpawned` 为 false 时返回 `InteractionResultHolder.fail(stack)` 或 `pass(stack)`。

---

### B-07: `NocturnalAstrolabeItem` 未检查 `addFreshEntity` 返回值
**文件**: `item/NocturnalAstrolabeItem.java`
**行号**: 134

如果 `addFreshEntity(boss)` 返回 false（实体被拒绝添加，如实体数量限制），`bossSpawned` 仍被设为 true，道具被消耗但 BOSS 未出现。

**建议**: 改为 `if (serverLevel.addFreshEntity(boss)) { bossSpawned = true; }`。

---

### B-08: NBT 反序列化 `radius` 默认值与 JSON 配置可能不匹配
**文件**: `worldgen/AstralAltarStructure.java`
**行号**: 98

`this.radius = tag.contains("radius") ? tag.getInt("radius") : 8` — 如果 JSON 配置中 `radius` 不是 8，已存在的存档加载时使用默认值 8 会导致水晶位置错位。这是一个存档兼容性问题。

---

### B-09: `getEntityData()` 暴露 protected 字段，破坏封装
**文件**: `entity/StellaEvokerEntity.java`
**行号**: 735-737

`public SynchedEntityData getEntityData()` 直接暴露了 `Entity.entityData`（protected 字段），使得任何类都可以随意读写所有 SynchedEntityData 条目，绕过访问控制。外部代码可任意篡改 `DATA_IS_DYING`、`DATA_COMBAT_PHASE` 等关键状态。

**建议**: 改为提供细粒度的 setter/getter。

---

### B-10: `DespairExecutionGoal.canContinueToUse()` 不检查死亡/转阶段
**文件**: `entity/ai/DespairExecutionGoal.java`
**行号**: 127-129

只检查 `state != IDLE` 和 `targetPlayer` 存活。如果 BOSS 在终结技期间进入死亡状态（被其他来源杀死），终结技会继续执行，虽然 `StellaEvokerEntity.tick()` 在 `isDying()` 时 return，但 `GoalSelector` 的 tick 可能在 `customServerAiStep` 之前执行。

---

### B-11: 终结技超时后目标玩家禁锢效果可能残留
**文件**: `entity/ai/DespairExecutionGoal.java`
**行号**: 490-496

当 `stateTimer > 60` 时执行 `cleanupExecution()`，但 `cleanupExecution` 不清除 `targetPlayer` 的 `VOID_ENTRAPMENT` 效果。如果 `targetPlayer` 此时已死亡或离线，禁锢效果会残留。

---

### B-12: `void_entrapment.json` 动画格式问题
**文件**: `player_animations/void_entrapment.json`

1. `beginTick`/`endTick` 不是 GeckoLib/Bedrock 动画格式的标准字段，会被解析器忽略
2. 骨骼 `rotation` 值使用了字符串 `["-7.5", 0, 0]`，标准格式应为数字 `[-7.5, 0, 0]`

---

### B-13: 着色器重新定义了 GLSL 内置 `smoothstep` 函数
**文件**: `shaders/program/singularity_distortion.fsh`
**行号**: 34-37

GLSL 150 已内置 `smoothstep`，重新定义内置函数属于未定义行为（Undefined Behavior），不同 GPU 驱动可能表现不同，在某些驱动上会导致着色器编译失败。

**建议**: 删除自定义 `smoothstep`，直接使用 GLSL 内置版本。

---

### B-14: `cachedNearbyPlayers` 缓存可能对已离开玩家施加效果
**文件**: `entity/NightfallSingularityEntity.java`
**行号**: 61, 245-303

缓存的玩家列表在 `SCAN_INTERVAL`（4tick）内可能过时。如果玩家在两次扫描之间离开了 AABB 范围，`player.hurtMarked = true` 和 `player.setDeltaMovement()` 仍会对已离开引力范围的玩家生效。

**建议**: 在处理每个玩家前也检查距离是否仍在 `PULL_RADIUS` 内。

---

## 🟡 建议修复 (Suggestions)

### S-01: `StellaBossBarOverlay` 多 BOSS 共享静态计时器
**文件**: `client/StellaBossBarOverlay.java:56`

`particleSpawnTimer` 是静态字段，多个 BOSS 共享一个计时器。应改为每个 BOSS 独立计时或基于 `gameTime` 的全局计时。

---

### S-02: `StellaBossBarOverlay` 中 `Math.random()` 性能瓶颈
**文件**: `client/StellaBossBarOverlay.java:170-171`

`Math.random()` 是 synchronized 方法，在高频渲染调用中可能成为性能瓶颈。应使用 `mc.level.random` 或 `ThreadLocalRandom`。

---

### S-03: `StellaBossBarOverlay` 静默吞没所有异常
**文件**: `client/StellaBossBarOverlay.java:111`

`catch(Throwable t)` 吞没了包括 `NullPointerException`、`StackOverflowError` 在内的所有异常，不利于调试。建议至少 log 一次警告。

---

### S-04: `ClientManaData` 无超时清理机制
**文件**: `client/ClientManaData.java`

如果服务端因任何原因未发送 `ClientboundStellaManaRemovePacket`（如实体被 `/kill` 或卸载），客户端缓存将永远残留。建议添加超时清理逻辑。

---

### S-05: `CrystalBeamRenderer` 中 `lastDimension` 使用 `!=` 而非 `equals()`
**文件**: `client/CrystalBeamRenderer.java:59`

`ResourceKey` 的 `!=` 比较依赖 Minecraft 内部 interning 实现，不够健壮。建议改用 `!Objects.equals()`。同样的问题存在于 `VoidSigilRenderer`。

---

### S-06: 多个渲染器静态 `particleTickCounter` 溢出风险
**文件**: `CrystalBeamRenderer.java:43`, `VoidSigilRenderer.java`, `StarTrackMazeRenderer.java`

静态 `int` 计数器只增不减，长时间运行后会溢出为负数。虽然需要极长时间，但建议定期取模重置。

---

### S-07: `MazeData` 缓存位于网络包 record 中且未标记 `volatile`
**文件**: `network/ClientboundMazeSyncPacket.java:43`

`lastMazeData` 由主线程写入、渲染线程读取，未标记 `volatile`，按 JMM 规范存在数据竞争。同时缓存数据放在网络包类中违反单一职责。

---

### S-08: `ScreenShakeManager` 多次快速触发会覆盖前一次
**文件**: `client/ScreenShakeManager.java`

如果服务端短时间内连续发送多个震动包，只有最后一个生效。应考虑叠加或取最大值。

---

### S-09: `ScreenShakeManager` 缺少维度切换/断线重置
**文件**: `client/ScreenShakeManager.java`

静态字段 `intensity/remainingTicks/falloff` 在玩家换维度或断线后不会清零，可能在新维度中残留旧震动。

---

### S-10: `ModPayloads` Lambda 引用客户端类存在 Side 安全风险
**文件**: `network/ModPayloads.java:51, 74-81`

Lambda 引用了 `ClientManaData`、`StellaParticles`、`ClientLevel` 等客户端类，依赖 NeoForge 延迟类加载保证安全性。如果 JVM 优化器提前解析或 NeoForge 行为变更，独立服务端会崩溃。

**建议**: 将客户端处理逻辑迁移到 `client` 包中的独立 Handler 类。

---

### S-11: `ParticleEmitter.flush()` 静默吞没异常
**文件**: `network/ParticleEmitter.java:61-64`

`catch(Exception t)` 静默丢弃所有异常，网络系统持续性错误无法被发现。建议至少 log 一次。

---

### S-12: `ManaData` setter 缺少边界检查
**文件**: `attachment/ManaData.java:50-52`

`setCurrentMana` 不验证 `currentMana >= 0` 且 `currentMana <= maxMana`。法力消耗逻辑有 bug 时不会被此层拦截。建议至少加 `Math.max(0, currentMana)` 防御。

---

### S-13: `ManaData` 复合操作不是原子的
**文件**: `attachment/ManaData.java`

`setCurrentMana` 和 `setMaxMana` 各自单独加锁，"同时设置 current 和 max"的复合操作不是原子的，可能产生 `currentMana > maxMana` 的瞬时不一致状态。

**建议**: 提供 `setBoth(int current, int max)` 复合方法。

---

### S-14: `ModAttachments` 未使用 `.copyOnResolve()`
**文件**: `init/ModAttachments.java`

Data Attachment 默认行为是所有存档共享同一个默认实例。如果 `ManaData` 的默认实例被意外修改，所有新实体会看到被修改的值。建议使用 `.copyOnResolve()` 确保独立性。

---

### S-15: `StellaEvokerEntity` 中 `currentAttackAnim` 和 `isWalking` 为 public 可变字段
**文件**: `entity/StellaEvokerEntity.java:127-131`

这两个 `public` 字段可被任何代码随意修改，没有状态一致性校验。如果外部设置了不存在的动画名称，switch 会匹配到 `default -> null`，攻击动画静默丢失。

**建议**: 改为方法调用，在 setter 中做有效性校验。

---

### S-16: `readAdditionalSaveData` 中 NBT 恢复后可能出现不一致状态
**文件**: `entity/StellaEvokerEntity.java:930-936`

`setNoGravity(false)` 在 `restorePhase2State()` 之前执行。如果 `restorePhase2State` 抛异常，重力设置已生效但 AI 未注册，BOSS 会直接掉落。

---

### S-17: `injectConfigHp()` 使用 `tickCount < 2` 判断首次生成不够稳健
**文件**: `entity/StellaEvokerEntity.java:415`

通过命令召唤等非标准方式创建时 `tickCount` 可能已跳过2。已有 `configHpInjected` 持久化到 NBT，`tickCount < 2` 检查可简化为检查 NBT 标记。

---

### S-18: 脱战检测在死亡演出期间可能导致 BOSS 消失
**文件**: `entity/StellaEvokerEntity.java:530-534`

如果玩家在死亡演出期间全部离开，`discard()` 会跳过死亡演出。建议在 `isDying()` 时跳过脱战检测或延长超时。

---

### S-19: `VoidFissureEntity.caster` 引用未做存活/维度检查
**文件**: `entity/VoidFissureEntity.java:141-145`

与 `NightfallSingularityEntity` 不同，`VoidFissureEntity` 没有检查 `caster` 是否存活或在同一维度。伤害仍以 `indirectMagic(caster, caster)` 归因，可能产生不正确的死亡消息。

---

### S-20: `VoidFissureEntity.readAdditionalSaveData` 未恢复 `damageTimer`
**文件**: `entity/VoidFissureEntity.java:58-62`

NBT 只保存 `casterUUID`，`damageTimer` 丢失。区块重载后可能立即触发伤害。

---

### S-21: 法力枯竭判定可能过早触发
**文件**: `entity/StellaManaSystem.java:58`

当法力暂时低于阈值但法术刚释放完时可能误判触发坠落。建议添加延迟窗口（如连续10tick满足条件才触发）。

---

### S-22: `StellaManaSystem.triggerImpactShockwave` 击退方向可能产生 NaN
**文件**: `entity/StellaManaSystem.java:133`

如果目标恰好在 BOSS 位置上，`normalize()` 会产生零向量。建议添加距离检查（`distSq > 0.01`）。

---

### S-23: `StellaDyingStateMachine.readFromNbt` 不验证 `timer` 有效性
**文件**: `entity/StellaDyingStateMachine.java:137-139`

如果 NBT 被篡改，`timer` 可能是负数或极大值。建议: `dyingTimer = Math.max(0, Math.min(timer, DYING_DURATION_TICKS))`。

---

### S-24: `StellaDyingStateMachine.finishDying` 中 `level.getServer()` 可能返回 null
**文件**: `entity/StellaDyingStateMachine.java:118`

在服务器关闭等极端情况下可能返回 null，导致 NPE。建议添加 null 检查。

---

### S-25: `StellaTransitionStateMachine` NBT 恢复路径可能卡住
**文件**: `entity/StellaTransitionStateMachine.java`

`tick()` 仅在 `isTransitioning()` 为 true 时被调用，但 NBT 恢复可能设置 `DATA_IS_TRANSITIONING = true` 而 `transitionTimer` 未正确恢复，导致演出卡住。

---

### S-26: `StellaGateSurgeAbility.setState()` 无校验
**文件**: `entity/StellaGateSurgeAbility.java:210-212`

接受任意整数，但有效状态只有0/1/2/3。NBT 数据损坏时可能导致技能卡死。建议: `this.gateSurgeState = Math.max(0, Math.min(3, state))`。

---

### S-27: 传送碰撞检测代码重复且缺少回退策略
**文件**: `Phase2MeleeGoal.java:352-360`, `DespairExecutionGoal.java:377-386`

多文件有相同的"向上扫描安全位置"逻辑，如果扫描失败无回退策略（BOSS 会被卡住）。建议提取为 `BossUtils.findSafeTeleportPosition` 工具方法并添加回退逻辑。

---

### S-28: `GolemMoveToBossGoal.canUse()` 每次调用都执行全实体扫描
**文件**: `entity/ai/GolemMoveToBossGoal.java:30-34`

`canUse()` 每 tick 被调用时执行 `getEntitiesOfClass` 搜索 BOSS，在高密度实体场景中有性能影响。建议添加搜索间隔。

---

### S-29: 三段连招伤害值硬编码，不可配置
**文件**: `entity/ai/Phase2MeleeGoal.java:91-94`

`SLASH_DAMAGE = 16.0F`、`THRUST_DAMAGE = 12.0F`、`BACKSTAB_DAMAGE = 28.0F` 等硬编码常量。终结技伤害已改为 `ModConfig` 动态读取，应统一策略。

---

### S-30: 伤害值来源不统一
**涉及文件**: `DespairExecutionGoal`, `Phase2MeleeGoal`, `SpellCastGoal`, `ModConstants`, `ModConfig`

部分伤害来自 `ModConfig`（动态配置），部分来自 `ModConstants`（常量），部分硬编码。建议统一为一种策略。

---

### S-31: `VoidBleedEffect` 首次伤害触发时间不确定
**文件**: `effect/VoidBleedEffect.java:79-81`

`duration % DAMAGE_INTERVAL == 0` 在效果施加时可能立刻触发第一次伤害（如果 duration 恰好是 40 的倍数），而非2秒后。

**建议**: 改为 `duration % DAMAGE_INTERVAL == DAMAGE_INTERVAL - 1`。

---

### S-32: `VoidEntrapmentEffect` 速度修饰符无法完全抵消速度药水
**文件**: `effect/VoidEntrapmentEffect.java`

`ADD_MULTIPLIED_BASE` 模式下 `-1.0` 的修饰符抵消基础速度，但如果实体有速度药水，净效果仍可能有残存速度。`setDeltaMovement` 每 tick 归零弥补了水平移动，但跳跃力无底层锁死。

---

### S-33: `StellaDamageEventHandler` 本地复制了常量
**文件**: `event/StellaDamageEventHandler.java:20`

`WEAKENED_DAMAGE_MULTIPLIER` 从 `ModConstants` 复制到本地字段，不同步风险。应直接引用 `ModConstants.WEAKENED_DAMAGE_MULTIPLIER`。

---

### S-34: `BossUtils.findGroundY` 参数类型截断
**文件**: `util/BossUtils.java:18`

`x` 和 `z` 参数类型是 `double`，在 `mutable.set(x, y, z)` 中隐式截断为 `int`。传入非整数坐标会导致搜索位置偏移。

---

### S-35: `BossUtils.findGroundY` 不检查流体
**文件**: `util/BossUtils.java`

只检查 `isAir()`，不检查流体。如果地表下方是水，方法会认为"找到了地面"并返回水面上方位置。

---

### S-36: `VoidHalberdItem` 攻击伤害可能不符合设计意图
**文件**: `item/VoidHalberdItem.java`

Tier 伤害 9.0 + additionalAttackDamage 5.0 + 基础 1.0 = 总计 15.0 攻击伤害。如果设计意图是 14 点，`additionalAttackDamage` 应为 4。

---

### S-37: `VoidHalberdItem` 不可修复且不可附魔
**文件**: `item/VoidHalberdItem.java`

`repairIngredient = Ingredient.EMPTY` + `enchantmentValue = 0` 使武器不可修复且不可附魔。作为 BOSS 战利品可能过于惩罚性。

---

### S-38: `sounds.json` 缺少 subtitle 字段
**文件**: `assets/astral_warfare/sounds.json`

缺少 `subtitle` 字段和对应翻译键，听障玩家无法获得音效提示。

---

### S-39: BOSS 战 8 种技能无施法音效
**文件**: `assets/astral_warfare/sounds.json`

仅有 2 个 BGM 音效事件，所有技能（光束、锁链、处决等）无对应音效注册。

---

### S-40: `stella_evoker.json` 战利品表 type 缺少命名空间前缀
**文件**: `data/astral_warfare/loot_table/entities/stella_evoker.json`

`"type": "entity"` 应为 `"type": "minecraft:entity"`，与 `starcore_golem.json` 不一致。

---

### S-41: `starcore_golem.json` 缺少 `random_sequence` 字段
**文件**: `data/astral_warfare/loot_table/entities/starcore_golem.json`

1.21.1 中建议所有战利品表都包含此字段以确保可复现性。

---

### S-42: 群系标签冗余重叠
**文件**: `data/astral_warfare/tags/worldgen/biome/has_structure/astral_altar.json`

`#minecraft:is_savanna` 和 `#neoforge:is_savanna` 存在语义重叠。建议只保留 NeoForge 标签。

---

### S-43: 祭坛生成间距可能过大
**文件**: `data/astral_warfare/worldgen/structure_set/astral_altar.json`

`spacing: 48, separation: 32` 意味着平均 768 格才有一个祭坛，可能过于稀疏。

---

### S-44: `hurtMarked` 和 `noPhysics` 字段直接访问
**文件**: `NightfallSingularityEntity.java:303`, `VoidFissureEntity.java:45` 等

依赖 Minecraft 内部 `public` 字段，未来版本可能变更访问权限。建议封装为工具方法。

---

### S-45: `getEntitiesOfClass` 调用频率高，缺少全局缓存机制
**涉及文件**: 多个实体和 AI Goal

多个实体都频繁调用 `getEntitiesOfClass`，各自有频率控制但没有全局优化策略。在多实体场景中，AABB 查询可能成为性能瓶颈。

---

### S-46: 粒子效果无条件生成，未做玩家距离优化
**涉及文件**: 几乎所有实体和 AI Goal

粒子包仍被创建和发送，即使附近没有玩家。虽然服务端 `sendParticles` 会自动过滤不可见粒子，但 `ParticleEmitter` 的 `add` 调用本身有开销。

---

### S-47: `StellaTransitionStateMachine.findGroundY` 在 BOSS 高空时可能找不到正确地面
**文件**: `entity/StellaTransitionStateMachine.java:180-182`

`evoker.getY()` 作为起始搜索高度，转阶段升空后可能导致搜索不到正确地面。

---

### S-48: `DespairExecutionGoal.spawnVoidFissures` 裂隙 Y 坐标使用 `evoker.getY()`
**文件**: `entity/ai/DespairExecutionGoal.java:632`

如果 BOSS 在砸地后 Y 坐标有微小偏移，裂隙可能生成在错误高度。建议使用 `BossUtils.findGroundY`。

---

### S-49: `AstralCrystalEntity.die()` 中硬编码粒子 ID
**文件**: `entity/AstralCrystalEntity.java:130-131`

使用硬编码数字 3 和 2 而非常量，如果 `StellaParticles` 中常量值改变，不会编译报错但运行时粒子错误。

---

### S-50: `AstralCrystalEntity.isCrystalAlive()` 返回 false 后实体仍存活
**文件**: `entity/AstralCrystalEntity.java:92-94`

水晶"死亡"只是将 `DATA_ALIVE` 设为 false，但实体仍存活，占用实体槽位和 tick 时间。建议死亡后 `discard()` 实体。

---

### S-51: `StarcoreGolemEntity.chargeDelayTimer` 可能变为负值
**文件**: `entity/StarcoreGolemEntity.java:87`

当 `chargeScheduled` 为 true 但 `chargeDelayTimer` 已为 0 时，`chargeDelayTimer--` 会使之变负。建议 `Math.max(0, chargeDelayTimer - 1)`。

---

### S-52: `ModConfig.SINGULARITY_TICK_DAMAGE` 单位注释可能误导
**文件**: `init/ModConfig.java`

注释说"每秒窒息伤害"，但实际调用处可能是每 tick。需确认单位一致性。

---

## 💭 小改进 (Nits)

### N-01 ~ N-15: 代码质量改进

| # | 文件 | 改进 |
|---|------|------|
| N-01 | `StellaEvokerEntity` | 动画名称硬编码字符串 → 用常量或枚举替代 |
| N-02 | `StellaEvokerEntity` | `getCelebrateSound()` 返回 null → 返回 `SoundEvents.EMPTY` |
| N-03 | `AstralCrystalEntity` | `isPushable()` + `push()` 双重保护 → 简化 |
| N-04 | `NightfallSingularityEntity` | 粒子每 tick 40-50 个 → 添加距离判断 |
| N-05 | `Phase2MeleeGoal` | `isInCone` 方法 → 提取到工具类复用 |
| N-06 | `SpellType` | `pickRandom` 每次创建临时列表 → 缓存 `values()` |
| N-07 | `SpellType` | `executor` 字段为 `public final` → 改为包可见 |
| N-08 | `StellaFlyingMoveControl` | 减速 `scale(0.8)` 永远不完全停止 → 添加阈值 |
| N-09 | `NightfallSingularityRenderer` + `VoidFissureRenderer` | 相同的空渲染器 → 提取 `NoOpRenderer<T>` |
| N-10 | `StellaEvokerRenderer` | 虚空长戟骨骼偏移硬编码 → 通过 GeckoLib `getBone()` API |
| N-11 | `StarcoreGolemModel` | `@SuppressWarnings("unused")` 的 `body` 字段 → 考虑是否需要 |
| N-12 | `ModConstants` | 大量修复历史注释混入 → 应属于 git commit message |
| N-13 | `DEFAULT_MAX_MANA` | 与 `ManaData` 构造函数中硬编码的 100 未关联 → 应引用常量 |
| N-14 | `PlayerDisconnectHandler` | `hasEffect` + `removeEffect` 冗余 → 直接调用 `removeEffect` |
| N-15 | `VoidBleedEffect.DAMAGE_PER_TICK` | 命名误解 → 重命名为 `DAMAGE_PER_TRIGGER` |

### N-16 ~ N-25: 资源与配置改进

| # | 文件 | 改进 |
|---|------|------|
| N-16 | `en_us.json` / `zh_cn.json` | 缺少音效字幕翻译键 |
| N-17 | `zh_cn.json` | `%1$s` 后缺少空格，与英文版风格不一致 |
| N-18 | `stella_evoker.animation.json` | walk 动画 body 静态 rotation 可简化 |
| N-19 | `stella_evoker.geo.json` | 模型极简（7 骨骼），确认是否为最终设计 |
| N-20 | `singularity_distortion.fsh` | `atan(0,0)` 未定义行为 → 添加安全检查 |
| N-21 | `singularity_distortion.json` | `blend` 模式需确认是否为预期效果 |
| N-22 | `singularity_distortion.vsh` | `gl_Position.z = 0.2` 需注释说明 |
| N-23 | `accesstransformer.cfg` | 建议注明测试过的 Minecraft 版本 |
| N-24 | `neoforge.mods.toml` | 建议补充 `logoFile`、`authors` 等元数据 |
| N-25 | `ModCreativeTabs` | 手动添加物品 → 考虑使用标签自动收集 |

---

## 🏗️ 架构级建议

### A-01: 统一伤害/属性配置策略
当前伤害值分散在 `ModConfig`、`ModConstants`、硬编码常量三处。建议统一为 `ModConfig` 可配置 + `ModConstants` 默认值模式。

### A-02: 提取公共工具方法
以下逻辑在多处重复实现，应提取到 `BossUtils`：
- `findSafeTeleportPosition`（传送碰撞检测）
- `isInCone` / `isInConeWithDir`（扇形范围判定）
- `findGroundY` 的流体感知版本

### A-03: 网络包与客户端缓存分离
`ClientboundMazeSyncPacket` 中的 `lastMazeData` 缓存应移到专门的客户端缓存类中，网络包应保持纯数据载体角色。

### A-04: 粒子系统统一优化
建议在 `ParticleEmitter` 中添加距离检查和批量分组发送能力，减少网络包数量和对无玩家场景的开销。

### A-05: 日志策略
关键状态变更（转阶段、法力枯竭、终结技触发）缺少日志记录，不利于线上问题排查。建议在关键节点添加 debug 级别日志。

---

## ✅ 值得肯定的设计

1. **注释质量极高** — 几乎每个方法都有详细中文注释，包括设计决策和修复历史
2. **状态机架构清晰** — `StellaDyingStateMachine` 和 `StellaTransitionStateMachine` 将复杂状态逻辑解耦
3. **法力系统设计完善** — 法力恢复、虚弱、枯竭坠落形成完整的资源管理循环
4. **粒子批量发送优化** — `ParticleEmitter` + `ClientboundParticleBatchPacket` 减少网络包数量
5. **脱战消失机制** — 避免无人区域的 BOSS 占用服务器资源
6. **GeckoLib 动画系统整合** — 多阶段动画（一阶段/二阶段/死亡/终结技）通过统一动画文件管理
7. **NBT 持久化完整** — 关键状态（战斗阶段、法力、配置血量注入等）均正确持久化
8. **国际化完整** — 中英翻译键一一对应，翻译质量高

---

> 本报告仅指出问题和改进方向，未做任何代码修改。建议按 🔴→🟡→💭 优先级逐步处理。
