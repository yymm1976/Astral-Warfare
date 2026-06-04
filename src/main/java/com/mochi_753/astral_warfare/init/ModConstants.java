package com.mochi_753.astral_warfare.init;

// 全局常量集中管理类
// 将散落在各处的魔法数值统一到此，便于后续参数调整时一处修改、全局生效
// 仅收纳跨文件共享或高频调参的常量，类内部私有的局部常量保留原处
public final class ModConstants {

    private ModConstants() {}

    // ==================== BOSS 基础属性 ====================

    // BOSS 基础血量和每增加一个玩家额外增加的生命值
    // 已迁移到 ModConfig，由服主通过 astral_warfare-server.toml 配置
    // 默认值：BASE_HP=1000, HP_PER_EXTRA_PLAYER=400
    // 统计玩家数量的范围（与脱战检测半径一致）
    public static final double PLAYER_COUNT_RADIUS = 30.0;

    // ==================== 战斗区域限制 ====================

    // 脱战检测半径：此范围内无玩家时 BOSS 消失
    public static final double ANCHOR_CHECK_RADIUS = 30.0;
    // 脱战检测间隔（tick）
    public static final int ANCHOR_CHECK_INTERVAL = 20;

    // ==================== 阶段转换 ====================

    // 转阶段演出持续时间（tick）
    public static final int TRANSITION_DURATION_TICKS = 60;
    // 转阶段升空目标高度（格）
    public static final double TRANSITION_TARGET_HEIGHT = 8.0;
    // 死亡演出持续时间（tick）
    public static final int DYING_DURATION_TICKS = 100;
    // 二阶段移动速度倍率
    public static final double PHASE2_SPEED_MULTIPLIER = 1.3;

    // ==================== 法力系统 ====================

    // 默认最大法力值
    public static final int DEFAULT_MAX_MANA = 100;
    // 法力枯竭坠落后恢复的法力量
    public static final int MANA_RECOVER_AMOUNT = 100;
    // 水晶法力恢复范围
    public static final double CRYSTAL_MANA_RECOVER_RADIUS = 15.0;
    // 水晶每次恢复的法力量
    public static final int CRYSTAL_MANA_RECOVER_AMOUNT = 2;
    // 水晶法力恢复间隔（tick）
    public static final int CRYSTAL_MANA_RECOVER_INTERVAL = 40;
    // 被动法力恢复：每 tick 恢复量（1/20 格/tick = 1 点/秒）
    public static final int PASSIVE_MANA_REGEN_PER_TICK = 1;
    // 被动法力恢复间隔（tick，每秒恢复一次）
    public static final int PASSIVE_MANA_REGEN_INTERVAL = 20;
    // 法力枯竭坠落阈值：法力低于此值且无法施放任何法术时触发坠落
    // 解决"法力1-9死区"问题：法力不为0但低于最便宜法术消耗时，BOSS既不能施法也不会坠落
    public static final int MANA_EXHAUSTION_THRESHOLD = 10;

    // ==================== 虚弱与冲击 ====================

    // 虚弱状态持续时间（tick）
    public static final int WEAKENED_DURATION_TICKS = 300;
    // 虚弱状态受伤倍率
    public static final float WEAKENED_DAMAGE_MULTIPLIER = 1.5F;
    // 坠落冲击范围
    public static final float IMPACT_RADIUS = 5.0F;
    // 坠落冲击伤害
    public static final float IMPACT_DAMAGE = 8.0F;

    // ==================== 傀儡 ====================

    // 傀儡充能延迟（tick）= 5 秒反应窗口
    public static final int GOLEM_CHARGE_DELAY_TICKS = 100;

    // ==================== 法术参数 ====================

    // 星界发散光束：每秒伤害间隔（10 tick = 0.5秒）
    // 修复：从20tick缩短到10tick，适配旋转扫射光束
    // 原因：光束旋转扫过时，玩家在锥形范围内的时间窗口较短
    // 缩短伤害间隔确保扫过时能稳定命中1-2次
    public static final int BEAM_DAMAGE_INTERVAL = 10;
    // 星界发散光束：每次伤害值
    // 修复：从8.0F提升到12.0F（再加强50%），配合magic伤害源绕过盔甲
    public static final float BEAM_DAMAGE = 12.0F;
    // 星界发散光束：锥形角度（度）
    // Phase 28：从 90° 增大到 135°，扩大光束覆盖范围
    public static final double BEAM_CONE_ANGLE = 135.0;
    // 星界发散光束：射程（格）
    // Phase 28：从 18.0 增大到 48.0，大幅扩大覆盖范围
    public static final double BEAM_RANGE = 48.0;
    // 星界发散光束：每秒额外法力消耗
    public static final int BEAM_EXTRA_MANA_PER_SEC = 5;
    // 星命锁链：最大判定距离（格）
    // Phase 28：从 18.0 增大到 48.0，扩大锁链追踪范围
    public static final double FATE_LINK_MAX_DIST = 48.0;
    // 星命锁链：斩杀伤害
    // 修复：从25.0F提升到38.0F（再加强50%），确保锁链斩杀有足够威慑力
    public static final float FATE_LINK_DAMAGE = 38.0F;
    // 星命锁链：预警切换时间点（tick）
    public static final int FATE_LINK_WARNING_THRESHOLD = 30;
    // 星陨矩阵：陨石高度（格）
    public static final int STARFALL_METEOR_HEIGHT = 30;
    // 星陨矩阵：爆炸伤害
    // 修复：从14.0F提升到21.0F（再加强50%），配合magic伤害源绕过盔甲
    public static final float STARFALL_DAMAGE = 21.0F;
    // 星陨矩阵：爆炸半径
    // Phase 28：从 5.0F 增大到 15.0F，扩大陨石爆炸范围（3×）
    public static final float STARFALL_RADIUS = 15.0F;
    // 星门涌动：脉冲伤害
    // 修复：从5.0F提升到8.0F（再加强50%），星门涌动是75%血量触发的大招
    public static final float SURGE_PULSE_DAMAGE = 8.0F;
    // 星门涌动：脉冲范围
    // Phase 28：从 8.0 增大到 24.0，扩大星门涌动覆盖范围（3×）
    public static final double SURGE_PULSE_RADIUS = 24.0;
    // 星门涌动：环特效半径
    public static final double SURGE_RING_RADIUS = 3.0;

