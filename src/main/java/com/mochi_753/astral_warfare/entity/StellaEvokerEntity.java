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
    {
        // 禁止原版 BossBGM 播放，使用自定义 BGM 系统替代
        // 防止原版凋灵/末影龙 BGM 与自定义 BGM 叠加
        bossEvent.setPlayBossMusic(false);
    }

    private int anchorCheckTimer = 0;

    // ==================== Phase 27：血量触发技能 ====================

    // 星轨迷宫：80% 血量一次性触发标志
    private boolean hasTriggeredStarTrackMaze = false;

    // 虚空裂隙：25% 血量后每 30 秒触发一次
    private static final int VOID_FISSURE_INTERVAL = 600; // 30秒 = 600 tick
    private int lastFissureSpawnTick = -VOID_FISSURE_INTERVAL; // 初始值确保首次可触发

    // ==================== GeckoLib 动画系统 ====================

    // 动画实例缓存：GeckoLib 要求每个 GeoEntity 实例拥有独立的缓存
    // 用于存储动画状态、控制器数据等，避免多实体间动画状态串扰
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // 行走动画标记：由 Phase2MeleeGoal 在冷却期/追击期设置
    // idle_controller 检测此标记切换到 walk 动画
    public boolean isWalking = false;

    // 一阶段待机动画的 RawAnimation 定义
    // 对应 animations/stella_evoker_idle_phase1.animation.json 中的 stella_evoker_idle_phase1
    // GeckoLib 4.7.3：使用 thenLoop() 而非 then(name, LoopType.LOOP)
    private static final RawAnimation IDLE_PHASE1_ANIM = RawAnimation.begin()
            .thenLoop("stella_evoker_idle_phase1");

    // 二阶段待机动画的 RawAnimation 定义
    // 对应 animations/stella_evoker_idle_phase2.animation.json 中的 stella_evoker_idle_phase2
    // 地面站立姿态：无浮动、持武器手臂、头部扫视、重心交替
    private static final RawAnimation IDLE_PHASE2_ANIM = RawAnimation.begin()
            .thenLoop("stella_evoker_idle_phase2");

    // 死亡动画的 RawAnimation 定义
    // 对应 animations/stella_evoker_death.animation.json 中的 stella_evoker_death
    // 使用 thenPlay() 播放一次性动画（不循环），播完后保持在最后一帧
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin()
            .thenPlay("stella_evoker_death");

    // 弦斩动画：右臂蓄力后横扫，身体微转
    private static final RawAnimation SLASH_ANIM = RawAnimation.begin()
            .thenPlay("stella_evoker_slash");

    // 突进掌动画：后仰蓄力→前倾刺出
    private static final RawAnimation THRUST_ANIM = RawAnimation.begin()
            .thenPlay("stella_evoker_thrust");

    // 背刺动画：蹲伏→消失→出现→双手穿刺
    private static final RawAnimation BACKSTAB_ANIM = RawAnimation.begin()
            .thenPlay("stella_evoker_backstab");

    // 处决下砸动画：双手持戟高举→急速下刺→砸地震退回弹
    private static final RawAnimation EXECUTION_SLAM_ANIM = RawAnimation.begin()
            .thenPlay("stella_evoker_execution_slam");

    // 转阶段动画：升空撑开→收缩碎裂→下坠展开→砸地缓冲
    private static final RawAnimation PHASE_TRANSITION_ANIM = RawAnimation.begin()
            .thenPlay("stella_evoker_phase_transition");

    // 施法动画：双臂举过头顶→保持→快速下挥
    private static final RawAnimation SPELL_CAST_ANIM = RawAnimation.begin()
            .thenPlay("stella_evoker_spell_cast");

    // 终结技击飞动画：下沉蓄力→猛然上挥（将玩家从地面掀起）
    private static final RawAnimation EXECUTION_LAUNCH_ANIM = RawAnimation.begin()
            .thenPlay("stella_evoker_execution_launch");

    // 行走动画：身体前倾8°，双腿交替±20°摆动
    private static final RawAnimation WALK_ANIM = RawAnimation.begin()
            .thenLoop("stella_evoker_walk");

    // GeckoLib 动画控制器注册
    //
    // 【Phase 27 根因修复】用 triggerableAnim + triggerAnim 替代 currentAttackAnim 字段
    // 根因：currentAttackAnim 是普通 Java 字段，服务端设置后不同步到客户端，
    //       客户端 attack_controller 回调始终看到 null，攻击/施法动画从未播放
    // 修复：GeckoLib 的 triggerAnim() 会发送网络包自动同步到客户端
    //
    // attack_controller（高优先级，后注册）：
    //   回调只处理 isTransitioning() → 播放转阶段动画
    //   其余动画通过 triggerableAnim 注册，由 AI Goal 调用 triggerAnim() 触发
    //   无触发动画时返回 STOP，idle_controller 接管
    //
    // idle_controller（低优先级，先注册）：
    //   isDying() → 死亡动画
    //   PHASE_2_MELEE + isWalking → 行走动画
    //   PHASE_2_MELEE → 二阶段待机
    //   否则 → 一阶段悬浮待机
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 待机控制器：过渡时间 10 tick（平滑切换）
        // 死亡动画例外：检测到 isDying() 时临时将过渡时间设为 0，确保跪地动作瞬间开始
        controllers.add(new AnimationController<>(this, "idle_controller", 10, state -> {
            if (this.isDying()) {
                state.getController().transitionLength(0);
                state.getController().setAnimation(DEATH_ANIM);
                return software.bernie.geckolib.animation.PlayState.CONTINUE;
            }
            if (this.getCombatPhase() == PHASE_2_MELEE) {
                if (this.isWalking) {
                    state.getController().setAnimation(WALK_ANIM);
                } else {
                    state.getController().setAnimation(IDLE_PHASE2_ANIM);
                }
                return software.bernie.geckolib.animation.PlayState.CONTINUE;
            }
            state.getController().setAnimation(IDLE_PHASE1_ANIM);
            return software.bernie.geckolib.animation.PlayState.CONTINUE;
        }));

        // 攻击/演出控制器：过渡时间 0（出招瞬间硬切）
        // 后注册 = 高优先级：攻击动画的骨骼变换覆盖待机动画
        // 回调只处理 isTransitioning()，其余通过 triggerableAnim 由 AI Goal 触发
        // triggerableAnim 注册后，调用 entity.triggerAnim("attack_controller", "trigger名") 即可播放
        // GeckoLib 自动发送网络包同步到客户端，解决 currentAttackAnim 不同步的根因
        controllers.add(new AnimationController<>(this, "attack_controller", 0, state -> {
            // 转阶段动画优先级最高：isTransitioning() 时强制播放
            if (this.isTransitioning()) {
                state.getController().setAnimation(PHASE_TRANSITION_ANIM);
                return software.bernie.geckolib.animation.PlayState.CONTINUE;
            }
            // 无攻击/演出动画触发：让 idle_controller 接管
            return software.bernie.geckolib.animation.PlayState.STOP;
        })
        // 注册 7 个可触发动画，对应 AI Goal 中的 triggerAnim() 调用
        .triggerableAnim("slash", SLASH_ANIM)
        .triggerableAnim("thrust", THRUST_ANIM)
        .triggerableAnim("backstab", BACKSTAB_ANIM)
        .triggerableAnim("execution_slam", EXECUTION_SLAM_ANIM)
        .triggerableAnim("spell_cast", SPELL_CAST_ANIM)
        .triggerableAnim("execution_launch", EXECUTION_LAUNCH_ANIM)
        .triggerableAnim("phase_transition", PHASE_TRANSITION_ANIM)
        );
    }

    // 返回动画实例缓存（GeoEntity 接口要求）
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // 施法 AI Goal：public 供 entity.ai 包中的 Goal 互相访问
    SpellCastGoal spellCastGoal;
    public Phase2MeleeGoal phase2MeleeGoal;
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

        // 禁飞镇压：每 10 tick 扫描 30 格内上升中的非创造玩家
        // 设计意图：BOSS 不允许玩家飞行/跳跃逃脱，空中玩家会被虚空力量猛砸回地面
        if (!isDying() && !isTransitioning() && this.tickCount % 10 == 0) {
            suppressFlyingPlayers();
        }
    }

    // 禁飞镇压：检测上升中的玩家，向下猛砸 + 魔法伤害
    // 条件：玩家在 30 格内、Y轴速度 > 0.3（上升中）、非创造/旁观模式
    // 效果：玩家被 setDeltaMovement(x, -1.5, z) 向下猛砸 + 10.0F 魔法伤害
    // 粒子：BOSS 手部向下爆发 ID_IMPACT_WAVE 粒子
    private static final double SUPPRESS_FLIGHT_RANGE = 30.0;
    private static final double SUPPRESS_FLIGHT_Y_THRESHOLD = 0.3;
    private static final float SUPPRESS_FLIGHT_DAMAGE = 10.0F;

    private void suppressFlyingPlayers() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        AABB searchBox = this.getBoundingBox().inflate(SUPPRESS_FLIGHT_RANGE);
        List<Player> flyingPlayers = serverLevel.getEntitiesOfClass(Player.class, searchBox,
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
                        && player.getDeltaMovement().y > SUPPRESS_FLIGHT_Y_THRESHOLD
        );

        if (flyingPlayers.isEmpty()) return;

        // BOSS 镇压粒子：手部向下爆发冲击波
        try (ParticleEmitter emitter = new ParticleEmitter(this)) {
            for (int i = 0; i < 8; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2;
                double r = 0.3 + this.random.nextDouble() * 0.5;
                double px = this.getX() + Math.cos(angle) * r;
                double pz = this.getZ() + Math.sin(angle) * r;
                // 手部高度约 Y+1.5，粒子向下扩散
                emitter.add(StellaParticles.ID_IMPACT_WAVE, px, this.getY() + 1.5, pz, 0);
            }
        }

        // 对每个飞行玩家施加镇压
        for (Player player : flyingPlayers) {
            // 向下猛砸：保留水平速度，Y 轴设为 -1.5
            player.setDeltaMovement(
                    player.getDeltaMovement().x,
                    -1.5,
                    player.getDeltaMovement().z
            );
            player.hurtMarked = true;
            // 魔法伤害（绕过盔甲）
            player.hurt(serverLevel.damageSources().indirectMagic(this, this), SUPPRESS_FLIGHT_DAMAGE);
        }
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

        // ---- 脱战检测（最高优先级，在所有 return 之前执行）----
        // 原因：如果脱战检测放在死亡/转阶段 return 之后，
        // BOSS 在死亡演出或转阶段期间玩家全部离开时不会消失，导致演出卡住
        anchorCheckTimer++;
        if (anchorCheckTimer >= ANCHOR_CHECK_INTERVAL) {
            anchorCheckTimer = 0;
            checkAnchorDespawn(serverLevel);
        }

        // ---- 死亡演出逻辑（最高优先级）----
        // 委托给 StellaDyingStateMachine 组件处理全部演出 tick 计数、粒子序列与音效时序
        if (isDying()) {
            dyingFSM.tick(serverLevel);
            // 死亡演出期间保持头部跟踪最近玩家，避免头部冻结
            lookAtNearestPlayer(serverLevel);
            return;
        }

        // ---- 转阶段演出逻辑 ----
        if (isTransitioning()) {
            transitionFSM.tick(serverLevel);
            // 转阶段期间保持头部跟踪最近玩家
            lookAtNearestPlayer(serverLevel);
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

        // ---- 星轨迷宫：80% 血量一次性触发 ----
        // 从法术轮换池移除，改为血量触发，确保玩家必见此中期演出
        if (!hasTriggeredStarTrackMaze && getCombatPhase() == PHASE_1_CASTER) {
            if (this.getHealth() <= this.getMaxHealth() * 0.8) {
                hasTriggeredStarTrackMaze = true;
                // 强制 SpellCastGoal 施放星轨迷宫
                if (this.spellCastGoal != null) {
                    this.spellCastGoal.forceCastSpell(SpellType.STAR_TRACK_MAZE);
                }
            }
        }

        // ---- 一阶段逻辑 ----
        if (getCombatPhase() == PHASE_1_CASTER) {
            manaSystem.tick(serverLevel);
        }

        // ---- 二阶段逻辑 ----
        if (getCombatPhase() == PHASE_2_MELEE) {
            tickPhase2(serverLevel);
            // ---- 虚空裂隙：25% 血量后每 30 秒自动生成 ----
            // 从终结技砸地触发改为血量触发，提供持续战场压力
            if (this.getHealth() <= this.getMaxHealth() * 0.25) {
                if (this.tickCount - this.lastFissureSpawnTick >= VOID_FISSURE_INTERVAL) {
                    this.lastFissureSpawnTick = this.tickCount;
                    if (this.despairExecutionGoal != null) {
                        this.despairExecutionGoal.spawnVoidFissures(serverLevel);
                    }
                }
            }
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
            // 先收集到临时列表再遍历停止，避免 stop() 修改 GoalSelector 内部状态导致 ConcurrentModificationException
            java.util.List<WrappedGoal> runningGoals = this.goalSelector.getAvailableGoals().stream()
                    .filter(WrappedGoal::isRunning)
                    .toList();
            for (WrappedGoal goal : runningGoals) {
                goal.stop();
            }

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

    // BOSS 环境伤害免疫：过滤火焰、爆炸、摔落、溺水、窒息
    // 设计意图：BOSS 是星穹级存在，不应被环境伤害击杀
    // 玩家只能通过主动战斗造成伤害，TNT/岩浆/摔落等环境因素无效
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 火焰伤害（火、岩浆、着火、岩浆块等）
        // 1.21.x 适配：isFire() 已移除，改用 DamageTypeTags.IS_FIRE
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) return false;
        // 爆炸伤害（TNT、末影水晶、床/重生锚爆炸等）
        // 1.21.x 适配：isExplosion() 已移除，改用 DamageTypeTags.IS_EXPLOSION
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return false;
        // 摔落伤害
        // 1.21.x 适配：isFall() 已移除，改用 DamageTypeTags.IS_FALL
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return false;
        // 溺水伤害
        if (source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)) return false;
        // 窒息伤害（卡在墙内）
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) return false;
        return super.hurt(source, amount);
    }

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

    // 终结技执行中判断：供 checkAnchorDespawn 调用
    // 终结技期间（WINDUP/LAUNCHING/ENTRAPMENT/TELEPORTING/CHARGING/SLAMMING）禁止脱战检测
    // 防止击飞玩家超出检测范围导致 BOSS 消失
    boolean isExecutingFinisher() {
        return this.despairExecutionGoal != null && this.despairExecutionGoal.isExecuting();
    }

    // 死亡/转阶段期间保持头部跟踪最近玩家
    // AI Goal 已停止，LookControl 不会收到 setLookAt 指令
    // 手动查找最近玩家并设置朝向，避免头部冻结在最后朝向
    private void lookAtNearestPlayer(ServerLevel level) {
        Player nearest = level.getNearestPlayer(this, 50.0);
        if (nearest != null) {
            this.getLookControl().setLookAt(nearest, 30.0F, 30.0F);
        }
    }

    private void checkAnchorDespawn(ServerLevel level) {
        // 终结技执行中禁止脱战检测：击飞玩家可能超出检测范围
        if (isExecutingFinisher()) return;

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
        tag.putInt("PassiveManaRegenTimer", manaSystem.getPassiveManaRegenTimer());
        tag.putInt("AnchorCheckTimer", anchorCheckTimer);
        // Phase 27：血量触发技能持久化
        tag.putBoolean("HasTriggeredStarTrackMaze", hasTriggeredStarTrackMaze);
        tag.putInt("LastFissureSpawnTick", lastFissureSpawnTick);
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
        if (tag.contains("PassiveManaRegenTimer")) {
            manaSystem.setPassiveManaRegenTimer(tag.getInt("PassiveManaRegenTimer"));
        }
        if (tag.contains("AnchorCheckTimer")) {
            anchorCheckTimer = tag.getInt("AnchorCheckTimer");
        }
        // Phase 27：恢复血量触发技能状态
        if (tag.contains("HasTriggeredStarTrackMaze")) {
            hasTriggeredStarTrackMaze = tag.getBoolean("HasTriggeredStarTrackMaze");
        }
        if (tag.contains("LastFissureSpawnTick")) {
            lastFissureSpawnTick = tag.getInt("LastFissureSpawnTick");
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
