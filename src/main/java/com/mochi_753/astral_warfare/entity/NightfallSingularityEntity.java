package com.mochi_753.astral_warfare.entity;

import com.mochi_753.astral_warfare.init.ModConstants;
import com.mochi_753.astral_warfare.init.ModEffects;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

// 夜幕黑洞实体 - 星穹唤星者的法术引力触点
// 不可见的 Misc 实体，持续吸引 8 格范围内的玩家
// 中心点（2 格内）的玩家被施加虚空禁锢效果并受到窒息伤害
// 存活时间由施法时长决定，施法结束后自动消散
//
// 性能优化：
//   1. 扫描频率从每 tick 降为每 4 tick（减少 75% 的 getEntitiesOfClass 调用）
//   2. 使用平滑吸引力公式：Velocity = (Target - Current) / Distance^2
//   3. 粒子密度通过随机数控制，避免低配电脑卡死
public class NightfallSingularityEntity extends Entity {

    // 引力吸引范围
    private static final double PULL_RADIUS = 8.0;
    // 中心禁锢范围
    private static final double CENTER_RADIUS = 2.0;
    // 中心窒息伤害
    // 修复：从5.0F提升到8.0F（再加强50%），黑洞中心应有高威胁伤害
    private static final float SUFFOCATION_DAMAGE = 8.0F;
    // 伤害间隔（每 20 tick = 1 秒）
    private static final int DAMAGE_INTERVAL = 20;
    // 玩家扫描间隔（每 4 tick 扫描一次，降低 75% CPU 开销）
    private static final int SCAN_INTERVAL = 4;
    // 超时自毁时间（200 tick = 10 秒）
    // 黑洞法术施法时长为 80 tick，加上安全余量，超过此时间自动消散
    // 防止施法异常终止（目标丢失、区块卸载等）导致黑洞残留
    private static final int MAX_LIFETIME = 200;
    // 最小距离平方阈值：当玩家距黑洞极近时，吸引力不再继续增大
    // 避免极近距离下 1/distSq 产生极大值导致玩家被弹射
    private static final double MIN_DIST_SQ = 1.0;
    // 最大吸引力：限制玩家每 tick 最大位移量，防止弹射
    private static final double MAX_PULL_STRENGTH = 0.3;
    // 施法者（用于伤害归因）
    private LivingEntity caster;
    // 施法者 UUID（用于 NBT 持久化，存档重载后通过 UUID 恢复施法者引用）
    private UUID casterUUID;

    private int damageTimer = 0;
    // 扫描计数器，控制 getEntitiesOfClass 调用频率
    private int scanTimer = 0;
    // 缓存扫描结果，在两次扫描之间复用
    // 使用 Collections.emptyList() 初始化，避免 List.of() 的不可变列表在意外修改时抛出异常
    private List<Player> cachedNearbyPlayers = java.util.Collections.emptyList();

