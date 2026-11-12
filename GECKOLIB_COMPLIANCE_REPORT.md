## GeckoLib 4.7.3 使用合规性审查报告

**项目:** Astral Warfare (NeoForge 1.21.1)
**GeckoLib 版本:** 4.7.3 (`geckolib-neoforge-1.21.1:4.7.3`)
**审查日期:** 2026-06-02
**审查范围:** 3 个 Java 文件 + 2 个资源文件 + 构建配置

---

## 一、总体结论

项目对 GeckoLib 4.7.3 的使用**基本符合官方 API 规范**，核心的接口实现、类继承、动画控制器注册、资源文件格式均正确。存在 2 个功能性问题、3 个 API 废弃方法使用、以及若干可利用官方内置功能替代自定义实现的机会。

---

## 二、逐项合规性检查

### 1. GeoEntity 接口实现 — StellaEvokerEntity.java

| 检查项 | 官方要求 | 项目实现 | 合规 |
|--------|---------|---------|------|
| 接口声明 | `implements GeoEntity` | `implements GeoEntity` | **通过** |
| 实例缓存 | `GeckoLibUtil.createInstanceCache(this)` | 字段 `geoCache`，正确创建 | **通过** |
| `getAnimatableInstanceCache()` | 返回实例缓存 | 返回 `geoCache` | **通过** |
| `registerControllers()` | 接收 `ControllerRegistrar`，注册控制器 | 注册 `attack_controller` 和 `idle_controller` | **通过** |
| `getTick(Object)` | 返回动画时间轴 | 返回 `this.tickCount` | **通过**（见备注） |
| 控制器优先级 | 按注册顺序处理，后注册的覆盖先注册的 | attack_controller 先注册（优先级高），idle_controller 后注册 | **注意** |

**备注 — `getTick()` 冗余覆盖：** `GeoEntity` 接口已提供默认实现 `default double getTick(Object entity) { return ((Entity)entity).tickCount; }`，项目中的覆盖与默认实现完全相同，属于冗余代码。不影响功能，但删除可简化代码。

**注意 — 控制器注册顺序：** 官方文档明确指出"控制器按注册顺序处理，后注册的控制器动画覆盖先注册的"。项目将 `attack_controller` 先注册、`idle_controller` 后注册。这意味着当 `idle_controller` 返回 `CONTINUE` 时，其动画会**覆盖** `attack_controller` 的动画。项目中 `attack_controller` 在无攻击动画时返回 `STOP`（让出控制权），而 `idle_controller` 始终返回 `CONTINUE`（接管动画），因此实际行为是正确的——但这种正确性依赖于 `STOP` 不产生动画帧的事实，逻辑较为脆弱。如果未来修改控制器逻辑，注册顺序可能成为隐患。

---

### 2. GeoModel 实现 — StellaEvokerModel.java

| 检查项 | 官方要求 | 项目实现 | 合规 |
|--------|---------|---------|------|
| 类继承 | `extends GeoModel<T>` | `extends GeoModel<StellaEvokerEntity>` | **通过** |
| `getModelResource(T)` | 返回 .geo.json 路径 | 返回 `geo/stella_evoker.geo.json` | **通过**（已废弃） |
| `getTextureResource(T)` | 返回纹理路径 | 返回 `textures/entity/stella_evoker.png` | **通过**（已废弃） |
| `getAnimationResource(T)` | 返回 .animation.json 路径 | 返回 `animations/stella_evoker.animation.json` | **通过** |
| ResourceLocation 构造 | `fromNamespaceAndPath()` (1.20.6+) | 使用 `ResourceLocation.fromNamespaceAndPath()` | **通过** |
| 泛型参数 | `<T extends GeoAnimatable>` | `<StellaEvokerEntity>` (实现了 GeoEntity) | **通过** |

**废弃方法使用：** 项目重写的三个方法（`getModelResource(T)`、`getTextureResource(T)`、`getAnimationResource(T)`）均为 GeckoLib 4.5+ 中标记为 `@Deprecated` 的单参数版本。官方推荐使用新的双参数版本：

```java
// 新 API（推荐）
public ResourceLocation getModelResource(T animatable, @Nullable GeoRenderer<T> renderer)
public ResourceLocation getTextureResource(T animatable, @Nullable GeoRenderer<T> renderer)
```

双参数版本默认委托给单参数版本，因此当前代码**功能完全正常**。但若未来 GeckoLib 移除废弃方法，代码将编译失败。这是低风险的维护性建议。

