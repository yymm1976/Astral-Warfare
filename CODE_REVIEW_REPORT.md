## Astral Warfare Mod (NeoForge 1.21.1) 代码审查报告

**项目名称:** Astral Warfare (com.mochi_753.astral_warfare)
**Minecraft 版本:** 1.21.1 (NeoForge)
**审查日期:** 2026-06-02
**源码文件数:** 55 个 Java 源文件 + 30 个资源文件
**代码总行数:** 约 8,000+ 行 Java 代码

---

## 一、项目概述

本项目是一个 NeoForge 1.21.1 的 Minecraft Mod，实现了一个完整的 BOSS 战斗系统——"星穹唤魔者"(Stella Evoker)。项目包含双阶段 BOSS 战（施法阶段 + 近战阶段）、法力系统、水晶机制、6 种法术、重力奇点、星核傀儡召唤、虚空长戟武器、夜幕星盘召唤道具、祭坛结构世界生成，以及完整的客户端渲染（GeckoLib 骨骼动画、粒子特效、屏幕震动、后处理着色器、战斗 BGM 切换、自定义 BOSS 血条等）。

依赖库包括 Lodestone（粒子系统）、GeckoLib（实体动画）、PAL（玩家动画）和 Curios API。

---

## 二、缺陷总览（按严重度分级）

### 严重（HIGH）— 可能导致崩溃或严重逻辑错误

**H1. StellaEvokerEntity.java — `die()` 方法存在 ConcurrentModificationException 风险**
`die()` 方法中使用 `forEach` 遍历 `getAvailableGoals()` 过滤后的流，同时调用 `stop()` 可能修改目标列表的内部状态。`readAdditionalSaveData()` 中相同逻辑正确地使用了 `.toList()` 先收集再遍历，但 `die()` 方法没有做同样的处理。在 BOSS 死亡时若触发此异常，会导致死亡流程中断，BOSS 卡在 0 生命值无法移除。

**H2. Phase2MeleeGoal.java — 突刺传送无碰撞检测**
`tickThrust()` 中 BOSS 向目标方向传送 6 格，但传送后没有检测是否进入固体方块。与 `DespairExecutionGoal` 中的传送逻辑不同（后者有 `isSolidRender()` 检测 + 向上扫描安全位置），突刺传送可能导致 BOSS 卡在墙壁或地形中，无法正常移动。

**H3. StellaFlyingMoveControl.java — 除零风险**
当 BOSS 正好位于目标玩家的正上方或正下方时（`dx` 和 `dz` 均为 0），水平幅度 `mag` 为 0，导致归一化计算产生 `NaN` 速度。虽然 `distSq < 0.01` 的检测能捕获大多数情况，但当垂直距离大而水平距离为 0 时，`distSq` 可能超过 0.01 而 `mag` 仍然为 0。

**H4. ModConfig.java / ModConstants.java — `EXECUTION_DAMAGE` 常量冲突**
`ModConfig` 中定义了可配置的 `EXECUTION_DAMAGE`（默认 80.0），而 `ModConstants` 中也定义了硬编码的 `EXECUTION_DAMAGE`（值 30.0F）。使用 `ModConstants.EXECUTION_DAMAGE` 的代码将完全忽略服务器配置，使配置系统形同虚设。这是功能完整度方面的严重缺陷。

**H5. ModPayloads.java — 顶层导入客户端专属类，专用服务器存在崩溃风险**
文件顶部直接导入了 `Minecraft`、`ClientLevel`、`ClientManaData`、`ScreenShakeManager`、`StellaParticles` 等客户端专属类。代码依赖 NeoForge 的延迟类加载行为来避免专用服务器上的 `NoClassDefFoundError`。如果 NeoForge 未来版本更改类加载策略，服务器将在类加载阶段直接崩溃。安全的做法是将所有客户端处理逻辑移至独立的 `@OnlyIn(Dist.CLIENT)` 类中。

### 中等（MEDIUM）— 影响功能正确性或游戏体验