    // 星轨切割：激光伤害
    // 修复：从16.0F提升到24.0F（再加强50%），配合magic伤害源绕过盔甲
    public static final float STAR_RAIL_CUT_DAMAGE = 24.0F;
    // 星轨切割：激光宽度（格）
    public static final double STAR_RAIL_CUT_WIDTH = 1.5;
    // 星轨切割：激光长度（格）
    // Phase 28：从 30.0 增大到 72.0，延长激光切割线（2.4×）
    public static final double STAR_RAIL_CUT_LENGTH = 72.0;

    // 念力投掷：爆炸伤害
    // 修复：从18.0F提升到27.0F（再加强50%），配合magic伤害源绕过盔甲
    public static final float TELEKINETIC_THROW_DAMAGE = 27.0F;
    // 念力投掷：爆炸半径
    // Phase 28：从 5.0F 增大到 15.0F，扩大傀儡爆炸范围（3×）
    public static final float TELEKINETIC_THROW_RADIUS = 15.0F;
    // 念力投掷：搜索充能傀儡范围
    public static final double TELEKINETIC_THROW_GOLEM_RANGE = 15.0;

    // ==================== 绝望处决 ====================

    // 处决连招冷却（tick）= 20 秒
    public static final int EXECUTION_COOLDOWN_TICKS = 400;
    // 处决成功后最小间隔（tick）= 30 秒，与 COOLDOWN 取最大值
    // 确保终结技成功后至少 30 秒才能再次触发
    public static final int EXECUTION_MIN_INTERVAL_TICKS = 600;
    // 处决触发血量阈值：BOSS血量低于此百分比时终结技可用
    public static final float EXECUTION_TRIGGER_HEALTH_PERCENT = 0.35F;
    // 处决触发范围：玩家在此范围内才会触发
    // 从 15.0 增大到 22.0，扩大终结技触发范围
    public static final double EXECUTION_TRIGGER_RANGE = 22.0;
    // 前摇时间（tick）：终结技触发后有明显的蓄力预警
    public static final int EXECUTION_WINDUP_TICKS = 30;
    // 击飞伤害：前摇结束后击飞玩家时造成的伤害
    public static final float EXECUTION_LAUNCH_DAMAGE = 15.0F;
    // 击飞范围：前摇结束后，此范围内的玩家才会被击飞（非锁头）
    // 从 10.0 增大到 15.0，扩大击飞判定范围
    public static final double EXECUTION_LAUNCH_RANGE = 15.0;
    // 传送高度（格）：BOSS 瞬移到玩家正上方的高度
    // 从 4.0 降低到 3.0，避免 BOSS 传送过高导致下刺后弹飞脱离仇恨范围
    public static final double EXECUTION_TELEPORT_HEIGHT = 3.0;
    // 蓄力时间（tick）
    public static final int EXECUTION_CHARGE_TICKS = 15;
    // 处决主目标伤害（已迁移至 ModConfig.EXECUTION_DAMAGE，此常量仅作参考，代码不再引用）
    @Deprecated
    public static final float EXECUTION_DAMAGE = 30.0F;
    // 砸地溅射范围
    // 从 5.0F 增大到 8.0F，扩大砸地冲击波范围
    public static final float EXECUTION_SLAM_RADIUS = 8.0F;
    // 砸地溅射伤害
    public static final float EXECUTION_SLAM_SPLASH_DAMAGE = 18.0F;

    // ==================== 夜幕黑洞 ====================

    // 奇点吸引力公式的基础强度系数
    // 公式：pullStrength = min(MAX_PULL_STRENGTH, PULL_STRENGTH_FACTOR / effectiveDistSq)
    public static final double PULL_STRENGTH_FACTOR = 2.0;

    // ==================== 星轨迷宫 ====================

    // 星轨迷宫：激活列伤害
    // Phase 28：从 12.0F 增大到 24.0F，配合更大的网格（2×）
    public static final float STAR_TRACK_MAZE_DAMAGE = 24.0F;
    // 星轨迷宫：网格大小（列数×行数）
    // Phase 28：从 7 增大到 15，扩大迷宫覆盖范围（2.1×）
    public static final int STAR_TRACK_MAZE_GRID_SIZE = 15;

    // ==================== 虚空裂隙 ====================

    // 虚空裂隙：生成数量
    public static final int VOID_FISSURE_COUNT = 3;
    // 虚空裂隙：每次伤害
    public static final float VOID_FISSURE_DAMAGE = 4.0F;
    // 虚空裂隙：生命周期（tick，300 = 15秒）
    public static final int VOID_FISSURE_LIFETIME = 300;

    // ==================== 死亡演出 ====================

    // 死亡爆炸粒子数量
    public static final int DEATH_EXPLOSION_PARTICLE_COUNT = 40;
    // 死亡爆炸粒子扩散半径
    public static final double DEATH_EXPLOSION_RADIUS = 6.0;
}