---

### 3. GeoEntityRenderer 实现 — StellaEvokerRenderer.java

| 检查项 | 官方要求 | 项目实现 | 合规 |
|--------|---------|---------|------|
| 类继承 | `extends GeoEntityRenderer<T>` | `extends GeoEntityRenderer<StellaEvokerEntity>` | **通过** |
| 构造函数 | `super(context, GeoModel<T>)` | `super(context, new StellaEvokerModel())` | **通过** |
| 渲染层注册 | `addRenderLayer(GeoRenderLayer)` | 注册 AstralGlowLayer 和 VoidHalberdLayer | **通过** |
| GeoRenderLayer 继承 | `extends GeoRenderLayer<T>` | 两个内部类均正确继承 | **通过** |
| `render()` 覆盖签名 | `(PoseStack, T, BakedGeoModel, RenderType, MultiBufferSource, VertexConsumer, float, int, int)` | 签名完全匹配 | **通过** |
| `actuallyRender()` 调用 | 11 个参数 | AstralGlowLayer 中正确调用 | **通过** |

---

### 4. AnimationController 使用

| 检查项 | 官方要求 | 项目实现 | 合规 |
|--------|---------|---------|------|
| 构造函数 | `(T, String name, int transitionTicks, AnimationStateHandler)` | 两个控制器均使用此签名 | **通过** |
| PlayState 返回 | `CONTINUE` / `STOP` | 正确使用两个枚举值 | **通过** |
| `setAnimation(RawAnimation)` | 通过 `state.getController()` 调用 | 所有路径均正确调用 | **通过** |
| 过渡时间设置 | 构造函数第 3 参数或 `transitionLength()` | attack: 0（硬切），idle: 10（平滑） | **通过** |
| 动态过渡时间 | `state.getController().transitionLength(int)` | idle_controller 在死亡时动态设为 0 | **通过** |

---

### 5. RawAnimation 定义

| 定义 | 方法 | 对应 JSON loop 字段 | 合规 |
|------|------|---------------------|------|
| `IDLE_PHASE1_ANIM` | `thenLoop()` | `loop: true` | **通过** |
| `IDLE_PHASE2_ANIM` | `thenLoop()` | `loop: true` | **通过** |
| `DEATH_ANIM` | `thenPlay()` | `loop: false` | **问题** |
| `SLASH_ANIM` | `thenPlay()` | `loop: false` | **通过** |
| `THRUST_ANIM` | `thenPlay()` | `loop: false` | **通过** |
| `BACKSTAB_ANIM` | `thenPlay()` | `loop: false` | **通过** |
| `EXECUTION_SLAM_ANIM` | `thenPlay()` | `loop: false` | **通过** |
| `PHASE_TRANSITION_ANIM` | `thenPlay()` | `loop: false` | **通过** |

**功能问题 — 死亡动画未保持在最后一帧：** `DEATH_ANIM` 使用 `thenPlay()` 配合控制器返回 `PlayState.STOP`。根据 GeckoLib 的 `Animation.LoopType` 语义，`thenPlay()` 使用 `DEFAULT` 循环类型（即 JSON 中定义的 `loop: false`），动画播放完毕后控制器被 `STOP`。此时 GeckoLib 会在 `getBoneResetTime()`（默认 5 tick）后将骨骼姿态重置为默认，导致死亡演出的最终姿态（跪地、缩小）被还原。

官方推荐使用 `thenPlayAndHold()` 来保持在动画最后一帧：

```java
// 当前实现：动画播完后 5 tick 内骨骼回默认姿态
private static final RawAnimation DEATH_ANIM = RawAnimation.begin()
        .thenPlay("stella_evoker_death");

// 推荐实现：永久保持在最后一帧
private static final RawAnimation DEATH_ANIM = RawAnimation.begin()
        .thenPlayAndHold("stella_evoker_death");
```

`thenPlayAndHold()` 内部使用 `LoopType.HOLD_ON_LAST_FRAME`，会让骨骼永远停留在动画结束时的位置/旋转/缩放，这正是死亡演出需要的效果。当前代码依赖 `StellaDyingStateMachine` 在动画结束后立刻移除实体来规避此问题，但如果死亡演出时长或时序有任何调整，骨骼复位可能导致视觉闪烁。

---

### 6. 动画 JSON 格式 — stella_evoker.animation.json