    public NightfallSingularityEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setInvisible(true);
        // 使用 noPhysics = true 实现无物理碰撞
        // 架构师终审裁决：允许保留此字段访问，1.21.x 生命周期内安全
        // 替代之前的 move() 重写方案，因为完全重写 move() 不调用 super
        // 会破坏其他模组的碰撞检测与实体跟踪
        // 字段依赖说明：Entity.noPhysics 自 1.17 起为 protected，从未被 private 化
        // 若未来版本将其改为 private，替代方案搜索方向：
        //   1. Mixin @Accessor 暴露字段访问
        //   2. 重写 move() 为空操作（需评估对其他模组的影响）
        this.noPhysics = true;
    }

    // 设置施法者（用于伤害归因）
    public void setCaster(LivingEntity caster) {
        this.caster = caster;
        this.casterUUID = caster != null ? caster.getUUID() : null;
    }

    // 1.21.1 中 defineSynchedData 使用 SynchedEntityData.Builder 参数
    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        // NightfallSingularity 没有自定义同步数据
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 从 NBT 恢复施法者 UUID
        if (tag.hasUUID("CasterUUID")) {
            this.casterUUID = tag.getUUID("CasterUUID");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 持久化施法者 UUID，确保存档重载后伤害归因正确
        if (this.casterUUID != null) {
            tag.putUUID("CasterUUID", this.casterUUID);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        // 使用 instanceof 模式匹配安全转换，替代直接强转
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 延迟恢复施法者引用：NBT 加载后 caster 为 null，但 casterUUID 已恢复
        // 通过 ServerLevel.getEntity(UUID) 查找实体，确保伤害归因正确
        if (this.caster == null && this.casterUUID != null) {
            Entity entity = serverLevel.getEntity(this.casterUUID);
            if (entity instanceof LivingEntity living) {
                this.caster = living;
            }
        }

        // 施法者存活断言：施法者已死亡或不在同一维度时，黑洞自毁
        // 防止施法者死亡后黑洞仍持续造成伤害归因错误
        if (this.caster == null || !this.caster.isAlive() || this.caster.level() != this.level()) {
            this.discard();
            return;
        }

        // 超时自毁兜底：超过最大存活时间自动消散
        // 防止施法异常终止（AI stop、区块卸载等）导致黑洞永久残留
        if (this.tickCount >= MAX_LIFETIME) {
            this.discard();
            return;
        }

        // 黑洞特效（Phase 29 重设计：四层结构 + 微弱光晕）
        // 视觉设计：
        //   1. 核心（r<1.5）：8个 ID_DYING_EMBER variant=2 近黑色粒子，缓慢向外扩散→消失
        //   2. 事件视界（r=1.5-3.0）：16个 ID_VOID_SPARK variant=0 暗紫，双层反向旋转密集环
        //   3. 吸积盘（r=3.0-8.0）：24个 ID_ASTRAL_BEAM variant=0 亮蓝白，逆时针旋转
        //   4. 引力透镜环（r=8.0-16.0）：每2tick 8个 ID_VOID_SPARK variant=2 极暗，极慢旋转
        //   5. 微弱光晕：4个 ID_STELLA_WISP variant=0 缓慢上升，替代喷流
        //
        // 所有粒子都有朝向核心的运动，增强"吸入"感
        // 使用 ParticleEmitter 批量发送粒子包，减少网络开销
        try (ParticleEmitter emitter = new ParticleEmitter(this)) {

        // === 1. 核心（每tick 8个近黑色粒子，r<1.5，缓慢向外扩散→消失）===
        // 核心粒子密集且暗，像"吞噬光线的黑洞中心"
        // DYING_EMBER variant=2（近黑色），半径1.5内
        for (int i = 0; i < 8; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double r = this.random.nextDouble() * 1.5;
            double px = this.getX() + Math.cos(angle) * r;
            double pz = this.getZ() + Math.sin(angle) * r;
            double py = this.getY() + 0.5 + (this.random.nextDouble() - 0.5) * 0.3;
            emitter.add(StellaParticles.ID_DYING_EMBER, px, py, pz, 2);
        }

        // === 2. 事件视界（每tick 16个暗紫粒子，双层反向旋转密集环，r=1.5-3.0）===
        // 双层反向旋转：内层顺时针、外层逆时针，营造"时空扭曲"感
        // VOID_SPARK variant=0（暗紫色），密集环
        double evRotation = this.tickCount * 0.08;
        for (int i = 0; i < 16; i++) {
            double baseAngle = i * Math.PI * 2.0 / 16;
            // 内层（r≈1.8）：顺时针旋转
            double innerAngle = baseAngle + evRotation;
            double innerR = 1.5 + this.random.nextDouble() * 0.5;
            emitter.add(StellaParticles.ID_VOID_SPARK,
                    this.getX() + Math.cos(innerAngle) * innerR,
                    this.getY() + 0.5 + (this.random.nextDouble() - 0.5) * 0.1,
                    this.getZ() + Math.sin(innerAngle) * innerR,
                    0);
            // 外层（r≈2.5）：逆时针旋转
            double outerAngle = baseAngle - evRotation * 0.7;
            double outerR = 2.2 + this.random.nextDouble() * 0.8;
            emitter.add(StellaParticles.ID_VOID_SPARK,
                    this.getX() + Math.cos(outerAngle) * outerR,
                    this.getY() + 0.5 + (this.random.nextDouble() - 0.5) * 0.1,
                    this.getZ() + Math.sin(outerAngle) * outerR,
                    0);
        }

        // === 3. 吸积盘（每tick 24个亮蓝白粒子，逆时针旋转，r=3.0-8.0）===
        // 吸积盘是黑洞最亮的部分，用亮蓝白色粒子
        // ASTRAL_BEAM variant=0（亮蓝白），逆时针旋转
        double diskRotation = this.tickCount * 0.03;
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI * 2.0 / 24 + diskRotation + this.random.nextDouble() * 0.2;
            double r = 3.0 + this.random.nextDouble() * 5.0;
            double px = this.getX() + Math.cos(angle) * r;
            double pz = this.getZ() + Math.sin(angle) * r;
            double py = this.getY() + 0.5 + (this.random.nextDouble() - 0.5) * 0.15;
            emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 0);
        }

        // === 4. 引力透镜环（每2tick 8个极暗粒子，极慢旋转，r=8.0-16.0）===
        // 外围环带，极稀疏极暗，像"光线被弯曲"的视觉暗示
        // VOID_SPARK variant=2（极暗），每2tick生成
        if (this.tickCount % 2 == 0) {
            double lensRotation = this.tickCount * 0.01;
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI * 2.0 / 8 + lensRotation + this.random.nextDouble() * 0.3;
                double r = 8.0 + this.random.nextDouble() * 8.0;
                double px = this.getX() + Math.cos(angle) * r;
                double pz = this.getZ() + Math.sin(angle) * r;
                double py = this.getY() + 0.5 + (this.random.nextDouble() - 0.5) * 0.4;
                emitter.add(StellaParticles.ID_VOID_SPARK, px, py, pz, 2);
            }
        }

        // === 5. 微弱光晕（4个 STELLA_WISP 缓慢上升，替代喷流）===
        // 垂直于吸积盘平面的微弱光晕，替代原来的上下喷流
        // STELLA_WISP variant=0（淡紫色），缓慢上升
        for (int i = 0; i < 4; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double r = this.random.nextDouble() * 1.0;
            double px = this.getX() + Math.cos(angle) * r;
            double pz = this.getZ() + Math.sin(angle) * r;
            double py = this.getY() + 0.5 + this.random.nextDouble() * 1.5;
            emitter.add(StellaParticles.ID_STELLA_WISP, px, py, pz, 0);
        }

        } // end ParticleEmitter

        // 扫描计数器：每 4 tick 执行一次高消耗的 getEntitiesOfClass
        scanTimer++;
        if (scanTimer >= SCAN_INTERVAL) {
            scanTimer = 0;
            // 执行扫描并缓存结果
            AABB pullBox = this.getBoundingBox().inflate(PULL_RADIUS);
            cachedNearbyPlayers = serverLevel.getEntitiesOfClass(Player.class, pullBox,
                    player -> player.isAlive() && !player.isSpectator());
        }

        // 使用缓存的玩家列表处理吸引逻辑
        for (Player player : cachedNearbyPlayers) {
            // 玩家可能已死亡或传送走，需要重新验证
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            // B-14修复：缓存玩家可能已超出拉拽范围（4 tick 缓存期间玩家可能走远）
            // 超出 PULL_RADIUS 的玩家跳过，避免对远处玩家施加残余拉力
            if (player.distanceToSqr(this) > PULL_RADIUS * PULL_RADIUS) {
                continue;
            }

            double distToCenter = player.distanceTo(this);

            if (distToCenter <= CENTER_RADIUS && distToCenter > 0.1) {
                // 中心区域：施加虚空禁锢效果
                // 仅在玩家身上没有虚空禁锢效果时施加，避免黑洞每 tick 都刷新禁锢时长
                // ModEffects.VOID_ENTRAPMENT 是 DeferredHolder，直接作为 Holder<MobEffect> 使用
                if (!player.hasEffect(ModEffects.VOID_ENTRAPMENT)) {
                    player.addEffect(new MobEffectInstance(
                            ModEffects.VOID_ENTRAPMENT,
                            40, 0, false, true, true
                    ));
                }

                // 窒息伤害
                damageTimer++;
                if (damageTimer >= DAMAGE_INTERVAL) {
                    damageTimer = 0;
                    // 施法者存活检查：防止施法者已死亡后仍造成伤害归因错误
                    // caster 可能在 NBT 恢复后指向已死亡实体，isAlive() 确保归因有效
                    if (caster != null && caster.isAlive()) {
                        // 使用魔法伤害源绕过盔甲减免
                        player.hurt(serverLevel.damageSources().indirectMagic(caster, caster), SUFFOCATION_DAMAGE);
                    } else {
                        player.hurt(serverLevel.damageSources().magic(), SUFFOCATION_DAMAGE);
                        // 施法者已死亡，清空引用避免后续 tick 重复检查
                        caster = null;
                    }
                }
            } else if (distToCenter > CENTER_RADIUS) {
                // 外围区域：施加平滑吸引力
                // 使用公式：Velocity = (Target - Current) / Distance^2
                // 距离越近吸引力越强，距离越远越弱，形成平滑渐进效果
                // 距离平方低于阈值时，直接使用最大吸引力，避免极近距离弹射
                // 性能优化：全程使用 double 基础类型运算，避免每 tick 创建临时 Vec3 对象
                double dx = this.getX() - player.getX();
                double dy = this.getY() - player.getY();
                double dz = this.getZ() - player.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > 0.01) {
                    double effectiveDistSq = Math.max(distSq, MIN_DIST_SQ);
                    double pullStrength = Math.min(MAX_PULL_STRENGTH, ModConstants.PULL_STRENGTH_FACTOR / effectiveDistSq);
                    double invDist = 1.0 / Math.sqrt(effectiveDistSq);
                    // 获取当前速度并直接计算新速度，避免中间 Vec3 对象
                    Vec3 cur = player.getDeltaMovement();
                    player.setDeltaMovement(
                        cur.x + dx * invDist * pullStrength,
                        cur.y + dy * invDist * pullStrength,
                        cur.z + dz * invDist * pullStrength
                    );
                    // hurtMarked = true 通知客户端同步速度变更
                    // 这是 Minecraft 原版中处理"服务端修改客户端实体速度"的标准惯用法
                    // 1.21.1 中无替代公共 API，若未来字段权限变更需迁移到网络包方案
                    player.hurtMarked = true;
                }
            }
        }
    }
}
