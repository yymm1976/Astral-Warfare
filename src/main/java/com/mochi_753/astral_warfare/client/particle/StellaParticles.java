package com.mochi_753.astral_warfare.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleTypes;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;
import team.lodestar.lodestone.systems.particle.data.spin.SpinParticleData;
import team.lodestar.lodestone.systems.easing.Easing;
import team.lodestar.lodestone.systems.particle.world.type.LodestoneWorldParticleType;

import java.awt.Color;

// 客户端粒子特效工具类
// 封装所有 Lodestone 粒子效果的具体配置（颜色、大小、旋转、生命周期等）
// 仅供客户端环境使用，由网络包处理器调用
//
// 核心修复：使用 LodestoneParticleTypes 内置粒子（已有客户端渲染工厂）
// 替代之前自定义 ModParticleTypes（缺少 ParticleProvider.Factory 导致粒子不可见）
//
// 粒子映射关系：
//   ID_STELLA_WISP (0) → WISP_PARTICLE（柔和光点，适合悬浮/吟唱）
//   ID_VOID_SPARK (1)  → SPARKLE_PARTICLE（闪烁粒子，适合黑洞/禁锢）
//   ID_ASTRAL_BEAM (2) → SPARK_PARTICLE（火花粒子，适合光束/能量）
//   ID_IMPACT_WAVE (3) → EXTRUDING_SPARK_PARTICLE（延伸火花，适合冲击波）
//   ID_DYING_EMBER (4) → SMOKE_PARTICLE（烟雾粒子，适合余烬/消散）
//   ID_TRANSITION_BURST (5) → STAR_PARTICLE（星形粒子，适合爆发/转换）
//   ID_VOID_TWINKLE (6)    → TWINKLE_PARTICLE（微光闪烁，适合星空/信号）
//   ID_VOID_THREAD (7)     → THIN_EXTRUDING_SPARK_PARTICLE（细长延伸火花，适合激光线/轨迹）
//
// Side 安全：此类引用了 ClientLevel 等客户端专属类
// 仅在 ModPayloads 的 playToClient handler 中被调用，独立服务端不会触发类加载
// 不使用 @OnlyIn 注解（NeoForge 不推荐），通过调用链隔离保证 Side 安全
public class StellaParticles {

    // ==================== 粒子 ID 常量 ====================
    // 与 ClientboundLodestoneParticlePacket.particleTypeId 对应
    public static final int ID_STELLA_WISP = 0;
    public static final int ID_VOID_SPARK = 1;
    public static final int ID_ASTRAL_BEAM = 2;
    public static final int ID_IMPACT_WAVE = 3;
    public static final int ID_DYING_EMBER = 4;
    public static final int ID_TRANSITION_BURST = 5;
    public static final int ID_VOID_TWINKLE = 6;
    public static final int ID_VOID_THREAD = 7;

    // ==================== 颜色定义 ====================
    // 星穹紫：紫色渐变，用于 STELLA_WISP
    private static final Color STELLA_START = new Color(112, 42, 168);
    private static final Color STELLA_END = new Color(161, 112, 178);
    // 虚空黑紫：深紫+黑色，用于 VOID_SPARK
    private static final Color VOID_START = new Color(56, 7, 84);
    private static final Color VOID_END = new Color(112, 35, 140);
    // 星界蓝：亮蓝色，用于 ASTRAL_BEAM
    private static final Color ASTRAL_START = new Color(70, 126, 178);
    private static final Color ASTRAL_END = new Color(126, 161, 178);
    // 冲击橙红：橙红+紫色，用于 IMPACT_WAVE
    private static final Color IMPACT_START = new Color(178, 98, 42);
    private static final Color IMPACT_END = new Color(154, 56, 178);
    // 死亡暗红：暗红+紫色，用于 DYING_EMBER
    private static final Color EMBER_START = new Color(140, 28, 42);
    private static final Color EMBER_END = new Color(98, 14, 126);
    // 转阶段白紫：白色+紫色，用于 TRANSITION_BURST
    private static final Color TRANSITION_START = new Color(178, 178, 178);
    private static final Color TRANSITION_END = new Color(147, 91, 178);
    // 虚空微光：淡紫+暗紫，用于 VOID_TWINKLE
    private static final Color TWINKLE_START = new Color(98, 56, 140);
    private static final Color TWINKLE_END = new Color(56, 28, 84);
    // 虚空丝线：深蓝紫+暗紫，用于 VOID_THREAD
    private static final Color THREAD_START = new Color(56, 70, 140);
    private static final Color THREAD_END = new Color(84, 42, 126);

