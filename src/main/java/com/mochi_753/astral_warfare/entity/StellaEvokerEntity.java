package com.mochi_753.astral_warfare.entity;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.attachment.ManaData;
import com.mochi_753.astral_warfare.entity.ai.DespairExecutionGoal;
import com.mochi_753.astral_warfare.entity.ai.Phase2MeleeGoal;
import com.mochi_753.astral_warfare.entity.ai.SpellCastGoal;
import com.mochi_753.astral_warfare.entity.ai.SpellType;
import com.mochi_753.astral_warfare.entity.ai.StellaFlyingMoveControl;
import com.mochi_753.astral_warfare.init.ModAttachments;
import com.mochi_753.astral_warfare.init.ModConfig;
import com.mochi_753.astral_warfare.init.ModConstants;
import com.mochi_753.astral_warfare.network.ClientboundStellaManaPacket;
import com.mochi_753.astral_warfare.network.ClientboundStellaManaRemovePacket;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

// 星穹唤星者 - BOSS 实体
// 继承 AbstractIllager（灾厄村民基类），保留 Raid 免疫等逻辑
// 实现 GeoEntity 接口，使用 GeckoLib 驱动骨骼动画（替代原版 IllagerModel 手动映射）
//
// 核心机制：
//   一阶段（法师形态）：法术轰炸 + 法力枯竭坠落 + 水晶联动回蓝
//   二阶段（近战形态）：半血转阶段 → 地面高机动寻路 → 虚空禁锢 → 绝望处决
//   死亡演出：5 秒下跪内爆 → 爆炸消散 → 战利品掉落 + 全服公告
//
// Raid 安全：重写所有突袭相关方法返回 false，确保 BOSS 不会参与原版村庄袭击
public class StellaEvokerEntity extends AbstractIllager implements GeoEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(StellaEvokerEntity.class);

    // ==================== 战斗阶段常量 ====================
    public static final int PHASE_1_CASTER = 0;
    public static final int PHASE_2_MELEE = 1;

    // ==================== 脱战检测常量 ====================
    private static final int ANCHOR_CHECK_INTERVAL = ModConstants.ANCHOR_CHECK_INTERVAL;
    private static final double ANCHOR_CHECK_RADIUS = ModConstants.ANCHOR_CHECK_RADIUS;

    // ==================== 二阶段常量 ====================
    private static final double PHASE2_SPEED_MULTIPLIER = ModConstants.PHASE2_SPEED_MULTIPLIER;

    // ==================== SynchedEntityData ====================
    static final EntityDataAccessor<Boolean> DATA_IS_WEAKENED =
            SynchedEntityData.defineId(StellaEvokerEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Integer> DATA_WEAKENED_TICKS =
            SynchedEntityData.defineId(StellaEvokerEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> DATA_COMBAT_PHASE =
            SynchedEntityData.defineId(StellaEvokerEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> DATA_IS_TRANSITIONING =
            SynchedEntityData.defineId(StellaEvokerEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_MANA_SYSTEM_DISABLED =
            SynchedEntityData.defineId(StellaEvokerEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_IS_DYING =
            SynchedEntityData.defineId(StellaEvokerEntity.class, EntityDataSerializers.BOOLEAN);

    // ==================== 转阶段演出组件 ====================
    private final StellaTransitionStateMachine transitionFSM = new StellaTransitionStateMachine(this);

    // ==================== 星门涌动技能组件 ====================
    private final StellaGateSurgeAbility gateSurgeAbility = new StellaGateSurgeAbility(this);

    // ==================== 法力值系统组件 ====================
    private final StellaManaSystem manaSystem = new StellaManaSystem(this);

    // ==================== 死亡演出状态机组件 ====================
    private final StellaDyingStateMachine dyingFSM = new StellaDyingStateMachine(this);
    public StellaDyingStateMachine getDyingFSM() { return dyingFSM; }

    // ==================== 内部状态 ====================
    final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_12
    );

    private int anchorCheckTimer = 0;

    // ==================== GeckoLib 动画系统 ====================

    // 动画实例缓存：GeckoLib 要求每个 GeoEntity 实例拥有独立的缓存
    // 用于存储动画状态、控制器数据等，避免多实体间动画状态串扰
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // 一阶段待机动画的 RawAnimation 定义
    // 对应 animations/stella_evoker_idle_phase1.animation.json 中的 stella_evoker_idle_phase1
    // GeckoLib 4.7.3：使用 thenLoop() 而非 then(name, LoopType.LOOP)
    private static final RawAnimation IDLE_PHASE1_ANIM = RawAnimation.begin()
            .thenLoop("stella_evoker_idle_phase1");

    // GeckoLib 动画控制器注册
    // 当前仅注册一阶段待机动画控制器，后续 Phase 可扩展攻击动画控制器
    // AnimationController 构造函数：(animatable, name, transitionTicks, stateHandler)
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle_controller", 10, state -> {
            // 一阶段待机：播放浮动呼吸动画
            // 后续可在此处根据战斗阶段、施法状态等条件切换动画
            state.getController().setAnimation(IDLE_PHASE1_ANIM);
            return software.bernie.geckolib.animation.PlayState.CONTINUE;
        }));
    }

    // 返回动画实例缓存（GeoEntity 接口要求）
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // 返回当前 tick 数（GeoEntity 接口要求）
    // GeckoLib 使用此值驱动动画时间轴
    @Override
    public double getTick(Object entity) {
        return this.tickCount;
    }

    // 施法 AI Goal：包可见，供组件访问
    SpellCastGoal spellCastGoal;
    Phase2MeleeGoal phase2MeleeGoal;
    DespairExecutionGoal despairExecutionGoal;

    // 祭坛中心坐标（用于防止活塞推走等边缘情况）
    private BlockPos altarCenterPos = null;

    public StellaEvokerEntity(EntityType<? extends StellaEvokerEntity> entityType, Level level) {
        super(entityType, level);
        this.bossEvent.setName(this.getDisplayName());
        this.setNoGravity(true);
        this.moveControl = new StellaFlyingMoveControl(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_WEAKENED, false);
        builder.define(DATA_WEAKENED_TICKS, 0);
        builder.define(DATA_COMBAT_PHASE, PHASE_1_CASTER);
        builder.define(DATA_IS_TRANSITIONING, false);
        builder.define(DATA_MANA_SYSTEM_DISABLED, false);
        builder.define(DATA_IS_DYING, false);
    }

    @Override
    protected void registerGoals() {
        // 必须在此处创建 SpellCastGoal，不能依赖字段初始化器
        // Mob 构造函数调用 registerGoals() 时，字段初始化器尚未执行
        // 如果使用字段初始化器，spellCastGoal 为 null，导致实体无法创建
        this.spellCastGoal = new SpellCastGoal(this);
        this.goalSelector.addGoal(1, this.spellCastGoal);

        // 目标选择器：主动追踪 50 格范围内的生存/冒险模式玩家
        // 排除创造模式和旁观模式玩家，BOSS 不应对无敌玩家产生仇恨
        // NearestAttackableTargetGoal 第三个参数 true 表示必须可见
        // 第四个参数为目标过滤条件：仅选中非创造且非旁观的玩家
        // 注意：lambda 参数类型为 LivingEntity，需先转型为 Player 才能调用 isCreative()/isSpectator()
        this.targetSelector.addGoal(1,
                new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                        this, Player.class, true,
                        living -> living instanceof Player player && !player.isCreative() && !player.isSpectator()));
        // 受击反击目标：被玩家攻击时立即设置仇恨
        // 作为 NearestAttackableTargetGoal 的补充，确保 BOSS 被攻击时立刻反击
        // 优先级2低于 NearestAttackableTargetGoal（优先级1），不会覆盖主动搜索
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    // ==================== AbstractIllager 必须实现的方法 ====================

    // 返回灾厄村民手臂姿态（AbstractIllager 抽象方法，必须实现）
    // GeckoLib 接管动画驱动后，此方法不再影响模型渲染
    // 保留 CROSSED 作为默认姿态，确保 AbstractIllager 父类逻辑不会异常
    @Override
    public AbstractIllager.IllagerArmPose getArmPose() {
        return IllagerArmPose.CROSSED;
    }

    // 获取 BossBar 事件对象（供 SpellCastGoal 发送技能施放提示使用）
    public ServerBossEvent getBossEvent() { return this.bossEvent; }

    // ==================== Raid 安全：禁用所有突袭行为 ====================
    // 重写以下方法确保 BOSS 永远不会参与原版村庄袭击

    @Override
    public boolean canJoinRaid() {
        return false;
    }

    @Override
    public void setCanJoinRaid(boolean canJoin) {
        // 忽略任何尝试设置 canJoinRaid 的操作
    }

    @Override
    public void setCurrentRaid(Raid raid) {
        // 拒绝加入任何 Raid
    }

    @Override
    public boolean hasActiveRaid() {
        return false;
    }

    @Override
    public boolean isCaptain() {
        return false;
    }

    @Override
    public boolean isCelebrating() {
        return false;
    }

    @Override
    public void setCelebrating(boolean celebrating) {
        // 忽略庆祝状态设置
    }

    @Override
    public boolean canJoinPatrol() {
        return false;
    }

    // AbstractIllager 继承 Raider，Raider 要求实现以下抽象方法
    // 返回 null 表示 BOSS 没有庆祝音效（不会参与 Raid 庆祝）
    @Override
    public net.minecraft.sounds.SoundEvent getCelebrateSound() {
        return null;
    }

    // Raider 要求实现的抽象方法：应用 Raid 波次增益
    // BOSS 不参与 Raid，此方法为空实现
    @Override
    public void applyRaidBuffs(ServerLevel level, int wave, boolean unused) {
    }

    // ==================== 属性配置 ====================

    public static AttributeSupplier createAttributes() {
        return AbstractIllager.createMonsterAttributes()
                // 基础血量 200.0 是安全的默认硬编码基准值
                // 不能在此处调用 ModConfig.BASE_HP.get()，因为实体属性注册阶段配置文件尚未加载
                // 真实配置值由 onLoad() 在区块加载时动态注入覆盖
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ATTACK_DAMAGE, 15.0)
                .add(Attributes.FOLLOW_RANGE, 50.0)
                .build();
    }

    // ModConfig 动态血量注入标志
    // 首次 tick 时从 ModConfig 读取血量配置并覆盖基础属性
    // 不能在 createAttributes() 中调用 ModConfig.get()，因为注册阶段配置文件尚未加载
    private boolean configHpInjected = false;

    // 从 ModConfig 动态注入血量值
    // 架构师裁决：ModConfig 是唯一权威，createAttributes() 中的 200.0 只是安全的默认基准
    // 在实体首次 tick 时（此时配置文件已加载）从 ModConfig 读取真实值并覆盖基础属性
    // 首次生成时同步刷新当前血量为满值；区块重载时不自动回满
    //
    // 关键：如果 NocturnalAstrolabeItem 已设置多人缩放血量（baseValue >= configBaseHp），
    // 则不覆盖，保留缩放后的血量值。仅当 baseValue 仍为默认值 200.0 时才注入配置值
    private void injectConfigHp() {
        if (configHpInjected) return;
        configHpInjected = true;
        double configMaxHp = ModConfig.BASE_HP.get();
        var maxHealthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double currentBase = maxHealthAttr.getBaseValue();
            // 如果 baseValue 已被 NocturnalAstrolabeItem 设置为缩放血量（>= configBaseHp），保留
            // 仅当 baseValue 仍为 createAttributes 中的默认值 200.0 时才注入配置值
            if (currentBase < configMaxHp) {
                maxHealthAttr.setBaseValue(configMaxHp);
                // 首次生成时（tickCount < 2），同步刷新当前血量为满值
                // 区块重载时不自动回满，避免玩家打残 BOSS 后走远再回来又满血
                if (this.tickCount < 2) {
                    this.setHealth((float) configMaxHp);
                }
            }
        }
    }

    // ==================== 客户端追踪与血条同步 ====================

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
        syncManaToPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // 死亡演出期间血条闪烁
        if (isDying()) {
            this.bossEvent.setProgress(0.0F);
            this.bossEvent.setColor(BossEvent.BossBarColor.PINK);
        } else {
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        }
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public void remove(RemovalReason reason) {
        // BOSS 移除前，通知所有追踪玩家清除法力数据缓存
        // 必须在 super.remove() 之前发送，因为移除后实体不再被追踪
        // 防止法力条在 BOSS 脱战消失或死亡消散后仍残留在屏幕上
        if (!this.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    this,
                    new ClientboundStellaManaRemovePacket(this.getUUID())
            );
        }
        super.remove(reason);
        this.bossEvent.removeAllPlayers();
    }

    // ==================== 法力值系统（脏数据判差优化） ====================

    public ManaData getManaData() {
        return this.getData(ModAttachments.MANA.get());
    }

    // 上一次同步的法力值，用于脏数据判差
    private int lastSyncedMana = -1;
    private int lastSyncedMaxMana = -1;
    // 上一次同步的法力系统关闭状态，用于脏数据判差
    // 转阶段时 DATA_MANA_SYSTEM_DISABLED 从 false→true，必须同步到客户端隐藏法力条
    private boolean lastSyncedManaSystemDisabled = false;

    public void setCurrentMana(int mana) {
        // 边界检查：法力值不能为负
        mana = Math.max(0, mana);
        ManaData data = this.getManaData();
        data.setCurrentMana(mana);

        // 脏数据判差：数值或法力系统状态改变时才发送网络包
        // 防止 tick() 中无脑调用 set() 引发每秒 20 次的网络风暴
        // isManaSystemDisabled() 检查确保转阶段时法力系统关闭状态能同步到客户端
        boolean manaChanged = mana != lastSyncedMana || data.getMaxMana() != lastSyncedMaxMana;
        boolean systemStateChanged = isManaSystemDisabled() != lastSyncedManaSystemDisabled;
        if (manaChanged || systemStateChanged) {
            lastSyncedMana = mana;
            lastSyncedMaxMana = data.getMaxMana();
            lastSyncedManaSystemDisabled = isManaSystemDisabled();
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    this,
                    new ClientboundStellaManaPacket(this.getUUID(), data.getCurrentMana(), data.getMaxMana(), isManaSystemDisabled())
            );
        }
    }

    private void syncManaToPlayer(ServerPlayer player) {
        ManaData data = this.getManaData();
        lastSyncedMana = data.getCurrentMana();
        lastSyncedMaxMana = data.getMaxMana();
        lastSyncedManaSystemDisabled = isManaSystemDisabled();
        PacketDistributor.sendToPlayer(
                player,
                new ClientboundStellaManaPacket(this.getUUID(), data.getCurrentMana(), data.getMaxMana(), isManaSystemDisabled())
        );
    }

    // ==================== 核心 tick 逻辑 ====================

    @Override
    public void tick() {
        super.tick();

        // 首次 tick 时注入 ModConfig 配置的血量值
        // 必须在 tick 中执行，因为注册阶段配置文件尚未加载
        injectConfigHp();

        // Side 安全：使用 instanceof 模式匹配，避免 ClassCastException
        // 与项目其他位置的风格保持一致
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // ---- 死亡演出逻辑（最高优先级）----
        // 委托给 StellaDyingStateMachine 组件处理全部演出 tick 计数、粒子序列与音效时序
        if (isDying()) {
            dyingFSM.tick(serverLevel);
            return;
        }

        // ---- 转阶段演出逻辑 ----
        if (isTransitioning()) {
            transitionFSM.tick(serverLevel);
            return;
        }

        // ---- 边缘 case：防止活塞推走 ----
        // 如果 BOSS 偏离祭坛中心超过指定距离，强制瞬移回中心
        // 阈值与脱战检测半径对齐，消除灰色地带
        // 防止玩家借助活塞在安全距离内反复骚扰 BOSS
        if (altarCenterPos != null) {
            // 活塞保护距离从 ModConfig 读取，服主可在配置文件中调整
            double pistonProtectDist = ModConfig.PISTON_PROTECTION_RADIUS.get();
            double pistonProtectDistSq = pistonProtectDist * pistonProtectDist;
            double distToAltar = this.distanceToSqr(
                    altarCenterPos.getX() + 0.5,
                    this.getY(),
                    altarCenterPos.getZ() + 0.5
            );
            if (distToAltar > pistonProtectDistSq) {
                this.teleportTo(
                        altarCenterPos.getX() + 0.5,
                        this.getY(),
                        altarCenterPos.getZ() + 0.5
                );
            }
        }

        // ---- 脱战检测 ----
        anchorCheckTimer++;
        if (anchorCheckTimer >= ANCHOR_CHECK_INTERVAL) {
            anchorCheckTimer = 0;
            checkAnchorDespawn(serverLevel);
        }

        // ---- 半血转阶段检测 ----
        if (!transitionFSM.hasTransitioned() && getCombatPhase() == PHASE_1_CASTER) {
            if (this.getHealth() <= this.getMaxHealth() * 0.5) {
                transitionFSM.startTransition(serverLevel);
                return;
            }
        }

        // ---- 星门涌动：75%血量一次性触发 ----
        if (getCombatPhase() == PHASE_1_CASTER && !gateSurgeAbility.isTriggered()) {
            if (this.getHealth() <= this.getMaxHealth() * 0.75) {
                gateSurgeAbility.trigger(serverLevel);
            }
        }
        if (gateSurgeAbility.isActive()) {
            gateSurgeAbility.tick(serverLevel);
            return;
        }

        // ---- 一阶段逻辑 ----
        if (getCombatPhase() == PHASE_1_CASTER) {
            manaSystem.tick(serverLevel);
        }

        // ---- 二阶段逻辑 ----
        if (getCombatPhase() == PHASE_2_MELEE) {
            tickPhase2(serverLevel);
        }
    }

    // ==================== 死亡演出状态机 ====================

    // 拦截原版死亡逻辑，进入 DYING 演出状态
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && !isDying()) {
            // 不调用 super.die()，阻止原版侧倒消散
            // 进入自定义死亡演出
            LOGGER.info("StellaEvoker 进入死亡演出，位置: [{}, {}, {}]",
                    (int) this.getX(), (int) this.getY(), (int) this.getZ());
            this.entityData.set(DATA_IS_DYING, true);
            this.dyingFSM.startDying();

            // 停止所有正在运行的 AI Goal
            // 1.21.1 中 GoalSelector 没有 getRunningGoals()，使用 getAvailableGoals 过滤运行中的 Goal
            this.goalSelector.getAvailableGoals().stream()
                    .filter(WrappedGoal::isRunning)
                    .forEach(WrappedGoal::stop);

            // 播放凋灵死亡音效（低沉的终末感）
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.0F, 0.3F);
        }
    }

    // 死亡演出逻辑已迁移至 StellaDyingStateMachine 组件

    // 强制死亡：绕过 isDying() 检查，直接调用原版死亡逻辑
    // 仅供 StellaDyingStateMachine 死亡演出结束后调用
    // 原因：die() 中有 !isDying() 守卫，演出结束后若调用 die() 会重新进入演出，导致无限循环
    void forceDie() {
        super.die(this.damageSources().generic());
    }

    // 供 StellaDyingStateMachine 组件调用，触发战利品掉落
    // 封装 protected 的 dropFromLootTable，使组件可以访问
    void onFinishDyingDropLoot() {
        this.dropFromLootTable(this.damageSources().generic(), true);
        // 移除 BossBar，避免死亡后血条残留
        this.bossEvent.removeAllPlayers();
    }

    // 指定战利品表路径，由 JSON 配置掉落内容
    // 路径：data/astral_warfare/loot_table/entities/stella_evoker.json
    // 1.21.1 中返回类型为 ResourceKey<LootTable>
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "entities/stella_evoker"));
    }

    // ==================== 无敌判定 ====================

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (isTransitioning() || isDying() || isGateSurging()) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    // 死亡演出期间不可被杀死
    @Override
    public boolean isDeadOrDying() {
        if (isDying()) {
            return false;
        }
        return super.isDeadOrDying();
    }

    // ==================== 公共 API ====================

    // ==================== 脱战检测 ====================

    private void checkAnchorDespawn(ServerLevel level) {
        AABB checkBox = this.getBoundingBox().inflate(ANCHOR_CHECK_RADIUS);
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, checkBox,
                player -> player.isAlive() && !player.isSpectator());

        if (nearbyPlayers.isEmpty()) {
            spawnDespawnParticles(level);
            for (ServerPlayer trackedPlayer : this.bossEvent.getPlayers()) {
                trackedPlayer.sendSystemMessage(
                        Component.translatable("entity.astral_warfare.stella_evoker.despawn"));
            }
            this.bossEvent.removeAllPlayers();
            this.discard();
        }
    }

    private void spawnDespawnParticles(ServerLevel level) {
        try (ParticleEmitter emitter = new ParticleEmitter(this)) {
            for (int i = 0; i < 50; i++) {
                double px = this.getX() + (this.random.nextDouble() - 0.5) * 2.0;
                double py = this.getY() + this.random.nextDouble() * 2.0;
                double pz = this.getZ() + (this.random.nextDouble() - 0.5) * 2.0;
                emitter.add(StellaParticles.ID_STELLA_WISP, px, py, pz, 0);
            }
        }
    }

    // ==================== 二阶段 tick 逻辑 ====================

    private void tickPhase2(ServerLevel serverLevel) {
        if (this.tickCount % 2 == 0) {
            try (ParticleEmitter emitter = new ParticleEmitter(this)) {
                for (int j = 0; j < 2; j++) {
                    double angle = this.random.nextDouble() * Math.PI * 2;
                    double radius = 0.6 + this.random.nextDouble() * 0.4;
                    double px = this.getX() + Math.cos(angle) * radius;
                    double py = this.getY() + this.random.nextDouble() * 1.8;
                    double pz = this.getZ() + Math.sin(angle) * radius;
                    emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 2);
                }
            }
        }
    }

    // ==================== 二阶段状态恢复 ====================

    // 二阶段速度加成的 AttributeModifier 唯一标识
    private static final ResourceLocation PHASE2_SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "phase2_speed_bonus");

    // 统一的二阶段状态恢复方法（实现见文件末尾）

    // ==================== 公共 API ====================

    // 供组件访问 entityData
    public SynchedEntityData getEntityData() {
        return this.entityData;
    }

    // 包可见：供组件设置 moveControl（Mob.moveControl 是 protected 字段，外部无法直接赋值）
    void setMoveControl(MoveControl mc) {
        this.moveControl = mc;
    }

    // 包可见：供组件重建导航（Mob.navigation 和 createNavigation 是 protected，外部无法直接访问）
    // 转阶段后必须重建，一阶段飞行时 GroundPathNavigation 可能积累脏状态
    void reinitializeNavigation() {
        this.navigation = this.createNavigation(this.level());
    }

    public boolean isWeakened() {
        return manaSystem.isWeakened();
    }

    public int getWeakenedTicks() {
        return manaSystem.getWeakenedTicks();
    }

    public boolean isFallingFromExhaustion() {
        return manaSystem.isFalling();
    }

    public int getCombatPhase() {
        return this.entityData.get(DATA_COMBAT_PHASE);
    }

    public boolean isTransitioning() {
        return transitionFSM.isActive();
    }

    public boolean isManaSystemDisabled() {
        return this.entityData.get(DATA_MANA_SYSTEM_DISABLED);
    }

    public boolean isDying() {
        return this.entityData.get(DATA_IS_DYING);
    }

    // 星门涌动是否正在进行（状态1=升空，状态2=施法）
    public boolean isGateSurging() {
        return gateSurgeAbility.isActive();
    }

    // 包可见：供 StellaDyingStateMachine 在死亡演出结束时清除 DYING 标志
    // 不能直接访问 entityData（protected 字段，非子类不可见）
    void setDying(boolean dying) {
        this.entityData.set(DATA_IS_DYING, dying);
    }

    public int getSpellCooldown(SpellType spell) {
        return this.spellCastGoal.getSpellCooldown(spell);
    }

    // 设置祭坛中心坐标（用于防止活塞推走）
    public void setAltarCenterPos(BlockPos pos) {
        this.altarCenterPos = pos;
    }

    public BlockPos getAltarCenterPos() {
        return this.altarCenterPos;
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CombatPhase", getCombatPhase());
        tag.putBoolean("HasTransitioned", transitionFSM.hasTransitioned());
        tag.putBoolean("ManaSystemDisabled", isManaSystemDisabled());
        tag.putInt("WeakenedTicks", manaSystem.getWeakenedTimer());
        tag.putBoolean("IsFallingFromExhaustion", manaSystem.isFalling());
        tag.putBoolean("ImpactTriggered", manaSystem.isImpactTriggered());
        tag.putInt("CrystalManaRecoverTimer", manaSystem.getCrystalManaRecoverTimer());
        tag.putInt("AnchorCheckTimer", anchorCheckTimer);
        if (altarCenterPos != null) {
            tag.putInt("AltarCenterX", altarCenterPos.getX());
            tag.putInt("AltarCenterY", altarCenterPos.getY());
            tag.putInt("AltarCenterZ", altarCenterPos.getZ());
        }
        // 转阶段演出状态持久化：防止区块重载后 BOSS 卡一阶段
        tag.putBoolean("IsTransitioning", isTransitioning());
        tag.putInt("TransitionTimer", transitionFSM.getTransitionTimer());
        // 死亡演出状态持久化：防止区块卸载后 BOSS "复活"
        tag.putBoolean("IsDying", isDying());
        tag.putInt("DyingTimer", dyingFSM.writeToNbt());
        // ModConfig 血量注入标志持久化：防止区块重载后重复注入
        tag.putBoolean("ConfigHpInjected", configHpInjected);
        // 星门涌动触发标志持久化：防止区块重载后重复触发
        tag.putBoolean("GateSurgeTriggered", gateSurgeAbility.isTriggered());
        // 星门涌动状态机持久化：防止区块重载后状态丢失
        tag.putInt("GateSurgeState", gateSurgeAbility.getState());
        tag.putInt("GateSurgeTimer", gateSurgeAbility.getTimer());
        tag.putDouble("GateSurgeOriginY", gateSurgeAbility.getOriginY());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("CombatPhase")) {
            this.entityData.set(DATA_COMBAT_PHASE, tag.getInt("CombatPhase"));
        }
        if (tag.contains("HasTransitioned")) {
            transitionFSM.setHasTransitioned(tag.getBoolean("HasTransitioned"));
        }
        if (tag.contains("ManaSystemDisabled")) {
            this.entityData.set(DATA_MANA_SYSTEM_DISABLED, tag.getBoolean("ManaSystemDisabled"));
        }
        if (tag.contains("IsWeakened")) {
            this.entityData.set(DATA_IS_WEAKENED, tag.getBoolean("IsWeakened"));
        }
        if (tag.contains("WeakenedTicks")) {
            manaSystem.setWeakenedTimer(tag.getInt("WeakenedTicks"));
            this.entityData.set(DATA_WEAKENED_TICKS, manaSystem.getWeakenedTimer());
        }
        if (tag.contains("IsFallingFromExhaustion")) {
            // 恢复法力枯竭坠落状态：防止区块卸载后 BOSS 以满法力值复活
            manaSystem.setFallingFromExhaustion(tag.getBoolean("IsFallingFromExhaustion"));
        }
        if (tag.contains("ImpactTriggered")) {
            // 恢复冲击波触发状态：防止重载后重复触发冲击波
            manaSystem.setImpactTriggered(tag.getBoolean("ImpactTriggered"));
        }
        if (tag.contains("CrystalManaRecoverTimer")) {
            manaSystem.setCrystalManaRecoverTimer(tag.getInt("CrystalManaRecoverTimer"));
        }
        if (tag.contains("AnchorCheckTimer")) {
            anchorCheckTimer = tag.getInt("AnchorCheckTimer");
        }
        if (tag.contains("AltarCenterX")) {
            altarCenterPos = new BlockPos(
                    tag.getInt("AltarCenterX"),
                    tag.getInt("AltarCenterY"),
                    tag.getInt("AltarCenterZ")
            );
        }

        // 恢复转阶段演出状态：防止区块重载后 BOSS 卡一阶段
        if (tag.contains("IsTransitioning") && tag.getBoolean("IsTransitioning")) {
            this.entityData.set(DATA_IS_TRANSITIONING, true);
            transitionFSM.setTransitionTimer(tag.contains("TransitionTimer") ? tag.getInt("TransitionTimer") : 0);
            // 转阶段期间保持无重力
            this.setNoGravity(true);
        }

        // 恢复 ModConfig 血量注入标志：防止区块重载后重复注入
        if (tag.contains("ConfigHpInjected")) {
            configHpInjected = tag.getBoolean("ConfigHpInjected");
        }

        // 恢复星门涌动触发标志：防止区块重载后重复触发
        if (tag.contains("GateSurgeTriggered")) {
            gateSurgeAbility.setTriggered(tag.getBoolean("GateSurgeTriggered"));
        }
        // 恢复星门涌动状态机：如果正在施法中，继续执行
        if (tag.contains("GateSurgeState")) {
            gateSurgeAbility.setState(tag.getInt("GateSurgeState"));
        }
        if (tag.contains("GateSurgeTimer")) {
            gateSurgeAbility.setTimer(tag.getInt("GateSurgeTimer"));
        }
        if (tag.contains("GateSurgeOriginY")) {
            gateSurgeAbility.setOriginY(tag.getDouble("GateSurgeOriginY"));
        }

        // 恢复死亡演出状态：如果 BOSS 正在死亡演出中，重新激活
        // 防止区块卸载后 BOSS "复活"（满血重新出现）
        if (tag.contains("IsDying") && tag.getBoolean("IsDying")) {
            this.entityData.set(DATA_IS_DYING, true);
            int savedTimer = tag.contains("DyingTimer") ? tag.getInt("DyingTimer") : ModConstants.DYING_DURATION_TICKS;
            this.dyingFSM.readFromNbt(savedTimer);
            // 强制血量归零，防止血条短暂显示满血的视觉错误
            this.setHealth(0);
            // 死亡演出期间停止所有 AI
            // 使用临时列表避免并发修改：stop() 可能触发 GoalSelector 内部结构变更
            // 先收集所有运行中的 Goal，再逐一停止
            java.util.List<WrappedGoal> runningGoals =
                this.goalSelector.getAvailableGoals().stream()
                    .filter(WrappedGoal::isRunning)
                    .toList();
            for (WrappedGoal goal : runningGoals) {
                goal.stop();
            }
        }

        // 恢复二阶段状态
        if (getCombatPhase() == PHASE_2_MELEE) {
            this.setNoGravity(false);
            this.moveControl = new MoveControl(this);

            // restorePhase2State() 内部已调用 reinitializeNavigation()，无需重复调用
            restorePhase2State();
        }

        // 修复：死亡演出状态下读档时，强制血量归零防止血条闪满
        // 当区块在 BOSS 死亡演出期间卸载后重载，super.readAdditionalSaveData 可能恢复旧血量
        // 此检测确保死亡演出中的 BOSS 始终显示空血条
        if (this.isDying()) {
            this.setHealth(0.0F);
        }
    }

    // 统一的二阶段状态恢复方法
    // 被 finishTransition() 和 readAdditionalSaveData() 共同调用，避免代码重复
    // 包含：AI Goal 注册、速度修饰符添加
    //
    // 【二阶段 AI Goal 优先级与互斥关系】
    // 优先级1：DespairExecutionGoal（终结技）
    //   触发条件：CD结束 + BOSS血量≤35% + 10格内有玩家
    //   效果：击飞→瞬移上方→蓄力→下刺砸地
    //   不依赖任何一阶段效果
    //
    // 优先级2：Phase2MeleeGoal（三段普攻）
    //   触发条件：玩家在3格内
    //   效果：虚空弦斩→星界突进掌→背刺·虚空贯穿
    //   第三段施加虚空流血（DoT），绝不施加虚空禁锢
    //
    // 两者共享 MOVE+LOOK Flag，同一时刻只能运行一个
    // 恢复二阶段 AI 状态：清理一阶段 Goal 并注册二阶段专用 Goal
    // 被 readAdditionalSaveData() 和 StellaTransitionStateMachine.finishTransition() 共同调用
    // 包可见：供 StellaTransitionStateMachine 委托调用，消除代码重复（DRY 原则）
    void restorePhase2State() {
        // 清理所有 goalSelector 中的 Goal，防止 Raider 父类构造函数添加的 Goal 干扰
        // Raider 在构造函数中添加了 MeleeAttackGoal 等使用 MOVE+LOOK Flag 的 Goal
        // 这些 Goal 会与 Phase2MeleeGoal 产生 Flag 互斥，导致 BOSS 不追人
        // 必须全部清除后只注册二阶段专用 Goal
        this.goalSelector.getAvailableGoals().forEach(WrappedGoal::stop);
        this.goalSelector.getAvailableGoals().clear();

        this.phase2MeleeGoal = new Phase2MeleeGoal(this);
        this.despairExecutionGoal = new DespairExecutionGoal(this);
        // 优先级1：终结技（罕见特殊技，仅禁锢时触发）
        this.goalSelector.addGoal(1, this.despairExecutionGoal);
        // 优先级2：三段普攻（默认近战行为）
        this.goalSelector.addGoal(2, this.phase2MeleeGoal);

        // 重建导航：一阶段飞行时 GroundPathNavigation 可能积累脏状态
        // 重建确保二阶段地面寻路从干净状态开始
        this.reinitializeNavigation();

        // 添加二阶段速度修饰符：先移除同 ID 的旧修饰符，防止保存/加载后叠加
        var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(PHASE2_SPEED_MODIFIER_ID);
            speedAttr.addPermanentModifier(new AttributeModifier(
                    PHASE2_SPEED_MODIFIER_ID,
                    PHASE2_SPEED_MULTIPLIER - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
        }
    }
}
