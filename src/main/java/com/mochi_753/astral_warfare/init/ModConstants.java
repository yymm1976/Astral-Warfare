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
    // 修复：从 60° 增大到 90°（半角 45°），适配 BOSS 飞行高度 6 格
    // 原因：BOSS 在 Y+6 处俯视地面，半角 30° 时锥形在地面覆盖半径仅 3.5 格
    // 增大到半角 45° 后覆盖半径扩大到 6 格，配合 lookAt 低头可稳定命中
    public static final double BEAM_CONE_ANGLE = 90.0;
    // 星界发散光束：射程（格）
    // 从 12.0 增大到 18.0，大幅扩大覆盖范围
    public static final double BEAM_RANGE = 18.0;
    // 星界发散光束：每秒额外法力消耗
    public static final int BEAM_EXTRA_MANA_PER_SEC = 5;
    // 星命锁链：最大判定距离（格）
    // 从 12.0 增大到 18.0，扩大逃生窗口的同时增加压迫感
    public static final double FATE_LINK_MAX_DIST = 18.0;
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
    // 从 3.0F 增大到 5.0F，扩大陨石爆炸范围
    public static final float STARFALL_RADIUS = 5.0F;
    // 星门涌动：脉冲伤害
    // 修复：从5.0F提升到8.0F（再加强50%），星门涌动是75%血量触发的大招
    public static final float SURGE_PULSE_DAMAGE = 8.0F;
    // 星门涌动：脉冲范围
    // 从 5.0 增大到 8.0，扩大星门涌动覆盖范围
    public static final double SURGE_PULSE_RADIUS = 8.0;

    // 星轨切割：激光伤害
    // 修复：从16.0F提升到24.0F（再加强50%），配合magic伤害源绕过盔甲
    public static final float STAR_RAIL_CUT_DAMAGE = 24.0F;
    // 星轨切割：激光宽度（格）
    public static final double STAR_RAIL_CUT_WIDTH = 1.5;
    // 星轨切割：激光长度（格）
    // 从 20.0 增大到 30.0，延长激光切割线
    public static final double STAR_RAIL_CUT_LENGTH = 30.0;

    // 念力投掷：爆炸伤害
    // 修复：从18.0F提升到27.0F（再加强50%），配合magic伤害源绕过盔甲
    public static final float TELEKINETIC_THROW_DAMAGE = 27.0F;
    // 念力投掷：爆炸半径
    // 从 3.0F 增大到 5.0F，扩大傀儡爆炸范围
    public static final float TELEKINETIC_THROW_RADIUS = 5.0F;
    // 念力投掷：搜索充能傀儡范围
    public static final double TELEKINETIC_THROW_GOLEM_RANGE = 15.0;

    // ==================== 绝望处决 ====================

    // 处决连招冷却（tick）
    public static final int EXECUTION_COOLDOWN_TICKS = 200;
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
    // 传送高度（格）
    public static final double EXECUTION_TELEPORT_HEIGHT = 4.0;
    // 蓄力时间（tick）
    public static final int EXECUTION_CHARGE_TICKS = 15;
    // 处决主目标伤害
    public static final float EXECUTION_DAMAGE = 30.0F;
    // 砸地溅射范围
    // 从 5.0F 增大到 8.0F，扩大砸地冲击波范围
    public static final float EXECUTION_SLAM_RADIUS = 8.0F;
    // 砸地溅射伤害
    public static final float EXECUTION_SLAM_SPLASH_DAMAGE = 18.0F;
}