**M1. StarcoreGolemEntity.java — 缺少 NBT 持久化**
`chargeDelayTimer` 和 `chargeScheduled` 字段未保存到 NBT。如果区块在傀儡充能延迟期间卸载，重新加载后傀儡将永久处于未充能状态，无法再变为充能状态。

**M2. StellaEvokerEntity.java — NBT 读取不完整**
读取 `AltarCenterPos` 时仅检测 `AltarCenterX` 是否存在，未检测 Y 和 Z。如果 NBT 损坏仅包含 X 坐标，`getInt("AltarCenterY")` 返回 0，生成错误的 BlockPos(?, 0, ?)。

**M3. StellaEvokerEntity.java — `injectConfigHp` 逻辑缺陷**
配置注入条件为 `currentBase < configMaxHp`，注释说"仅当 baseValue 仍为默认 200.0 时覆盖"。但如果缩放道具将基础 HP 设为低于配置值（如配置 300、缩放后 250），该值会被覆盖为 300，破坏缩放系统的功能。

**M4. StellaEvokerEntity.java — `getMaxHealth()` 除零**
`this.getHealth() / this.getMaxHealth()` 在 `getMaxHealth()` 返回 0 时产生 `NaN`（属性未加载或数据损坏），可能导致 BOSS 血条渲染异常或客户端崩溃。

**M5. DespairExecutionGoal.java — 重复屏幕震动包**
`executeSlamImpact()` 中 `ClientboundScreenShakePacket` 被发送了两次（参数完全相同），导致震屏时间翻倍。可能是复制粘贴错误。

**M6. DespairExecutionGoal.java — 目标选择非最近玩家**
`nearbyPlayers.get(0)` 选取 `getEntitiesOfClass` 返回的第一个玩家，该方法不保证按距离排序。BOSS 可能选中较远的玩家而忽略更近的。

**M7. SpellCastGoal.java — 星束法术每 tick 搜索最近玩家**
`tickAstralBeam()` 在 60 tick 的持续时间内每 tick 调用 `getNearestPlayer()`，产生 60 次实体搜索。结果可以缓存。

**M8. StellaTransitionStateMachine.java — `findGroundY()` 每 tick 调用**
过渡阶段每 tick 执行一次向下的方块扫描来寻找地面。地面位置在 4-5 秒的过渡期间不太可能变化，应在过渡开始时缓存结果。

**M9. ManaData.java — Codec 快照非原子操作**
`snapshotCurrentMana()`、`snapshotMaxMana()`、`snapshotManaSystemDisabled()` 各自独立 `synchronized`，但 Codec 序列化时依次调用三个方法，之间其他线程可以修改字段，导致序列化的快照包含来自不同时间点的数据。

**M10. ClientboundParticleBatchPacket.java — 无数据包大小验证**
解码时 `count` 字段直接使用网络数据，无任何上限检测。恶意或损坏的数据包指定 `count = Integer.MAX_VALUE` 将导致 OOM 或长时间挂起。

**M11. StellaBossBarOverlay.java — `getMaxMana()` 除零**
`manaRatio` 计算中如果 `getMaxMana()` 返回 0，产生 `NaN`/`Infinity`，传播到填充宽度和闪光计算，可能导致渲染异常。

**M12. NocturnalAstrolabeItem.java — BOSS 生成无碰撞检测**
BOSS 在玩家上方 5 格处生成，未检测该位置是否在固体方块内。如果祭坛上方天花板较低，BOSS 可能生成在方块中窒息。

**M13. NocturnalAstrolabeItem.java — 道具消耗不检查生成是否成功**
道具在使用时立即消耗（非创造模式），但如果 BOSS 创建失败（`create()` 返回 null 或抛出异常），道具仍然被消耗且无反馈。

**M14. AstralAltarStructure.java — 水面规避逻辑缺陷**
水面规避循环仅检测中心柱位置是否有水。如果中心是水而周围是陆地，整个平台会被提升到水面以上，可能悬浮在空中。此外，超过建筑高度上限时不中止生成，而是静默失败。

### 低（LOW）— 代码质量、可维护性或轻微逻辑问题

