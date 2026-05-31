package com.mochi_753.astral_warfare.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// 法力值数据持有类，通过 Data Attachment 机制绑定到实体上
// 替代旧版 Capability 系统，NeoForge 1.21.x 推荐使用 Data Attachments 管理实体附加数据
//
// 线程安全说明：
//   主线程（游戏 tick）和网络/存档线程可能并发访问同一 ManaData 实例。
//   setter 方法加 synchronized 确保写入原子性；
//   Codec 序列化通过 synchronized 快照方法读取，确保所有字段来自同一时刻。
public class ManaData {

    private int currentMana;
    private int maxMana;
    // 二阶段法力系统是否关闭（客户端渲染用）
    private boolean manaSystemDisabled;

    // Codec 用于将法力数据序列化到磁盘（存档保存）
    // RecordCodecBuilder 通过字段定义自动生成编解码逻辑
    // 使用 synchronized 快照方法确保序列化时读取一致的状态
    public static final Codec<ManaData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("currentMana").forGetter(ManaData::snapshotCurrentMana),
                    Codec.INT.fieldOf("maxMana").forGetter(ManaData::snapshotMaxMana),
                    Codec.BOOL.optionalFieldOf("manaSystemDisabled", false).forGetter(ManaData::snapshotManaSystemDisabled)
            ).apply(instance, ManaData::new)
    );

    // 默认构造：法力值 100/100，法力系统未关闭
    public ManaData() {
        this(100, 100, false);
    }

    public ManaData(int currentMana, int maxMana) {
        this(currentMana, maxMana, false);
    }

    public ManaData(int currentMana, int maxMana, boolean manaSystemDisabled) {
        this.currentMana = currentMana;
        this.maxMana = maxMana;
        this.manaSystemDisabled = manaSystemDisabled;
    }

    public synchronized int getCurrentMana() {
        return currentMana;
    }

    public synchronized void setCurrentMana(int currentMana) {
        this.currentMana = currentMana;
    }

    public synchronized int getMaxMana() {
        return maxMana;
    }

    public synchronized void setMaxMana(int maxMana) {
        this.maxMana = maxMana;
    }

    public synchronized boolean isManaSystemDisabled() {
        return manaSystemDisabled;
    }

    public synchronized void setManaSystemDisabled(boolean manaSystemDisabled) {
        this.manaSystemDisabled = manaSystemDisabled;
    }

    // 序列化快照方法：synchronized 确保读取时字段不被并发修改
    // Codec 逐字段调用 getter，synchronized 保证每次读取时对象处于一致状态
    // 在多人服务器环境下是必要的数据竞争防护
    private synchronized int snapshotCurrentMana() { return this.currentMana; }
    private synchronized int snapshotMaxMana() { return this.maxMana; }
    private synchronized boolean snapshotManaSystemDisabled() { return this.manaSystemDisabled; }
}
