# Astral Warfare 代码审查修复计划

> **目标**: 修复审查报告中的全部 40 个问题（10 Critical + 16 Important + 14 Minor）
> **架构**: NeoForge 1.21.1 模组，59 个 Java 源文件
> **执行方式**: Subagent-Driven Development，每任务独立实施 + 两级审查

---

## Task 1: 资源文件修复 [C-01, I-14, I-15, M-14]
- 修复生物群系标签命名空间 neoforge: → c:
- 添加 void_fissure 翻译键到 en_us/zh_cn
- 为 starcore_golem 添加战利品池
- 为 astral_crystal/nightfall_singularity/void_fissure 添加空战利品表

## Task 2: 侧安全修复 [C-02, I-05]
- 创建 ParticleIds.java（侧中立常量类）
- 将所有 StellaParticles.ID_* 引用替换为 ParticleIds
- 修复 ModPayloads 中的客户端类导入

## Task 3: Boss 实体 NBT + 状态 [C-03, I-02, I-03]
- 添加 IsWeakened 到 NBT save
- 修复 syncManaToPlayer 不修改共享脏追踪
- 替换 getAvailableGoals().clear() 为安全 API

## Task 4: Boss 技能系统 [C-04, I-10, M-08]
- 添加 normalize() 零距离保护
- 修复 findSafeTeleportPosition 高度检查 + 危险方块
- 更新过时注释

## Task 5: 粒子性能优化 [C-05, I-01, M-03]
- 减少 NightfallSingularity 粒子数量/频率
- 迷宫包节流
- 星轨迷宫粒子减量

## Task 6: 客户端渲染 [C-06, C-07, C-08, I-08, I-09, M-04~M-07]
- SingularityPostProcessor volatile
- crossfadeToPhase 音频泄漏
- StarcoreGolemModel 缺失部件
- SingularityRenderHandler 最近实体
- Throwable → Exception
- VoidSigilRenderer 分配优化
- endBatch 指定 RenderType
- AstralCrystal 双重浮动修复

## Task 7: 内存泄漏 + 效果系统 [C-09, I-06, M-12]
- VoidEntrapmentEffect 效果移除清理
- 跳跃包速率限制
- ParticleEmitter 日志级别提升

## Task 8: 网络包 [C-10, I-05]
- ClientboundParticleBatchPacket 缓冲区修复
- ModPayloads 客户端隔离

## Task 9: AI Goal 系统 [I-04, I-11, I-12]
- ManaData 原子操作
- StellaFlyingMoveControl 速度增量
- 移除 COOLDOWN 死代码

## Task 10: 物品 + 结构 + 构建 [I-13, I-16, M-10, M-13]
- NocturnalAstrolabeItem 条件水晶生成
- Curios 改为 optional
- 移除弃用常量
- mcp.json → .gitignore

## Task 11: 杂项清理 [M-01, M-02, M-09, M-11]
- isWalking 封装
- StarcoreGolem hurt() 死代码
- SpellType.pickRandom 优化
- sounds.json subtitle 字段