**L1. StellaFlyingMoveControl.java — 悬停目标不过滤创造/旁观模式玩家**
`getNearestPlayer` 不过滤游戏模式。BOSS 可能在非战斗的创造模式玩家上方悬停跟随。`NearestAttackableTargetGoal` 正确过滤了创造模式，导致目标选择和悬停行为不一致。

**L2. GolemMoveToBossGoal.java — `canUse()` 每 tick 无节流搜索**
`getEntitiesOfClass` 在 `canUse()` 中每 tick 调用，无缓存或节流。多个傀儡时搜索开销倍增。

**L3. Phase2MeleeGoal.java — 背刺传送在目标仰视/俯视时失效**
BOSS 传送到目标"身后" 1 格处基于视线方向。当目标直视上方或下方时，`targetLook.x` 和 `targetLook.z` 接近 0，BOSS 会被传送到目标正上方而非身后。

**L4. Phase2MeleeGoal.java — 拉扯技能无地面检测**
`tickPull()` 将玩家传送到 BOSS 的 Y 高度，未进行地面检测。如果 BOSS 和玩家处于不同高度，玩家可能被传送到方块内部或空中。

**L5. SpellCastGoal.java — 命运链接可 targeting 创造模式玩家**
命运链接选择最近玩家时不过滤游戏模式。创造模式玩家虽然免疫伤害，但法术仍会消耗法力和冷却。

**L6. StellaManaSystem.java — 两次相同 AABB 的实体搜索**
`recoverManaFromCrystals()` 对同一个 AABB 分别搜索水晶和傀儡实体，可以合并为一次搜索再按类型过滤。

**L7. StellaEvokerEntity.java — 动画状态使用 String 而非枚举**
`currentAttackAnim` 使用字符串匹配 `switch` 表达式。添加新动画时若忘记更新 switch，静默返回 `null`。枚举更安全。

**L8. StellaGateSurgeAbility / StellaTransitionStateMachine — `findGroundY()` 重复实现**
相同的地面扫描方法在两个类中各自实现，违反 DRY 原则。

**L9. AstralCrystalEntity.java — 粒子 ID 使用硬编码魔术数字**
`emitter.add(3, ...)` 和 `emitter.add(2, ...)` 使用原始整数而非命名常量。如果粒子 ID 重新排序，这些数字会无声地失效。

**L10. ScreenShakeManager.java — 硬编码 Mod ID**
`@EventBusSubscriber(modid = "astral_warfare")` 使用字面量而非 `AstralWarfare.MOD_ID`。其他所有类都使用常量引用。

**L11. ClientManaData.java — 死亡/移除的实体不清理缓存**
`removeManaData()` 存在但没有机制在实体死亡或卸载时调用。BOSS 死亡后若新 BOSS 在同一会话中生成，旧 UUID 条目永久残留，每帧被无效遍历。

**L12. PlayerAnimationHandler.java — `wasEntrapped` 状态永不重置**
静态字段 `wasEntrapped` 在玩家登出、重生、切换维度时均不重置。导致被虚空束缚的玩家登出后重新登录时，动画无法再次触发。

**L13. CrystalBeamRenderer.java — 垂直光束除零**
当水晶和 BOSS 的 X/Z 坐标完全相同时，光束方向归一化产生 `NaN`，可能导致渲染伪影或崩溃。

**L14. VoidSigilRenderer.java — 渲染事件中每帧搜索实体**
`getEntitiesOfClass` 在渲染帧事件中调用（60-240 FPS），而非按 tick 节流。32 格 AABB 搜索在此频率下开销显著。

**L15. ParticleEmitter.java — `catch (Throwable)` 吞没 OOM 错误**
`flush()` 和 `close()` 捕获 `Throwable`（包括 `OutOfMemoryError`），危险的内存不足错误会被静默忽略。应缩小为 `catch (Exception)`。

**L16. AstralAltarStructure.java — 每次生成约 305 次不必要的 BlockPos 分配**
平台和柱子方块使用 `new BlockPos(...)` 而非已有的 `MutableBlockPos`，每次结构生成产生约 305 个可避免的不可变对象分配。

