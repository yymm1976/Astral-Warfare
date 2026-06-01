package com.mochi_753.astral_warfare.client;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.entity.StellaEvokerEntity;
import com.mochi_753.astral_warfare.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

// BOSS 战斗 BGM 管理器
// 负责检测 BOSS 存在状态、播放/停止 BGM、渐隐过渡、防抖和维度切换清理
//
// 核心逻辑：
//   每 20 tick 扫描一次客户端 Level 中的 StellaEvokerEntity
//   最近 BOSS ≤ 64 格 & 存活 & 非死亡演出 → 播放 BGM
//   无有效 BOSS → 2 秒渐隐后停止
//
// Side 安全：@EventBusSubscriber(value=Dist.CLIENT) 确保仅在客户端注册
@EventBusSubscriber(modid = AstralWarfare.MOD_ID, value = Dist.CLIENT)
public class StellaBattleMusic {

    // 当前正在播放的 BGM 实例，null 表示未播放
    private static BattleBgmSoundInstance currentBgm = null;

    // 渐隐倒计时：0=不渐隐，>0=渐隐中
    private static int fadeOutTick = 0;

    // 扫描间隔：每秒扫描一次 BOSS 存在状态
    private static final int SCAN_INTERVAL = 20;

    // BOSS 检测范围（格）
    private static final double DETECT_RANGE = 64.0;

    // 基础音量（0~1）
    private static final float BASE_VOLUME = 0.6F;

    // 渐隐持续时间（tick）：2秒=40tick
    private static final int FADE_DURATION = 40;

    // 防抖：上次切换（播放/停止）的游戏 tick
    private static long lastToggleTick = 0;

    // 防抖冷却：2秒内不允许重复切换
    private static final int TOGGLE_COOLDOWN = 40;

    // 扫描计时器
    private static int scanTimer = 0;

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
                float progress = (float) fadeOutTick / FADE_DURATION;
                currentBgm.setVolume(BASE_VOLUME * progress);
            }
            // 渐隐结束：停止并清理
            if (fadeOutTick <= 0) {
                stopBgm();
            }
            return;
        }

        // 扫描计时：每 SCAN_INTERVAL tick 扫描一次
        scanTimer++;
        if (scanTimer < SCAN_INTERVAL) {
            // 非扫描 tick：检查 BGM 是否意外停止（如音频流错误）
            if (currentBgm != null && currentBgm.isStopped()) {
                // BGM 意外停止但 BOSS 仍有效，自动重新播放
                StellaEvokerEntity boss = findNearestValidBoss(mc);
                if (boss != null) {
                    startBgm();
                } else {
                    currentBgm = null;
                }
            }
            return;
        }
        scanTimer = 0;

        // 扫描逻辑
        StellaEvokerEntity boss = findNearestValidBoss(mc);

        if (boss != null) {
            // 有效 BOSS 存在：应播放 BGM
            if (currentBgm == null || currentBgm.isStopped()) {
                // 防抖检查：2秒内不允许重复切换
                if (mc.player.tickCount - lastToggleTick >= TOGGLE_COOLDOWN) {
                    startBgm();
                    lastToggleTick = mc.player.tickCount;
                }
            }
        } else {
            // 无有效 BOSS：应停止 BGM
            if (currentBgm != null && fadeOutTick == 0) {
                // 防抖检查
                if (mc.player.tickCount - lastToggleTick >= TOGGLE_COOLDOWN) {
                    fadeOutTick = FADE_DURATION;
                    lastToggleTick = mc.player.tickCount;
                }
            }
        }
    }

    // 玩家登出事件：清理 BGM 引用，防止维度切换后残留
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        stopBgm();
        fadeOutTick = 0;
        scanTimer = 0;
        lastToggleTick = 0;
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

    // 开始播放 BGM
    private static void startBgm() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 先停止旧的 BGM（如果存在）
        if (currentBgm != null) {
            mc.getSoundManager().stop(currentBgm);
        }

        currentBgm = new BattleBgmSoundInstance(
                ModSounds.STELLA_EVOKER_PHASE1.get(),
                SoundSource.MUSIC,
                BASE_VOLUME
        );
        mc.getSoundManager().queueTickingSound(currentBgm);
    }

    // 停止 BGM 并清理引用
    private static void stopBgm() {
        if (currentBgm != null) {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().stop(currentBgm);
            currentBgm = null;
        }
    }

    // ==================== 内部类：可 tick 的 BGM 音效实例 ====================

    // 继承 AbstractTickableSoundInstance，支持每 tick 更新音量（渐隐）
    // 循环播放 BGM，直到被外部主动停止
    private static class BattleBgmSoundInstance extends AbstractTickableSoundInstance {

        private float targetVolume;

        // 构造函数
        // @param sound   音效事件（STELLA_EVOKER_PHASE1）
        // @param source  音源类别（MUSIC，受"音乐"滑块控制）
        // @param volume  初始音量
        protected BattleBgmSoundInstance(net.minecraft.sounds.SoundEvent sound,
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

        // 设置目标音量（供渐隐逻辑调用）
        void setVolume(float vol) {
            this.targetVolume = Math.max(0, Math.min(1, vol));
        }

        // 每 tick 调用：平滑过渡音量到目标值
        @Override
        public void tick() {
            // 线性插值趋近目标音量
            if (this.volume < this.targetVolume) {
                this.volume = Math.min(this.volume + 0.05F, this.targetVolume);
            } else if (this.volume > this.targetVolume) {
                this.volume = Math.max(this.volume - 0.05F, this.targetVolume);
            }
        }
    }
}