    // 星穹微光粒子：紫色渐变光点，缓慢上浮（优化版）
    // 使用 LodestoneParticleTypes.WISP_PARTICLE（柔和光点，已有客户端渲染工厂）
    // variant 参数用于颜色微调（0=默认，1=亮色变体，2=暗色变体）
    // 优化：增强颜色对比度，添加随机水平漂移，提升层次感
    public static void spawnStellaWisp(ClientLevel level, double x, double y, double z, int variant) {
        Color startColor, endColor;
        switch (variant) {
            case 1 -> { startColor = new Color(126, 56, 178); endColor = new Color(168, 126, 178); }
            case 2 -> { startColor = new Color(70, 21, 126); endColor = new Color(126, 56, 154); }
            default -> { startColor = STELLA_START; endColor = STELLA_END; }
        }
        // 随机水平漂移：营造星空漂浮的不规则感
        double driftX = (level.random.nextDouble() - 0.5) * 0.015;
        double driftZ = (level.random.nextDouble() - 0.5) * 0.015;
        WorldParticleBuilder.create(LodestoneParticleTypes.WISP_PARTICLE)
                .setColorData(ColorParticleData.create(startColor, endColor).build())
                .setScaleData(GenericParticleData.create(0.7f, 0.05f).setEasing(Easing.QUINTIC_OUT).build())
                .setTransparencyData(GenericParticleData.create(0.7f, 0f).setEasing(Easing.QUINTIC_OUT).build())
                .setLifetime(45)
                .addMotion(driftX, 0.015, driftZ)
                .spawn(level, x, y, z);
    }

    // 虚空火花粒子：深紫+品红，螺旋旋转（增强版：增大初始尺寸，提高对比度）
    // variant=1: 亮紫品红（吸积盘）
    // variant=2: 暗紫（引力透镜环）
    // variant=0: 默认（黑洞核心周围的紫粒子）
    public static void spawnVoidSpark(ClientLevel level, double x, double y, double z, int variant) {
        Color startColor, endColor;
        float spinOffset = variant * 0.5f;
        switch (variant) {
            // variant=1 吸积盘：更亮的品红，饱和度高
            case 1 -> { startColor = new Color(112, 21, 154); endColor = new Color(178, 70, 178); }
            // variant=2 外围：暗紫，像被引力拉动的粒子
            case 2 -> { startColor = new Color(35, 7, 56); endColor = new Color(98, 21, 140); }
            default -> { startColor = VOID_START; endColor = VOID_END; }
        }
        // 初始速度：营造喷射/旋转感
        double velX = (level.random.nextDouble() - 0.5) * 0.04;
        double velY = level.random.nextDouble() * 0.03;
        double velZ = (level.random.nextDouble() - 0.5) * 0.04;
        WorldParticleBuilder.create(LodestoneParticleTypes.SPARKLE_PARTICLE)
                .setColorData(ColorParticleData.create(startColor, endColor).build())
                .setScaleData(GenericParticleData.create(0.5f, 0.05f).setEasing(Easing.QUINTIC_OUT).build())
                .setTransparencyData(GenericParticleData.create(0.7f, 0.1f).setEasing(Easing.CUBIC_OUT).build())
                .setSpinData(SpinParticleData.create(0.4f, 0f).setSpinOffset(spinOffset).setEasing(Easing.QUINTIC_OUT).build())
                .setLifetime(35)
                .addMotion(velX, velY, velZ)
                .spawn(level, x, y, z);
    }

    // 星界光束粒子：亮蓝色，快速移动并带自发光（像实体光柱）
    // 使用 Lodestone 高级 API：setFullBrightLighting 让粒子不受环境光照影响，始终明亮
    public static void spawnAstralBeam(ClientLevel level, double x, double y, double z) {
        WorldParticleBuilder.create(LodestoneParticleTypes.SPARK_PARTICLE)
                .setColorData(ColorParticleData.create(ASTRAL_START, ASTRAL_END).build())
                .setScaleData(GenericParticleData.create(0.55f, 0.02f).setEasing(Easing.QUINTIC_OUT).build())
                .setTransparencyData(GenericParticleData.create(0.65f, 0.15f).setEasing(Easing.QUINTIC_OUT).build())
                .setLifetime(20)
                .setFullBrightLighting()
                .addMotion(0, 0.03, 0)
                .spawn(level, x, y, z);
    }

    // 星界光束粒子短生命周期变体：12 tick，用于星命锁链链环，快速消散不拖影
    public static void spawnAstralBeamShort(ClientLevel level, double x, double y, double z) {
        WorldParticleBuilder.create(LodestoneParticleTypes.SPARK_PARTICLE)
                .setColorData(ColorParticleData.create(ASTRAL_START, ASTRAL_END).build())
                .setScaleData(GenericParticleData.create(0.55f, 0.02f).setEasing(Easing.QUINTIC_OUT).build())
                .setTransparencyData(GenericParticleData.create(0.65f, 0.15f).setEasing(Easing.QUINTIC_OUT).build())
                .setLifetime(12)
                .setFullBrightLighting()
                .addMotion(0, 0.03, 0)
                .spawn(level, x, y, z);
    }

