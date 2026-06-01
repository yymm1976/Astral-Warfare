package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mochi_753.astral_warfare.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

// BOSS 战斗 BGM 管理器
// 负责检测 BOSS 存在状态与战斗阶段、播放/停止/切换 BGM、交叉淡入淡出、防抖和维度切换清理
//
// 核心逻辑：
//   每 20 tick 扫描一次客户端 Level 中的 StellaEvokerEntity
//   最近 BOSS ≤ 64 格 & 存活 & 非死亡演出 → 根据 getCombatPhase() 选择曲目播放
//   一阶段（PHASE_1_CASTER）→ STELLA_EVOKER_PHASE1
//   二阶段（PHASE_2_MELEE）或 isTransitioning() → STELLA_EVOKER_PHASE2
//   无有效 BOSS → 2 秒渐隐后停止
//
// 交叉淡入淡出：切换曲目时旧曲 1s 渐隐 + 新曲 1s 淡入同时进行
//
// Side 安全：@EventBusSubscriber(value=Dist.CLIENT) 确保仅在客户端注册
@EventBusSubscriber(modid = AstralWarfare.MOD_ID, value = Dist.CLIENT)
public class StellaBattleMusic {

    // 当前正在播放的 BGM 实例，null 表示未播放
    private static BattleBgmSoundInstance currentBgm = null;

    // 正在渐隐中的旧 BGM 实例（交叉淡入淡出时旧曲仍在播放）
    private static BattleBgmSoundInstance fadingOutBgm = null;

    // 渐隐倒计时：0=不渐隐，>0=渐隐中
    private static int fadeOutTick = 0;

    // 扫描间隔：每秒扫描一次 BOSS 存在状态
    private static final int SCAN_INTERVAL = 20;

    // BOSS 检测范围（格）
    private static final double DETECT_RANGE = 64.0;

    // 基础音量（0~1）
    private static final float BASE_VOLUME = 0.6F;

    // 停止渐隐持续时间（tick）：2秒=40tick（BOSS 死亡/远离时使用）
    private static final int STOP_FADE_DURATION = 40;

    // 交叉淡入淡出持续时间（tick）：1秒=20tick（转阶段切换曲目时使用）
    private static final int CROSSFADE_DURATION = 20;

    // 防抖：上次切换（播放/停止）的游戏 tick
    private static long lastToggleTick = 0;

    // 防抖冷却：2秒内不允许重复切换
    private static final int TOGGLE_COOLDOWN = 40;

    // 扫描计时器
    private static int scanTimer = 0;

    // 当前 BGM 对应的战斗阶段（用于防重入判断）
    private static int currentBgmPhase = -1;

    // 客户端 tick 事件：扫描 BOSS 状态并管理 BGM 生命周期
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // 渐隐处理：每 tick 递减并更新音量
        if (fadeOutTick > 0) {
            fadeOutTick--;
            if (currentBgm != null) {
                // 线性渐隐：从 BASE_VOLUME 降至 0
                float progress = (float) fadeOutTick / STOP_FADE_DURATION;
                currentBgm.setVolume(BASE_VOLUME * progress);
            }
            // 渐隐结束：停止并清理
            if (fadeOutTick <= 0) {
                stopBgm();
            }
            // 交叉淡入淡出中的旧曲渐隐处理
            tickFadingOutBgm();
            return;
        }

        // 交叉淡入淡出中的旧曲渐隐处理（非停止渐隐期间也需要处理）
        tickFadingOutBgm();

        // 扫描计时：每 SCAN_INTERVAL tick 扫描一次
        scanTimer++;
        if (scanTimer < SCAN_INTERVAL) {
            // 非扫描 tick：检查 BGM 是否意外停止（如音频流错误）
            if (currentBgm != null && currentBgm.isStopped()) {
                StellaEvokerEntity boss = findNearestValidBoss(mc);
                if (boss != null) {
                    startBgmForPhase(boss.getCombatPhase());
                } else {
                    currentBgm = null;
                    currentBgmPhase = -1;
                }
            }
            return;
        }
        scanTimer = 0;