**L17. StellaEvokerRenderer.java — 每帧分配 ItemStack**
`VoidHalberdLayer` 在 BOSS 处于第二阶段时每渲染帧创建新的 `ItemStack(ModItems.VOID_HALBERD.get())`。应缓存为字段。

**L18. StellaBossBarOverlay.java — 使用 `Math.random()` 而非 `ThreadLocalRandom`**
在每帧调用的渲染热路径中使用 `Math.random()`，会创建并锁定共享的 `Random` 实例。应使用 `ThreadLocalRandom` 或 Minecraft 提供的 `RandomSource`。

**L19. Model 类 — `setupAnim` 未重置部件姿态**
`AstralCrystalModel` 和 `StarcoreGolemModel` 的 `setupAnim` 方法开始时均未调用 `root().getAllParts().forEach(ModelPart::resetPose)`。如果前一个动画留下了非默认姿态，新动画会叠加其上。

**L20. StarcoreGolemModel.java — 耳朵部件缺少 `xRot` 同步**
所有头部子部件（颈部、头顶、双眼、嘴）都同步了 `yRot` 和 `xRot`，但左右耳仅同步了 `yRot`。当傀儡上下看时，耳朵不随头部俯仰倾斜。

---

## 三、性能分析

### 粒子系统网络开销

本项目的粒子效果非常密集。以下是主要粒子源的开销分析：

| 粒子来源 | 频率 | 每次粒子数 | 等效每 tick |
|----------|------|-----------|------------|
| 绝望处刑 WINDUP 阶段 | 每 tick | 最高 ~70 | ~70 |
| 绝望处刑 SLAM 阶段 | 每 tick | ~10 | ~10 |
| 奇点核心 + 吸积盘 | 每 tick | 10-14 | 10-14 |
| 光束法术 | 每 tick (60 tick) | ~50 | ~50 |
| 斩击连击 | 每 tick (15 tick) | ~53 | ~53 |

`ParticleEmitter` 的批处理机制有效降低了网络包数量，但同类型粒子交替时会自动 flush，降低批处理效率。`DespairExecutionGoal` 的 WINDUP 阶段在 40 tick 内可产生约 2,800 个粒子，对客户端渲染和网络带宽都是显著压力。

### 渲染线程开销

`CrystalBeamRenderer`、`VoidSigilRenderer` 和 `StellaBossBarOverlay` 都在渲染帧事件（60-240 FPS）中执行 `getEntitiesOfClass` AABB 搜索。这些搜索应按 tick 节流或缓存结果，当前实现在高帧率下产生大量冗余计算。

`bufferSource.endBatch()` 在两处被无参数调用（`CrystalBeamRenderer` 和 `VoidSigilRenderer`），这会刷新所有渲染类型而非仅 `RenderType.lines()`，可能干扰共享同一 BufferSource 的其他渲染器。

### 实体搜索效率

`GolemMoveToBossGoal.canUse()` 每 tick 调用实体搜索且无节流，`SpellCastGoal.tickAstralBeam()` 在持续期间每 tick 调用 `getNearestPlayer()`。对比 `StellaFlyingMoveControl` 中有效的 20 tick 缓存机制，这些缺少节流的搜索是明显的优化点。

---

## 四、架构与设计评价

### 优秀之处

**状态机模式应用得当。** 战斗阶段（PHASE_1_CASTER / PHASE_2_MELEE）、死亡动画（StellaDyingStateMachine）、过渡动画（StellaTransitionStateMachine）、门涌能力（StellaGateSurgeAbility）和绝望处刑（DespairExecutionGoal 的 7 状态机）都采用了清晰的状态机设计，状态转换明确。

**关注点分离良好。** 主实体类 StellaEvokerEntity（965 行）虽然较大，但已将法力系统、死亡状态、过渡状态、门涌能力分别提取到独立类中。策略模式（SpellType 枚举 + BiConsumer 执行器）使法术系统易于扩展。

**NBT 持久化全面。** BOSS 实体的战斗阶段、过渡状态、死亡状态、法力系统、门涌进度、祭坛位置都正确保存和恢复，确保区块卸载/加载后战斗状态不丢失。