    // 冲击波粒子：橙红+紫色，向外扩散（增强版：更小更快更亮，像刀刃闪光）
    // variant 参数用于区分不同方向的刃光
    public static void spawnImpactWave(ClientLevel level, double x, double y, double z, int variant) {
        float angle = variant * (float) (Math.PI * 2 / 8);
        double motionX = Math.cos(angle) * 0.12;
        double motionZ = Math.sin(angle) * 0.12;
        // 增强版：更大初始尺寸，更鲜明的颜色
        WorldParticleBuilder.create(LodestoneParticleTypes.EXTRUDING_SPARK_PARTICLE)
                .setColorData(ColorParticleData.create(IMPACT_START, IMPACT_END).build())
                .setScaleData(GenericParticleData.create(0.65f, 0.08f).setEasing(Easing.QUINTIC_OUT).build())
                .setTransparencyData(GenericParticleData.create(0.7f, 0.2f).setEasing(Easing.CUBIC_OUT).build())
                .setLifetime(25)
                .addMotion(motionX, 0.02, motionZ)
                .spawn(level, x, y, z);
    }

    // 死亡余烬粒子：暗红+紫色，缓慢飘散（增强版：颜色更暗，像烧焦的余烬）
    // variant=3: 短生命周期变体（5 tick），用于星命锁链连接线，快速消散不拖影
    public static void spawnDyingEmber(ClientLevel level, double x, double y, double z, int variant) {
        double driftX = (level.random.nextDouble() - 0.5) * 0.01;
        double driftY = 0.005 + level.random.nextDouble() * 0.015;
        double driftZ = (level.random.nextDouble() - 0.5) * 0.01;
        int lifetime = variant == 3 ? 5 : 50;
        WorldParticleBuilder.create(LodestoneParticleTypes.SMOKE_PARTICLE)
                .setColorData(ColorParticleData.create(EMBER_START, EMBER_END).build())
                .setScaleData(GenericParticleData.create(0.4f, 0.01f).setEasing(Easing.QUINTIC_OUT).build())
                .setTransparencyData(GenericParticleData.create(0.65f, 0.1f).setEasing(Easing.CUBIC_OUT).build())
                .setLifetime(lifetime)
                .addMotion(driftX, driftY, driftZ)
                .spawn(level, x, y, z);
    }

    // 转阶段爆发粒子：白色+紫色，快速扩散并受重力影响（像爆炸后的星尘散落）
    // 使用 Lodestone 高级 API：setGravity 让星尘自然下落，enableNoClip 让粒子穿透方块
    public static void spawnTransitionBurst(ClientLevel level, double x, double y, double z) {
        double yaw = level.random.nextDouble() * Math.PI * 2;
        double pitch = (level.random.nextDouble() - 0.5) * Math.PI;
        double speed = 0.05 + level.random.nextDouble() * 0.08;
        double vx = Math.cos(pitch) * Math.cos(yaw) * speed;
        double vy = Math.sin(pitch) * speed;
        double vz = Math.cos(pitch) * Math.sin(yaw) * speed;
        WorldParticleBuilder.create(LodestoneParticleTypes.STAR_PARTICLE)
                .setColorData(ColorParticleData.create(TRANSITION_START, TRANSITION_END).build())
                .setScaleData(GenericParticleData.create(0.95f, 0.05f).setEasing(Easing.QUINTIC_OUT).build())
                .setTransparencyData(GenericParticleData.create(0.75f, 0f).setEasing(Easing.CUBIC_OUT).build())
                .setLifetime(35)
                .setGravity(0.008f)
                .setFriction(0.96f)
                .enableNoClip()
                .addMotion(vx, vy, vz)
                .spawn(level, x, y, z);
    }

