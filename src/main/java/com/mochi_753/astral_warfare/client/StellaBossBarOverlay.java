package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.attachment.ManaData;

import java.awt.Color;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.lodestar.lodestone.registry.common.particle.LodestoneScreenParticleTypes;
import team.lodestar.lodestone.systems.easing.Easing;
import team.lodestar.lodestone.systems.particle.builder.ScreenParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;
import team.lodestar.lodestone.systems.particle.screen.ScreenParticleHolder;

// StellaEvoker BOSS 法力条自定义渲染层
// 使用 NeoForge 1.21.1 的 LayeredDraw.Layer 系统，在原版 BossOverlay 后注册
// 完全基于 ClientManaData 缓存渲染，无需查找客户端实体，零性能开销
// 二阶段法力系统关闭后，法力条自动隐藏
//
// 法力枯竭警告：当法力值低于30%时，在法力条位置生成Lodestone屏幕粒子
// 使用深紫色WISP粒子，每秒2-3个，营造"法力即将耗尽"的紧迫感
public class StellaBossBarOverlay {

    private static final Logger LOGGER = LoggerFactory.getLogger(StellaBossBarOverlay.class);

    // 法力条尺寸
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;

    // 法力条颜色系统（现代极简风格）
    // 主填充色：饱和度更高的星空蓝
    private static final int MANA_FILL = 0xFF4DB8FF;
    // 背景：极暗半透明
    private static final int MANA_BG = 0x40000000;
    // 边框：细线蓝灰
    private static final int MANA_BORDER = 0xFF2A4A6A;
    // 枯竭警告色：暗红
    private static final int MANA_LOW_FILL = 0xFFFF4444;

    // 动画状态
    private static float shimmerOffset = 0;
    // 每条 BossBar 之间的标准间距
    private static final int BOSS_BAR_SPACING = 19;
    // 原版 BossBar 起始 Y 偏移
    private static final int BOSS_BAR_TOP_OFFSET = 12;

    // 法力枯竭阈值：法力低于30%时触发屏幕粒子警告
    private static final float MANA_LOW_THRESHOLD = 0.3F;

    // 屏幕粒子持有器：存储法力枯竭警告粒子
    // ScreenParticleHolder 管理粒子的生命周期、tick更新和渲染
    private static final ScreenParticleHolder manaWarningParticles = new ScreenParticleHolder();

    // 粒子生成计时器：控制每秒生成2-3个粒子（约每8-10 tick一个）
    private static int particleSpawnTimer = 0;
    // 粒子生成间隔（tick）：约10 tick = 0.5秒，即每秒2个粒子
    private static final int PARTICLE_SPAWN_INTERVAL = 10;

    // 法力枯竭粒子颜色：深紫色，与法力条颜色一致
    private static final Color MANA_LOW_START = new Color(100, 30, 180);
    private static final Color MANA_LOW_END = new Color(180, 80, 255);

