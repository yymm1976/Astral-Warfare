package com.mochi_753.astral_warfare.init;

import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// 模组配置系统 - 服务端配置
// 所有 BOSS 战斗相关数值通过 astral_warfare-server.toml 暴露给服主调整
// 配置热重载通过 AstralWarfare 主类中 modEventBus.addListener 手动注册
// 不使用 @EventBusSubscriber(bus = Bus.MOD)，因为 Bus.MOD 已被标记 @removal
public class ModConfig {

    // ForgeConfigSpec 配置构建器
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ==================== BOSS 属性配置 ====================
    public static final ModConfigSpec.DoubleValue BASE_HP;
    public static final ModConfigSpec.DoubleValue HP_PER_EXTRA_PLAYER;
    public static final ModConfigSpec.IntValue PISTON_PROTECTION_RADIUS;

    // ==================== 法术伤害配置 ====================
    public static final ModConfigSpec.DoubleValue EXECUTION_DAMAGE;
    public static final ModConfigSpec.DoubleValue CHAIN_BREAK_DAMAGE;
    public static final ModConfigSpec.DoubleValue BLACKHOLE_STRENGTH;
    public static final ModConfigSpec.DoubleValue SINGULARITY_TICK_DAMAGE;

    // ==================== 近战伤害配置 ====================
    public static final ModConfigSpec.DoubleValue SLASH_DAMAGE;
    public static final ModConfigSpec.DoubleValue THRUST_DAMAGE;
    public static final ModConfigSpec.DoubleValue BACKSTAB_DAMAGE;
    public static final ModConfigSpec.DoubleValue PULL_PUNCH_DAMAGE;

    // ==================== 冷却时间配置 ====================
    public static final ModConfigSpec.IntValue STAR_GATE_INTERVAL_TICKS;

    static {
        // BOSS 属性分组
        BUILDER.push("boss");
        BASE_HP = BUILDER
                .comment("BOSS 基础血量", "该值仅对新召唤的 BOSS 生效，已存在的 BOSS 将保留原血量直到死亡")
                .defineInRange("base_hp", 1000.0, 1.0, 100000.0);
        HP_PER_EXTRA_PLAYER = BUILDER
                .comment("每多一名玩家增加的血量", "该值仅对新召唤的 BOSS 生效，已存在的 BOSS 将保留原血量直到死亡")
                .defineInRange("hp_per_extra_player", 400.0, 0.0, 100000.0);
        PISTON_PROTECTION_RADIUS = BUILDER
                .comment("活塞推离保护阈值（格），BOSS 偏离祭坛中心超过此距离时强制瞬移回中心")
                .defineInRange("piston_protection_radius", 24, 1, 128);
        BUILDER.pop();

        // 法术伤害分组
        BUILDER.push("spells");
        EXECUTION_DAMAGE = BUILDER
                .comment("二阶段处决砸地的主目标伤害")
                .defineInRange("execution_damage", 80.0, 0.0, 10000.0);
        CHAIN_BREAK_DAMAGE = BUILDER
                .comment("星命锁链断裂时对周围目标的溅射伤害")
                .defineInRange("chain_break_damage", 40.0, 0.0, 10000.0);
        BLACKHOLE_STRENGTH = BUILDER
                .comment("夜幕黑洞引力强度")
                .defineInRange("blackhole_strength", 2.0, 0.0, 100.0);
        SINGULARITY_TICK_DAMAGE = BUILDER
                .comment("黑洞中心区域每秒窒息伤害")
                .defineInRange("singularity_tick_damage", 3.0, 0.0, 1000.0);
        BUILDER.pop();

        // 近战伤害分组
        BUILDER.push("melee");
        SLASH_DAMAGE = BUILDER
                .comment("二阶段弦斩伤害")
                .defineInRange("slash_damage", 16.0, 0.0, 10000.0);
        THRUST_DAMAGE = BUILDER
                .comment("二阶段突进伤害")
                .defineInRange("thrust_damage", 12.0, 0.0, 10000.0);
        BACKSTAB_DAMAGE = BUILDER
                .comment("二阶段背刺伤害")
                .defineInRange("backstab_damage", 28.0, 0.0, 10000.0);
        PULL_PUNCH_DAMAGE = BUILDER
                .comment("二阶段拉人重拳伤害")
                .defineInRange("pull_punch_damage", 30.0, 0.0, 10000.0);
        BUILDER.pop();

        // 冷却时间分组
        BUILDER.push("cooldowns");
        STAR_GATE_INTERVAL_TICKS = BUILDER
                .comment("星门涌动冷却时间（tick，20 tick = 1 秒）")
                .defineInRange("star_gate_interval_ticks", 1200, 20, 72000);
        BUILDER.pop();
    }

    // 最终配置规格对象，由主类注册到 NeoForge 配置系统
    public static final ModConfigSpec SPEC = BUILDER.build();

    // 配置热重载监听：当服主修改 toml 文件后自动重新加载数值
    // 无需重启服务器即可生效
    // 通过 AstralWarfare 主类中 modEventBus.addListener(ModConfig::onConfigReload) 手动注册
    // 不使用 @SubscribeEvent + @EventBusSubscriber，因为 Bus.MOD 已被标记 @removal
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() == Type.SERVER) {
            // 配置已自动更新到对应的 ConfigValue 中
        }
    }
}