    // 虚空微光粒子：淡紫闪烁，比SPARKLE更细腻柔和，带自发光
    // 适合星空背景、能量信号、微弱光芒
    // variant=0: 默认淡紫  variant=1: 亮紫  variant=2: 暗紫
    // 使用 Lodestone 高级 API：setFullBrightLighting 确保微光在黑暗中可见
    public static void spawnVoidTwinkle(ClientLevel level, double x, double y, double z, int variant) {
        Color startColor, endColor;
        switch (variant) {
            case 1 -> { startColor = new Color(126, 70, 168); endColor = new Color(84, 42, 126); }
            case 2 -> { startColor = new Color(56, 28, 84); endColor = new Color(42, 14, 70); }
            default -> { startColor = TWINKLE_START; endColor = TWINKLE_END; }
        }
        WorldParticleBuilder.create(LodestoneParticleTypes.TWINKLE_PARTICLE)
                .setColorData(ColorParticleData.create(startColor, endColor).build())
                .setScaleData(GenericParticleData.create(0.5f, 0.02f).setEasing(Easing.QUINTIC_OUT).build())
                .setTransparencyData(GenericParticleData.create(0.65f, 0f).setEasing(Easing.CUBIC_OUT).build())
                .setSpinData(SpinParticleData.create(0.2f, 0f).setEasing(Easing.QUINTIC_OUT).build())
                .setLifetime(40)
                .setFullBrightLighting()
                .addMotion(0, 0.01, 0)
                .spawn(level, x, y, z);
    }

    // 虚空丝线粒子：细长延伸火花，比EXTRUDING_SPARK更纤细
    // 适合激光线、细长能量轨迹、切割线
    // variant 参数控制延伸方向角度
    // 使用 Lodestone 高级 API：setFullBrightLighting 确保激光线在黑暗中清晰可见
    public static void spawnVoidThread(ClientLevel level, double x, double y, double z, int variant) {
        float angle = variant * (float) (Math.PI * 2 / 8);
        double motionX = Math.cos(angle) * 0.08;
        double motionZ = Math.sin(angle) * 0.08;
        WorldParticleBuilder.create(LodestoneParticleTypes.THIN_EXTRUDING_SPARK_PARTICLE)
                .setColorData(ColorParticleData.create(THREAD_START, THREAD_END).build())
                .setScaleData(GenericParticleData.create(0.45f, 0.02f).setEasing(Easing.QUINTIC_OUT).build())
                .setTransparencyData(GenericParticleData.create(0.7f, 0.1f).setEasing(Easing.CUBIC_OUT).build())
                .setLifetime(30)
                .setFullBrightLighting()
                .addMotion(motionX, 0.01, motionZ)
                .spawn(level, x, y, z);
    }

    // 根据 particleTypeId 获取对应的 LodestoneWorldParticleType
    // 供网络包处理器使用，将 int ID 映射回 Lodestone 内置粒子类型
    // 所有返回的粒子类型都已在 Lodestone 客户端注册了渲染工厂，保证可见
    public static LodestoneWorldParticleType getParticleTypeById(int id) {
        return switch (id) {
            case ID_STELLA_WISP -> LodestoneParticleTypes.WISP_PARTICLE.get();
            case ID_VOID_SPARK -> LodestoneParticleTypes.SPARKLE_PARTICLE.get();
            case ID_ASTRAL_BEAM -> LodestoneParticleTypes.SPARK_PARTICLE.get();
            case ID_IMPACT_WAVE -> LodestoneParticleTypes.EXTRUDING_SPARK_PARTICLE.get();
            case ID_DYING_EMBER -> LodestoneParticleTypes.SMOKE_PARTICLE.get();
            case ID_TRANSITION_BURST -> LodestoneParticleTypes.STAR_PARTICLE.get();
            case ID_VOID_TWINKLE -> LodestoneParticleTypes.TWINKLE_PARTICLE.get();
            case ID_VOID_THREAD -> LodestoneParticleTypes.THIN_EXTRUDING_SPARK_PARTICLE.get();
            default -> LodestoneParticleTypes.WISP_PARTICLE.get();
        };
    }

    // 根据网络包数据分发粒子效果
    // 网络包处理器的统一入口，根据 particleTypeId 选择对应的生成方法
    // 此方法仅在客户端主线程上被调用（由 ModPayloads 的 enqueueWork 保证）
    public static void handlePacket(ClientLevel level, int particleTypeId, double x, double y, double z, int extraData) {
        switch (particleTypeId) {
            case ID_STELLA_WISP -> spawnStellaWisp(level, x, y, z, extraData);
            case ID_VOID_SPARK -> spawnVoidSpark(level, x, y, z, extraData);
            case ID_ASTRAL_BEAM -> spawnAstralBeam(level, x, y, z);
            case ID_IMPACT_WAVE -> spawnImpactWave(level, x, y, z, extraData);
            case ID_DYING_EMBER -> spawnDyingEmber(level, x, y, z, extraData);
            case ID_TRANSITION_BURST -> spawnTransitionBurst(level, x, y, z);
            case ID_VOID_TWINKLE -> spawnVoidTwinkle(level, x, y, z, extraData);
            case ID_VOID_THREAD -> spawnVoidThread(level, x, y, z, extraData);
            default -> spawnStellaWisp(level, x, y, z, extraData);
        }
    }
}