| 检查项 | 官方要求 | 项目实现 | 合规 |
|--------|---------|---------|------|
| `format_version` | `"1.8.0"` | `"1.8.0"` | **通过** |
| 动画命名 | 任意字符串（推荐 `animation.<ns>.<name>`） | `stella_evoker_idle_phase1` 等 | **通过** |
| `loop` 字段 | `true` / `false` / `"hold_on_last_frame"` | 正确使用 `true`/`false` | **通过** |
| `animation_length` | 秒数 | 各动画均有明确时长 | **通过** |
| 骨骼引用 | 必须匹配 geo.json 中的骨骼名 | 全部匹配（body, head, right_arm, left_arm, right_leg, left_leg） | **通过** |
| 关键帧格式 | `"时间": [x, y, z]` | 全部使用标准格式 | **通过** |
| 动画数量 | 无限制 | 8 个动画 | **通过** |

**内容问题 — `stella_evoker_idle_phase2` 动画数据为空：** 该动画定义了 6 个骨骼的关键帧，但所有 `position` 和 `rotation` 值在全部时间点都是 `[0, 0, 0]`。这意味着该动画实际上是一个"静止"动画，没有任何可见运动。如果这是设计意图（二阶段站立静止），那么定义关键帧没有意义；如果是遗漏，需要补充实际的动画数据（头部扫视、重心交替等，代码注释中提到了这些效果但动画文件中没有体现）。

---

### 7. 模型 JSON 格式 — stella_evoker.geo.json

| 检查项 | 官方要求 | 项目实现 | 合规 |
|--------|---------|---------|------|
| `format_version` | `"1.12.0"` 或 `"1.16.0"` | `"1.12.0"` | **通过** |
| `minecraft:geometry` | 数组结构 | 正确 | **通过** |
| `description` | 包含 identifier、texture 尺寸、可见边界 | 全部定义 | **通过** |
| `texture_width/height` | 纹理尺寸 | `64 x 64` | **通过** |
| 骨骼层级 | 树状父子关系 | root → body → head/arms, root → legs | **通过** |
| `cubes` | `origin`, `size`, `uv` 必需 | 所有 cube 均完整 | **通过** |
| `mirror` | 镜像 UV | left_arm 和 left_leg 正确使用 | **通过** |
| `visible_bounds` | 渲染边界框 | `2 x 3`，偏移 `[0, 1.5, 0]` | **通过** |

---

### 8. 构建配置与依赖

| 检查项 | 官方要求 | 项目实现 | 合规 |
|--------|---------|---------|------|
| Maven 仓库 | Cloudsmith GeckoLib Maven | `dl.cloudsmith.io/public/geckolib3/geckolib/maven/` | **通过** |
| 依赖坐标 | `software.bernie.geckolib:geckolib-neoforge-1.21.1:<version>` | 坐标正确 | **通过** |
| 版本号 | 1.21.1 可用版本 | `4.7.3` | **通过**（见备注） |
| `neoforge.mods.toml` | 声明 geckolib 依赖 | `modId = "geckolib"` (required) | **通过** |

**备注 — 版本滞后：** 注释称"4.7.6 不存在于 Maven 仓库，4.7.3 是当前最新可用版本"。根据研究，GeckoLib 1.21.1 分支的最新版本已达约 4.8.5。4.7.3 可以正常工作，但可能缺少后续修复和优化。建议检查 Cloudsmith Maven 仓库确认可用版本列表。

---

## 三、可利用的官方内置功能

GeckoLib 4.7.3 提供了多个内置渲染层，项目当前使用自定义实现，可以评估替换的可行性：

### 3.1 AutoGlowingGeoLayer 替代 AstralGlowLayer

GeckoLib 内置 `AutoGlowingGeoLayer` 支持发光纹理渲染，要求提供一张 `_glowing` 后缀的发光遮罩纹理。项目当前自定义 `AstralGlowLayer` 使用 `RenderType.energySwirl()` 实现流动能量效果，这是**合理的自定义需求**——内置的 AutoGlowing 只支持静态发光，不支持能量流动动画。因此当前自定义实现是正当的。

### 3.2 ItemInHandGeoLayer 替代 VoidHalberdLayer

GeckoLib 内置 `ItemInHandGeoLayer` 可在骨骼位置渲染手持物品，要求在 geo.json 中定义名为 `"RightHandItem"` 或 `"LeftHandItem"` 的不可见骨骼作为挂载点。项目当前使用硬编码变换参数定位长戟，这意味着：
- 修改模型几何后需要手动重新调整 `poseStack.translate(0.35, 1.1, -0.4)` 等参数
- 变换参数与骨骼位置紧耦合