        // 扫描逻辑
        StellaEvokerEntity boss = findNearestValidBoss(mc);

        if (boss != null) {
            int bossPhase = boss.getCombatPhase();
            // 转阶段演出中视为二阶段
            if (boss.isTransitioning()) {
                bossPhase = StellaEvokerEntity.PHASE_2_MELEE;
            }

            if (currentBgm == null || currentBgm.isStopped()) {
                // 无 BGM 播放中：启动对应阶段的 BGM
                if (mc.player.tickCount - lastToggleTick >= TOGGLE_COOLDOWN) {
                    startBgmForPhase(bossPhase);
                    lastToggleTick = mc.player.tickCount;
                }
            } else if (currentBgmPhase != bossPhase) {
                // BGM 播放中但阶段不匹配：交叉淡入淡出切换
                crossfadeToPhase(bossPhase);
            }
        } else {
            // 无有效 BOSS：应停止 BGM
            if (currentBgm != null && fadeOutTick == 0) {
                if (mc.player.tickCount - lastToggleTick >= TOGGLE_COOLDOWN) {
                    fadeOutTick = STOP_FADE_DURATION;
                    lastToggleTick = mc.player.tickCount;
                }
            }
        }
    }

    // 处理交叉淡入淡出中的旧曲渐隐
    private static void tickFadingOutBgm() {
        if (fadingOutBgm != null) {
            // 旧曲音量线性递减（通过 setVolume 设置目标值，tick() 中平滑过渡）
            float currentTarget = fadingOutBgm.getTargetVolume();
            float newTarget = currentTarget - (BASE_VOLUME / CROSSFADE_DURATION);
            if (newTarget <= 0.01F) {
                // 渐隐完成：停止旧曲
                Minecraft mc = Minecraft.getInstance();
                mc.getSoundManager().stop(fadingOutBgm);
                fadingOutBgm = null;
            } else {
                fadingOutBgm.setVolume(newTarget);
            }
        }
    }

    // 玩家登出事件：清理 BGM 引用，防止维度切换后残留
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        stopBgm();
        if (fadingOutBgm != null) {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().stop(fadingOutBgm);
            fadingOutBgm = null;
        }
        fadeOutTick = 0;
        scanTimer = 0;
        lastToggleTick = 0;
        currentBgmPhase = -1;
    }

    // 查找最近的有效 BOSS
    // 有效条件：存活 & 非死亡演出 & 距离 ≤ DETECT_RANGE
    private static StellaEvokerEntity findNearestValidBoss(Minecraft mc) {
        if (mc.level == null) return null;
        LocalPlayer player = mc.player;
        if (player == null) return null;

        double rangeSq = DETECT_RANGE * DETECT_RANGE;
        List<StellaEvokerEntity> bosses = mc.level.getEntitiesOfClass(
                StellaEvokerEntity.class,
                player.getBoundingBox().inflate(DETECT_RANGE),
                boss -> boss.isAlive() && !boss.isDying()
        );

        StellaEvokerEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (StellaEvokerEntity boss : bosses) {
            double distSq = player.distanceToSqr(boss);
            if (distSq < nearestDistSq && distSq <= rangeSq) {
                nearestDistSq = distSq;
                nearest = boss;
            }
        }
        return nearest;
    }

    // 根据战斗阶段选择对应的 SoundEvent
    // 【修复】交换一阶段和二阶段的 BGM 分配
    // 原先 PHASE_2_MELEE → PHASE2 曲目，否则 → PHASE1 曲目
    // 用户反馈两阶段 BGM 播反了，交换返回值
    private static SoundEvent getSoundForPhase(int phase) {
        if (phase == StellaEvokerEntity.PHASE_2_MELEE) {
            return ModSounds.STELLA_EVOKER_PHASE1.get();
        }
        return ModSounds.STELLA_EVOKER_PHASE2.get();
    }

    // 启动指定阶段的 BGM
    private static void startBgmForPhase(int phase) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 先停止旧的 BGM（如果存在）
        if (currentBgm != null) {
            mc.getSoundManager().stop(currentBgm);
        }

        currentBgm = new BattleBgmSoundInstance(
                getSoundForPhase(phase),
                SoundSource.MUSIC,
                BASE_VOLUME
        );
        currentBgmPhase = phase;
        mc.getSoundManager().queueTickingSound(currentBgm);
    }

    // 交叉淡入淡出切换到指定阶段的 BGM
    // 旧曲在 CROSSFADE_DURATION 内渐隐，新曲同时在 CROSSFADE_DURATION 内从 0 淡入
    private static void crossfadeToPhase(int newPhase) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 将当前 BGM 移入渐隐队列
        if (currentBgm != null) {
            fadingOutBgm = currentBgm;
        }

        // 创建新 BGM，初始音量为极小值（0.01F），确保声音通道被 Minecraft 音频引擎正确分配
        // 如果初始音量为 0，SoundManager 可能不分配音频通道，导致后续 tick() 淡入无效
        currentBgm = new BattleBgmSoundInstance(
                getSoundForPhase(newPhase),
                SoundSource.MUSIC,
                0.01F
        );
        currentBgm.setTargetVolume(BASE_VOLUME);
        currentBgmPhase = newPhase;
        mc.getSoundManager().queueTickingSound(currentBgm);
    }

    // 停止 BGM 并清理引用
    private static void stopBgm() {
        if (currentBgm != null) {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().stop(currentBgm);
            currentBgm = null;
            currentBgmPhase = -1;
        }
    }

    // ==================== 内部类：可 tick 的 BGM 音效实例 ====================

    // 继承 AbstractTickableSoundInstance，支持每 tick 更新音量（渐隐/淡入）
    // 循环播放 BGM，直到被外部主动停止
    private static class BattleBgmSoundInstance extends AbstractTickableSoundInstance {

        private float targetVolume;

        // 构造函数
        // @param sound   音效事件（STELLA_EVOKER_PHASE1 或 PHASE2）
        // @param source  音源类别（MUSIC，受"音乐"滑块控制）
        // @param volume  初始音量（交叉淡入淡出时新曲从 0 开始）
        protected BattleBgmSoundInstance(SoundEvent sound,
                                         SoundSource source, float volume) {
            super(sound, source, RandomSource.create());
            this.volume = volume;
            this.targetVolume = volume;
            // 循环播放
            this.looping = true;
            // 延迟 0 tick 开始播放
            this.delay = 0;
            // BGM 不随距离衰减（由自定义逻辑控制音量）
            this.attenuation = SoundInstance.Attenuation.NONE;
            // 相对位置模式：音效位置相对于听众，不受世界坐标影响
            this.relative = true;
        }

        // 设置目标音量（供渐隐/淡入逻辑调用）
        void setVolume(float vol) {
            this.targetVolume = Math.max(0, Math.min(1, vol));
        }

        // 获取目标音量（供外部读取，避免直接访问 protected volume 字段）
        float getTargetVolume() {
            return this.targetVolume;
        }

        // 设置目标音量（供交叉淡入淡出使用，不立即改变 volume）
        void setTargetVolume(float vol) {
            this.targetVolume = Math.max(0, Math.min(1, vol));
        }

        // 每 tick 调用：平滑过渡音量到目标值
        // 步长 = BASE_VOLUME / CROSSFADE_DURATION = 0.6 / 20 = 0.03/tick
        // 确保在 CROSSFADE_DURATION 内完成从 0 到 BASE_VOLUME 的淡入
        @Override
        public void tick() {
            float step = BASE_VOLUME / CROSSFADE_DURATION;
            if (this.volume < this.targetVolume) {
                this.volume = Math.min(this.volume + step, this.targetVolume);
            } else if (this.volume > this.targetVolume) {
                this.volume = Math.max(this.volume - step, this.targetVolume);
            }
        }
    }
}