**客户端-服务器隔离基本正确。** 主 mod 类中使用 `FMLEnvironment.dist == Dist.CLIENT` 保护客户端初始化，粒子通过自定义网络包从服务器发送到客户端。

**配置系统支持热重载。** `ModConfig` 中的 `ConfigValue` 值通过 `.get()` 动态读取，支持配置重载后生效。

### 需要改进之处

**客户端/服务器隔离不彻底。** `ModPayloads.java` 在顶层导入客户端类是最大的架构风险。虽然当前依赖 NeoForge 的延迟加载行为可以工作，但这种模式在版本升级时极其脆弱。

**配置系统与硬编码常量不一致。** `ModConfig`（可配置）和 `ModConstants`（硬编码）的职责划分缺乏明确标准。`EXECUTION_DAMAGE` 在两处以不同值定义是最突出的问题，其他常量如 `STARFALL_DAMAGE`、`WEAKENED_DURATION_TICKS` 等的归属也缺乏一致性。

**维度/会话切换时的静态状态清理不完整。** 多个客户端类使用静态字段但在维度切换或玩家重生时不清理：`ClientManaData.MANA_MAP`、`CrystalBeamRenderer.particleTickCounter`、`VoidSigilRenderer.particleTickCounter`、`StellaBattleMusic` 状态字段、`PlayerAnimationHandler.wasEntrapped`、`ScreenShakeManager` 状态字段。这些字段仅在登出时部分清理，可能导致跨维度或重生后的异常行为。

---

## 五、资源文件审查

### 完整性

所有代码中引用的资源文件均已正确创建，包括 5 个物品模型 + 纹理、4 个实体纹理、2 个 BGM 音频（正确配置为 `stream: true`）、GeckoLib 模型和动画文件、PAL 玩家动画文件、着色器文件、战利品表、伤害类型、世界生成结构和生物群系标签。

JSON 文件语法全部正确。中英文语言文件（en_us.json / zh_cn.json）包含完全一致的 29 个翻译键，覆盖了所有物品、实体、效果、死亡消息和 BOSS 事件消息。

### 发现的问题

缺少可选的效果描述翻译键（`effect.astral_warfare.void_entrapment.description` 和 `effect.astral_warfare.void_bleed.description`），导致效果在背包界面中不显示描述文本。

`player_animations/void_entrapment.json` 包含 `geckolib_format_version` 字段，但该文件由 PAL 而非 GeckoLib 消费，字段被忽略但具有误导性。

后处理着色器文件（`singularity_distortion` 系列）有效但从未被激活（`ENABLED = false`），属于保留的死资源。

`stella_evoker.json` 战利品表使用 `"type": "entity"`（无命名空间前缀），而 `starcore_golem.json` 使用 `"type": "minecraft:entity"`（有前缀），两者均有效但风格不一致。

---

## 六、构建配置评价

`build.gradle.kts` 配置规范，正确使用了 NeoForge ModDev 插件 2.0.139、Java 21 工具链、Parchment 映射。依赖管理合理，CurseMaven 用于获取 Lodestone 1.21.1 NeoForge 版本（附有详细注释解释选择原因），GeckoLib 4.7.3 和 PAL 1.1.4 版本选择有注释说明。Access Transformer 验证已启用。

`gradle.properties` 中的版本号配置合理，NeoForge 版本范围限制了兼容性。

---

## 七、改进优先级建议

**第一优先级（修复崩溃/严重逻辑错误）：**
H1（ConcurrentModificationException）、H2（突刺传送卡墙）、H3（除零崩溃）、H4（配置常量冲突）、H5（服务器类加载风险）

**第二优先级（修复功能缺陷）：**
M1（傀儡 NBT 丢失）、M4（血条 NaN）、M5（重复震屏）、M6（目标选择错误）、M10（数据包验证）、M12/M13（BOSS 生成与道具消耗）

**第三优先级（性能优化）：**
渲染线程中的实体搜索节流、粒子密集场景的带宽优化、`findGroundY()` 缓存、`getNearestPlayer` 调用频率降低

**第四优先级（代码质量）：**
DRY 重复提取、枚举替代字符串、静态状态清理补全、模型姿态重置规范
