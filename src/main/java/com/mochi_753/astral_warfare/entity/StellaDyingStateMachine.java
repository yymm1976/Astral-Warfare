package com.mochi_753.astral_warfare.entity;

import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.network.ClientboundLodestoneParticlePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

// StellaEvoker 死亡演出状态机组件
// 从 StellaEvokerEntity 中剥离死亡演出逻辑，实现单一职责原则
// 组件内部管理全部演出 tick 计数、粒子序列与音效时序
// 实体自身只保留 isDying() 状态判断和 dyingFSM.tick() 调用
//
// 放在 entity 包中（而非 entity.ai），以便访问 StellaEvokerEntity 的包可见字段
public class StellaDyingStateMachine {

    // 死亡演出持续时间：5 秒 = 100 tick
    private static final int DYING_DURATION_TICKS = 100;

    private final StellaEvokerEntity evoker;
    // 死亡演出倒计时计时器
    private int dyingTimer = 0;

    public StellaDyingStateMachine(StellaEvokerEntity evoker) {
        this.evoker = evoker;
    }

    // 启动死亡演出，由 StellaEvokerEntity.die() 调用
    public void startDying() {
        this.dyingTimer = DYING_DURATION_TICKS;
    }

    // 是否正在死亡演出中
    public boolean isActive() {
        return this.dyingTimer > 0;
    }

    // 获取当前死亡演出进度（0.0 ~ 1.0）
    public float getProgress() {
        return 1.0F - (float) this.dyingTimer / DYING_DURATION_TICKS;
    }

    // 每 tick 调用一次，驱动死亡演出状态机
    public void tick(ServerLevel level) {
        if (this.dyingTimer <= 0) {
            return;
        }

        this.dyingTimer--;

        // 5 秒期间：身体逐渐变透明（通过缩放模拟），内部吸入紫色虚空粒子
        // 粒子效果：向中心吸入的反向龙息粒子
        float progress = getProgress();

        // 粒子密度随时间增加（限流：使用随机数控制）
        // 死亡吸入情景：身体逐渐透明，虚空能量从四周吸入中心
        // 只保留 VOID_SPARK（虚空吸入）+ PORTAL（原版传送门旋转），删除 STELLA_WISP，降低密度
        if (this.evoker.getRandom().nextFloat() < 0.2F + progress * 0.3F) {
            int particleCount = (int) (1 + progress * 4);
            for (int i = 0; i < particleCount; i++) {
                double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
                double radius = 1.0 + this.evoker.getRandom().nextDouble() * (3.0 - progress * 2.5);
                double px = this.evoker.getX() + Math.cos(angle) * radius;
                double pz = this.evoker.getZ() + Math.sin(angle) * radius;
                double py = this.evoker.getY() + this.evoker.getRandom().nextDouble() * 2.0;

                // 虚空火花（默认变体）：反向吸入粒子
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(this.evoker,
                        new ClientboundLodestoneParticlePacket(StellaParticles.ID_VOID_SPARK, px, py, pz, 0));
            }
            // 原版传送门粒子：死亡吸入的虚空旋转效果
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    this.evoker.getX(), this.evoker.getY() + 1.0, this.evoker.getZ(),
                    2, 0.5, 0.5, 0.5, 0.5);
        }

        // 第 5 秒整：轰然炸开
        if (this.dyingTimer <= 0) {
            finishDying(level);
        }
    }

    // 死亡演出结束：爆炸消散 + 战利品掉落 + 全服公告
    // 使用 Lodestone 粒子网络包替代原版 sendParticles
    private void finishDying(ServerLevel level) {
        // 死亡爆炸情景：轰然炸开，冲击波+烟尘
        // 只保留 IMPACT_WAVE（冲击波扩散）+ LARGE_SMOKE（原版浓密烟尘），删除 VOID_SPARK、EXPLOSION，降低粒子数量
        // 原版大型烟雾：死亡爆炸的浓密烟尘
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                this.evoker.getX(), this.evoker.getY() + 1.0, this.evoker.getZ(),
                15, 1.5, 0.5, 1.5, 0.05);
        // 大范围爆炸粒子
        for (int i = 0; i < 40; i++) {
            double angle = this.evoker.getRandom().nextDouble() * Math.PI * 2;
            double r = this.evoker.getRandom().nextDouble() * 6.0;
            double px = this.evoker.getX() + Math.cos(angle) * r;
            double pz = this.evoker.getZ() + Math.sin(angle) * r;
            // 冲击波（大爆炸变体）：死亡爆炸
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(this.evoker,
                    new ClientboundLodestoneParticlePacket(StellaParticles.ID_IMPACT_WAVE, px, this.evoker.getY() + 1.0, pz, 1));
        }

        // 播放末影龙死亡回音
        level.playSound(null, this.evoker.getX(), this.evoker.getY(), this.evoker.getZ(),
                SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 3.0F, 0.5F);

        // 触发原版战利品表掉落（由 stella_evoker.json 配置）
        // 必须在 super.die() 之前执行，否则实体已被移除
        this.evoker.onFinishDyingDropLoot();

        // 全服公告
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(
                    Component.translatable("entity.astral_warfare.stella_evoker.defeated"));
        }

        // 关键：先清除 DATA_IS_DYING 标志，再调用 forceDie()
        // 原因：isDeadOrDying() 被重写为 isDying() 时返回 false
        // 如果不清除 DATA_IS_DYING，forceDie() 后 isDeadOrDying() 仍返回 false
        // 导致 LivingEntity.tick() 中的移除逻辑不执行，实体残留为 0 血量尸体
        // 通过包可见的 setDying() 方法访问，不能直接操作 entityData（protected 字段）
        this.evoker.setDying(false);

        // 调用 forceDie()，绕过 die() 中的 isDying() 检查
        // super.die() 会设置 this.dead = true，下一 tick LivingEntity.tick() 检测到
        // isDeadOrDying() 返回 true 后自动调用 remove(KILLED) 移除实体
        this.evoker.forceDie();
    }

    // 从 NBT 恢复死亡演出计时器
    public void readFromNbt(int timer) {
        this.dyingTimer = timer;
    }

    // 保存死亡演出计时器到 NBT
    public int writeToNbt() {
        return this.dyingTimer;
    }
}