使用 `ItemInHandGeoLayer` 可以让骨骼位置驱动物品挂载，更加健壮。但这需要修改 `.geo.json` 添加定位骨骼。

### 3.3 DefaultedEntityGeoModel 替代 StellaEvokerModel

GeckoLib 提供 `DefaultedEntityGeoModel` 可以根据实体注册名自动生成资源路径，消除手动指定三个 `ResourceLocation` 的需要。但项目的文件路径不完全符合默认约定（动画文件在 `animations/` 而非 `animations/entity/`），因此当前自定义 `GeoModel` 是合理的。

---

## 四、问题汇总

### 功能性问题（2 个）

| 编号 | 严重度 | 文件 | 描述 |
|------|--------|------|------|
| G1 | **中** | StellaEvokerEntity.java | 死亡动画使用 `thenPlay()` + `PlayState.STOP`，而非 `thenPlayAndHold()`。骨骼会在 `getBoneResetTime()`（5 tick）后复位到默认姿态。当前依赖死亡演出状态机在动画结束后立即移除实体来规避，但存在视觉闪烁风险。 |
| G2 | **中** | stella_evoker.animation.json | `stella_evoker_idle_phase2` 动画的所有骨骼关键帧值均为 `[0,0,0]`，不产生任何可见运动。代码注释描述了"头部扫视、重心交替"等效果，但动画文件中未体现。 |

### API 废弃方法使用（1 个）

| 编号 | 严重度 | 文件 | 描述 |
|------|--------|------|------|
| G3 | **低** | StellaEvokerModel.java | 重写的 `getModelResource(T)`、`getTextureResource(T)` 是 `@Deprecated` 的单参数版本。推荐使用 `(T, @Nullable GeoRenderer<T>)` 双参数版本。当前功能正常，但未来 GeckoLib 移除废弃方法时将编译失败。 |

### 可改进项（3 个）

| 编号 | 严重度 | 文件 | 描述 |
|------|--------|------|------|
| G4 | **建议** | StellaEvokerEntity.java | `getTick(Object)` 覆盖与 `GeoEntity` 接口默认实现完全相同，属于冗余代码，可删除。 |
| G5 | **建议** | StellaEvokerRenderer.java | `VoidHalberdLayer` 使用硬编码变换定位物品。可改用 GeckoLib 内置 `ItemInHandGeoLayer` + 骨骼定位点，提高模型修改时的健壮性。 |
| G6 | **建议** | build.gradle.kts | GeckoLib 版本 4.7.3 可能不是 1.21.1 分支的最新版本（已知有 4.8.x）。建议检查是否有更新可用。 |

---

## 五、合规性总结矩阵

| 审查维度 | 状态 | 说明 |
|----------|------|------|
| GeoEntity 接口实现 | **合规** | 所有必需方法正确实现 |
| GeoModel 类继承 | **合规**（有废弃警告） | 功能正常，但使用了 @Deprecated 方法签名 |
| GeoEntityRenderer 继承 | **合规** | 构造函数、渲染层注册均正确 |
| GeoRenderLayer 继承 | **合规** | 覆盖签名正确，render 参数完整 |
| AnimationController 注册 | **合规** | 控制器创建、状态处理、过渡时间均正确 |
| RawAnimation 构建器 | **基本合规**（1 个问题） | 死亡动画应使用 `thenPlayAndHold()` |
| 动画 JSON 格式 | **基本合规**（1 个数据问题） | 格式正确，但 idle_phase2 内容为空 |
| 模型 JSON 格式 | **合规** | 格式、骨骼层级、UV 定义均正确 |
| 构建配置 | **合规** | Maven 仓库、坐标、mod 依赖均正确 |
| 控制器注册顺序 | **可工作但脆弱** | 依赖 STOP 不产生帧的隐含行为 |

**总体评价：** 项目对 GeckoLib 的使用在架构层面是正确且规范的。核心的 `GeoEntity → GeoModel → GeoEntityRenderer → AnimationController` 管线完整无误，资源文件格式符合 Bedrock Edition 标准。主要问题集中在死亡动画的 `thenPlay` vs `thenPlayAndHold` 选择，以及 idle_phase2 动画数据为空这两个功能性缺陷上。
