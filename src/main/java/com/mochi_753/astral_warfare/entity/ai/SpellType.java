package com.mochi_753.astral_warfare.entity.ai;

import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.function.BiConsumer;

// 法术类型枚举 - 数据驱动的法术定义
// 每个法术包含：法力消耗、冷却时间、施法时长、执行器
// 使用策略模式将法术施放逻辑从 SpellCastGoal 的 else-if 链迁移至枚举自身
// 一阶段共 6 个常规法术（星门涌动已改为血量触发技能，不再参与轮换）：
//   1. STARFALL_MATRIX - 星陨矩阵：在玩家头顶召唤高爆星石
//   2. ASTRAL_BEAM - 星界发散光束：圆锥形宽广光束扫过地面
//   3. NIGHTFALL_SINGULARITY - 夜幕黑洞：引力触点吸引玩家
//   4. FATE_LINK - 星命锁链：星光锁链逼迫玩家跑位
//   5. STAR_RAIL_CUT - 星轨切割：地面星轨预警，2秒后爆发高能激光
//   6. TELEKINETIC_THROW - 念力投掷：抓起充能傀儡砸向玩家
// 【Phase 27】星轨迷宫已从法术轮换池移除，改为血量 80% 一次性触发
public enum SpellType {

    // 星陨矩阵：在玩家头顶召唤高爆星石
    STARFALL_MATRIX(10, 100, 30, SpellCastGoal::castStarfallMatrix),

    // 星界发散光束：圆锥形宽广光束扫过地面，持续 3 秒
    // 每秒对范围内玩家造成 4 点星空伤害，每秒消耗 5 点法力
    ASTRAL_BEAM(15, 200, 60, (goal, level) -> {
        // 光束在 tick 中持续处理，execute 阶段无需额外操作
    }),

    // 夜幕黑洞：在玩家附近生成引力触点，吸引 8 格内玩家
    // 中心点玩家被禁锢并受到持续窒息伤害
    NIGHTFALL_SINGULARITY(15, 300, 80, (goal, level) -> {
        // 黑洞在 start 中生成，tick 中持续，execute 阶段无需额外操作
    }),

    // 星命锁链：向玩家发射星光锁链
    // 3 秒内玩家若未能跑出 12 格之外扯断锁链，受到 20 点斩杀伤害
    FATE_LINK(12, 400, 60, SpellCastGoal::executeFateLinkDamage),

    // 星轨切割：地面星轨预警线，2秒后沿预警线爆发高能激光
    // 蓝图 A.5：地面星轨预警，2秒后爆发高能激光，消耗 8 点法力
    STAR_RAIL_CUT(8, 200, 40, SpellCastGoal::executeStarRailCut),

    // 念力投掷：BOSS 抓起充能完毕待命的傀儡砸向玩家
    // 蓝图 B.7：落地星尘爆炸，消耗 5 点法力
    TELEKINETIC_THROW(5, 300, 20, SpellCastGoal::executeTelekineticThrow),

    // 【Phase 27】星轨迷宫：已从法术轮换池移除，仅由血量 80% 强制触发
    // 不参与 pickRandom() 随机选取，由 StellaEvokerEntity.tick() 调用 forceCastSpell 触发
    STAR_TRACK_MAZE(12, 300, 120, SpellCastGoal::executeStarTrackMaze);

    public final int manaCost;
    public final int cooldownTicks;
    public final int castDuration;
    // 策略模式：法术执行器，将具体施放逻辑委托给 SpellCastGoal 的静态方法
    public final BiConsumer<SpellCastGoal, ServerLevel> executor;

    SpellType(int manaCost, int cooldownTicks, int castDuration,
              BiConsumer<SpellCastGoal, ServerLevel> executor) {
        this.manaCost = manaCost;
        this.cooldownTicks = cooldownTicks;
        this.castDuration = castDuration;
        this.executor = executor;
    }

    // 随机选取一个当前可释放的法术（法力充足且冷却完毕）
    // 【Phase 27】排除 STAR_TRACK_MAZE（已改为血量触发，不参与随机轮换）
    public static SpellType pickRandom(StellaEvokerEntity evoker) {
        List<SpellType> available = List.of(values()).stream()
                .filter(spell -> spell != STAR_TRACK_MAZE)
                .filter(spell -> evoker.getManaData().getCurrentMana() >= spell.manaCost)
                .filter(spell -> evoker.getSpellCooldown(spell) <= 0)
                .toList();

        if (available.isEmpty()) {
            return null;
        }
        return available.get(evoker.getRandom().nextInt(available.size()));
    }
}