    // LayeredDraw.Layer 实现：绘制法力条 + 法力枯竭屏幕粒子
    public static final LayeredDraw.Layer MANA_BAR_LAYER = (guiGraphics, deltaTick) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui == null) {
            return;
        }

        // 直接从 ClientManaData 缓存读取法力数据，无需遍历客户端实体
        // ClientManaData 中的数据由网络包同步，始终与服务端保持一致
        // 支持多 BOSS 场景：为每个 BOSS 渲染独立法力条
        // 性能优化：直接遍历 Map 条目，避免每帧分配新 ArrayList
        java.util.Map<UUID, ManaData> allData = ClientManaData.getAllManaData();
        if (allData.isEmpty()) {
            manaWarningParticles.tick();
            manaWarningParticles.render(guiGraphics);
            return;
        }

        // 检查是否有活跃法力条（非禁用状态）
        boolean hasActiveManaBar = false;
        for (ManaData data : allData.values()) {
            if (!data.isManaSystemDisabled()) {
                hasActiveManaBar = true;
                break;
            }
        }

        if (!hasActiveManaBar) {
            // 没有活跃法力条时，仍然tick已有粒子使其自然消散
            manaWarningParticles.tick();
            manaWarningParticles.render(guiGraphics);
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // 动态 Y 偏移：法力条位于所有 BossBar 下方
        // 通过 BossHealthOverlay.events 获取当前活跃 BossBar 数量
        // 在整合包环境中，其他模组可能也有 BossBar，必须动态计算避免重叠
        // Access Transformer 使 events 字段可公开访问
        // 防御性 try-catch：若 NeoForge/Mojang 更新中 events 字段名变更，
        // NoSuchFieldError 是 Error 子类，必须 catch(Throwable) 而非 catch(Exception)
        // 降级策略：bossBarCount=0，法力条渲染在屏幕顶部，不会崩溃
        BossHealthOverlay bossOverlay = mc.gui.getBossOverlay();
        int bossBarCount;
        try {
            bossBarCount = bossOverlay.events.size();
        } catch (Throwable t) {
            // S-03修复：记录降级日志，便于排查 events 字段变更问题
            LOGGER.warn("bossBarCount 降级", t);
            bossBarCount = 0;
        }
        int baseY = BOSS_BAR_TOP_OFFSET + bossBarCount * BOSS_BAR_SPACING;
        int x = (screenWidth - BAR_WIDTH) / 2;

        // 为每个活跃 BOSS 渲染法力条
        int manaBarIndex = 0;
        for (ManaData manaData : allData.values()) {
            if (manaData.isManaSystemDisabled()) continue;
            int y = baseY + manaBarIndex * BOSS_BAR_SPACING;

            // 计算法力比例（除零保护：maxMana<=0 时比例归零）
            float manaRatio = manaData.getMaxMana() <= 0 ? 0 : (float) manaData.getCurrentMana() / manaData.getMaxMana();
            int filledWidth = (int) (BAR_WIDTH * manaRatio);

            // 更新光泽动画
            if (mc.level != null) {
                shimmerOffset = (mc.level.getGameTime() % 60) / 60.0f;
            }

            // 现代极简法力条设计：
            //   - 细边框（1像素蓝灰线）
            //   - 暗背景（几乎透明）
            //   - 纯色填充（高饱和度蓝，无渐变）
            //   - 微妙的光泽扫过（半透明白色细线）
            //   - 低法力时变红警告

            int fillColor = (manaRatio < MANA_LOW_THRESHOLD) ? MANA_LOW_FILL : MANA_FILL;

            // 外边框
            guiGraphics.fill(x, y - 1, x + BAR_WIDTH, y, MANA_BORDER);
            guiGraphics.fill(x, y + BAR_HEIGHT, x + BAR_WIDTH, y + BAR_HEIGHT + 1, MANA_BORDER);
            guiGraphics.fill(x - 1, y, x, y + BAR_HEIGHT, MANA_BORDER);
            guiGraphics.fill(x + BAR_WIDTH, y, x + BAR_WIDTH + 1, y + BAR_HEIGHT, MANA_BORDER);

            // 背景
            guiGraphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, MANA_BG);

            // 填充条
            if (filledWidth > 0) {
                guiGraphics.fill(x, y, x + filledWidth, y + BAR_HEIGHT, fillColor);

                // 光泽扫过：一条极细的白线缓慢移动
                int shimmerWidth = Math.min(20, filledWidth / 4);
                int shimmerX = x + (int) ((filledWidth - shimmerWidth) * shimmerOffset);
                if (shimmerX + shimmerWidth > x + filledWidth) {
                    shimmerX = x + filledWidth - shimmerWidth;
                }
                guiGraphics.fill(shimmerX, y, shimmerX + shimmerWidth, y + BAR_HEIGHT, 0x40FFFFFF);
            }

            // 法力枯竭警告：法力低于30%时在法力条位置生成屏幕粒子
            // 深紫色WISP粒子缓慢上浮，营造"法力即将耗尽"的紧迫感
            if (manaRatio < MANA_LOW_THRESHOLD && manaRatio > 0) {
                particleSpawnTimer++;
                if (particleSpawnTimer >= PARTICLE_SPAWN_INTERVAL) {
                    particleSpawnTimer = 0;
                    // 在法力条范围内随机位置生成粒子
                    // S-02修复：使用 mc.level.random 替代 Math.random()
                    // Math.random() 是 synchronized 方法，渲染帧高频调用造成线程竞争
                    double particleX = x + mc.level.random.nextDouble() * BAR_WIDTH;
                    double particleY = y + mc.level.random.nextDouble() * BAR_HEIGHT;
                    ScreenParticleBuilder.create(LodestoneScreenParticleTypes.WISP, manaWarningParticles)
                            .setColorData(ColorParticleData.create(MANA_LOW_START, MANA_LOW_END).build())
                            .setScaleData(GenericParticleData.create(0.6f, 0.05f).setEasing(Easing.QUINTIC_OUT).build())
                            .setTransparencyData(GenericParticleData.create(0.8f, 0f).setEasing(Easing.CUBIC_OUT).build())
                            .setLifetime(30)
                            .addMotion(0, -0.5)
                            .spawn(particleX, particleY);
                }
            }
            manaBarIndex++;
        }

        // tick更新屏幕粒子（位置、生命周期、透明度衰减等）
        manaWarningParticles.tick();
        // 渲染屏幕粒子
        manaWarningParticles.render(guiGraphics);
    };
}
