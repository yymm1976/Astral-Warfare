# Tasks

- [x] Task 1: 降低 StellaParticles 粒子亮度与透明度
  - [x] SubTask 1.1: 降低6种粒子的颜色亮度（RGB各通道乘以0.7，保持色相不变）
  - [x] SubTask 1.2: 降低6种粒子的初始透明度（从0.85~1.0降至0.6~0.75）
  - [x] SubTask 1.3: 缩小6种粒子的初始尺寸约20%

- [x] Task 2: 精简 Phase2MeleeGoal 粒子数量
  - [x] SubTask 2.1: 弦斩蓄力（tickSlashDelay）— 余烬从2+progress*5降至2+progress*2，星芒从每3tick降至每5tick
  - [x] SubTask 2.2: 弦斩（tickSlash）— 删除DYING_EMBER烟雾层和ASTRAL_BEAM碎片层，保留核心IMPACT_WAVE+VOID_SPARK尾迹+TRANSITION_BURST高潮
  - [x] SubTask 2.3: 突进（tickThrustDash）— 路径粒子从10降至6，删除DYING_EMBER层，落点TRANSITION_BURST从5降至3
  - [x] SubTask 2.4: 突进命中（tickThrustHit）— 删除DYING_EMBER和ASTRAL_BEAM层，保留IMPACT_WAVE+STELLA_WISP
  - [x] SubTask 2.5: 背刺消失（tickBackstabVanish）— TRANSITION_BURST从12降至6，STELLA_WISP从15降至8，DYING_EMBER从8降至4
  - [x] SubTask 2.6: 背刺命中（tickBackstabStrike）— 删除DYING_EMBER和VOID_SPARK层，保留ASTRAL_BEAM轨迹+IMPACT_WAVE核心+TRANSITION_BURST闪光
  - [x] SubTask 2.7: 拉人蓄力（tickPullWindup）— 删除STELLA_WISP环绕和DYING_EMBER烟雾，保留VOID_SPARK核心；拉扯路径删除ASTRAL_BEAM和DYING_EMBER层，保留VOID_SPARK
  - [x] SubTask 2.8: 拉人重拳（tickPullStrike）— 删除DYING_EMBER/ASTRAL_BEAM/VOID_SPARK层，保留IMPACT_WAVE+TRANSITION_BURST

- [x] Task 3: 精简 DespairExecutionGoal 粒子数量
  - [x] SubTask 3.1: 前摇（tickWindup）— 螺旋粒子最大数量从3+progress*8降至2+progress*4；能量爆发从每3tick/2个降至每5tick/1个
  - [x] SubTask 3.2: 击飞（tickLaunching）— TRANSITION_BURST从25降至12，VOID_SPARK从15降至8
  - [x] SubTask 3.3: 瞬移（tickTeleporting）— 消失粒子从12降至6，出现粒子从8降至4
  - [x] SubTask 3.4: 蓄力（tickCharging）— 从8降至4，删除ASTRAL_BEAM层；DYING_EMBER从每2tick/3个降至每4tick/2个
  - [x] SubTask 3.5: 下刺拖尾（tickSlamming）— 从20降至10
  - [x] SubTask 3.6: 砸地冲击（executeSlamImpact）— 从150降至60，VOID_SPARK从80降至30，DYING_EMBER从50降至20，TRANSITION_BURST从20降至8
  - [x] SubTask 3.7: 十字裂缝线— 从每方向15降至每方向8

- [x] Task 4: 增大终结技范围参数
  - [x] SubTask 4.1: ModConstants.EXECUTION_LAUNCH_RANGE 从 5.0 增大到 10.0
  - [x] SubTask 4.2: ModConstants.EXECUTION_TRIGGER_RANGE 从 10.0 增大到 15.0
  - [x] SubTask 4.3: DespairExecutionGoal 预警圈段数从 24 增加到 32

- [x] Task 5: 构建验证
  - [x] SubTask 5.1: 运行 ./gradlew build 确认编译通过

# Task Dependencies
- Task 1 独立，可先执行
- Task 2 和 Task 3 依赖 Task 1（亮度调整后再精简数量，避免二次调整）
- Task 4 独立，可与 Task 1-3 并行
- Task 5 依赖所有前置任务完成
