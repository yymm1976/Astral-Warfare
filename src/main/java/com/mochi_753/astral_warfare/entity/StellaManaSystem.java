package com.mochi_753.astral_warfare.entity;

import com.mochi_753.astral_warfare.attachment.ManaData;
import com.mochi_753.astral_warfare.client.particle.StellaParticles;
import com.mochi_753.astral_warfare.entity.ai.SpellType;
import com.mochi_753.astral_warfare.init.ModConstants;
import com.mochi_753.astral_warfare.network.ParticleEmitter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;


import java.util.List;

// StellaEvoker 法力值系统组件
// 从 StellaEvokerEntity 中剥离法力值系统逻辑，实现单一职责原则
// 组件内部管理全部法力值相关逻辑：消耗、恢复、虚弱、坠落
//
// 放在 entity 包中（而非 entity.ai），以便访问 StellaEvokerEntity 的包可见字段
public class StellaManaSystem {

    private static final int WEAKENED_DURATION_TICKS = ModConstants.WEAKENED_DURATION_TICKS;
    private static final float IMPACT_RADIUS = ModConstants.IMPACT_RADIUS;
    private static final float IMPACT_DAMAGE = ModConstants.IMPACT_DAMAGE;
    private static final int RECOVER_MANA = ModConstants.MANA_RECOVER_AMOUNT;
    private static final double CRYSTAL_MANA_RECOVER_RADIUS = ModConstants.CRYSTAL_MANA_RECOVER_RADIUS;
    private static final int CRYSTAL_MANA_RECOVER_AMOUNT = ModConstants.CRYSTAL_MANA_RECOVER_AMOUNT;
    private static final int CRYSTAL_MANA_RECOVER_INTERVAL = ModConstants.CRYSTAL_MANA_RECOVER_INTERVAL;

    private final StellaEvokerEntity evoker;
    private int weakenedTimer = 0;
    private boolean isFallingFromExhaustion = false;
    private boolean impactTriggered = false;
    private int crystalManaRecoverTimer = 0;
    private int passiveManaRegenTimer = 0;

    public StellaManaSystem(StellaEvokerEntity evoker) {
        this.evoker = evoker;
    }

    public void tick(ServerLevel serverLevel) {
        ManaData manaData = evoker.getManaData();
        int currentMana = manaData.getCurrentMana();

        if (isWeakened()) {
            weakenedTimer--;
            if (weakenedTimer % 20 == 0) {
                evoker.getEntityData().set(StellaEvokerEntity.DATA_WEAKENED_TICKS, weakenedTimer);
            }
            if (weakenedTimer <= 0) {
                recoverFromWeakened();
            }
        } else if (currentMana <= 0 && !isFallingFromExhaustion) {
            startFallingFromExhaustion();
        } else if (!isFallingFromExhaustion && currentMana > 0 && currentMana < ModConstants.MANA_EXHAUSTION_THRESHOLD) {
            boolean canCastAny = false;
            for (SpellType spell : SpellType.values()) {
                if (currentMana >= spell.manaCost && evoker.getSpellCooldown(spell) <= 0) {
                    canCastAny = true;
                    break;
                }
            }
            if (!canCastAny) {
                startFallingFromExhaustion();
            }
        }

        if (isFallingFromExhaustion && !impactTriggered && evoker.onGround()) {
            triggerImpactShockwave(serverLevel);
        }

        if (!isWeakened() && !isFallingFromExhaustion) {
            if (evoker.tickCount % 2 == 0) {
                try (ParticleEmitter emitter = new ParticleEmitter(evoker)) {
                    for (int j = 0; j < 2; j++) {
                        double angle = evoker.getRandom().nextDouble() * Math.PI * 2;
                        double radius = 0.8 + evoker.getRandom().nextDouble() * 0.5;
                        double px = evoker.getX() + Math.cos(angle) * radius;
                        double py = evoker.getY() + evoker.getRandom().nextDouble() * 1.5;
                        double pz = evoker.getZ() + Math.sin(angle) * radius;
                        emitter.add(StellaParticles.ID_STELLA_WISP, px, py, pz, 1);
                    }
                }
            }

            passiveManaRegenTimer++;
            if (passiveManaRegenTimer >= ModConstants.PASSIVE_MANA_REGEN_INTERVAL) {
                passiveManaRegenTimer = 0;
                if (currentMana < manaData.getMaxMana()) {
                    evoker.setCurrentMana(Math.min(currentMana + ModConstants.PASSIVE_MANA_REGEN_PER_TICK, manaData.getMaxMana()));
                }
            }

            crystalManaRecoverTimer++;
            if (crystalManaRecoverTimer >= CRYSTAL_MANA_RECOVER_INTERVAL) {
                crystalManaRecoverTimer = 0;
                recoverManaFromCrystals(serverLevel);
            }
        }
    }

    private void startFallingFromExhaustion() {
        isFallingFromExhaustion = true;
        impactTriggered = false;
        evoker.setNoGravity(false);
        evoker.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
    }

    private void triggerImpactShockwave(ServerLevel level) {
        impactTriggered = true;

        try (ParticleEmitter emitter = new ParticleEmitter(evoker)) {
            for (int i = 0; i < 30; i++) {
                double angle = evoker.getRandom().nextDouble() * Math.PI * 2;
                double radius = evoker.getRandom().nextDouble() * IMPACT_RADIUS;
                double px = evoker.getX() + Math.cos(angle) * radius;
                double pz = evoker.getZ() + Math.sin(angle) * radius;
                emitter.add(StellaParticles.ID_VOID_SPARK, px, evoker.getY() + 0.5, pz, 0);
                emitter.add(StellaParticles.ID_IMPACT_WAVE, px, evoker.getY() + 0.3, pz, 0);
            }
        }

        AABB impactBox = evoker.getBoundingBox().inflate(IMPACT_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, impactBox,
                entity -> entity != evoker && entity.isAlive());

        for (LivingEntity target : targets) {
            Vec3 knockbackDir = target.position().subtract(evoker.position()).normalize();
            target.knockback(1.5F, -knockbackDir.x, -knockbackDir.z);
            target.hurt(evoker.damageSources().mobAttack(evoker), IMPACT_DAMAGE);
        }

        enterWeakenedState();
    }

    private void enterWeakenedState() {
        weakenedTimer = WEAKENED_DURATION_TICKS;
        evoker.getEntityData().set(StellaEvokerEntity.DATA_IS_WEAKENED, true);
        evoker.getEntityData().set(StellaEvokerEntity.DATA_WEAKENED_TICKS, weakenedTimer);
        evoker.addEffect(new MobEffectInstance(MobEffects.GLOWING, WEAKENED_DURATION_TICKS, 0, false, false));
    }

    private void recoverManaFromCrystals(ServerLevel level) {
        AABB checkBox = evoker.getBoundingBox().inflate(CRYSTAL_MANA_RECOVER_RADIUS);

        List<AstralCrystalEntity> nearbyCrystals = level.getEntitiesOfClass(AstralCrystalEntity.class, checkBox,
                crystal -> crystal.isAlive());

        List<StarcoreGolemEntity> nearbyGolems = level.getEntitiesOfClass(StarcoreGolemEntity.class, checkBox,
                golem -> golem.isAlive() && golem.isCharged());

        int totalRecover = nearbyCrystals.size() * CRYSTAL_MANA_RECOVER_AMOUNT
                + nearbyGolems.size() * CRYSTAL_MANA_RECOVER_AMOUNT;

        if (totalRecover > 0) {
            ManaData manaData = evoker.getManaData();
            int newMana = Math.min(manaData.getCurrentMana() + totalRecover, manaData.getMaxMana());
            evoker.setCurrentMana(newMana);

            if (evoker.getRandom().nextFloat() < 0.3F) {
                try (ParticleEmitter emitter = new ParticleEmitter(evoker)) {
                    for (AstralCrystalEntity crystal : nearbyCrystals) {
                        emitter.add(StellaParticles.ID_ASTRAL_BEAM,
                                crystal.getX(), crystal.getY() + 0.5, crystal.getZ(), 1);
                    }
                    for (StarcoreGolemEntity golem : nearbyGolems) {
                        Vec3 golemPos = golem.position().add(0, 0.8, 0);
                        Vec3 bossPos = evoker.position().add(0, 0.8, 0);
                        Vec3 dir = bossPos.subtract(golemPos);
                        double dist = dir.length();
                        if (dist < 0.5) continue;
                        dir = dir.normalize();
                        int particleCount = (int) (dist / 1.5);
                        for (int i = 0; i < particleCount; i++) {
                            double t = (i + 0.5) / particleCount;
                            double px = golemPos.x + dir.x * dist * t + (evoker.getRandom().nextDouble() - 0.5) * 0.3;
                            double py = golemPos.y + dir.y * dist * t + (evoker.getRandom().nextDouble() - 0.5) * 0.3;
                            double pz = golemPos.z + dir.z * dist * t + (evoker.getRandom().nextDouble() - 0.5) * 0.3;
                            emitter.add(StellaParticles.ID_ASTRAL_BEAM, px, py, pz, 1);
                        }
                    }
                }
            }
        }
    }

    private void recoverFromWeakened() {
        evoker.getEntityData().set(StellaEvokerEntity.DATA_IS_WEAKENED, false);
        evoker.getEntityData().set(StellaEvokerEntity.DATA_WEAKENED_TICKS, 0);
        isFallingFromExhaustion = false;
        impactTriggered = false;
        evoker.setCurrentMana(RECOVER_MANA);
        evoker.setNoGravity(true);
        evoker.removeEffect(MobEffects.SLOW_FALLING);
    }

    public boolean isWeakened() {
        return evoker.getEntityData().get(StellaEvokerEntity.DATA_IS_WEAKENED);
    }

    public int getWeakenedTicks() {
        return evoker.getEntityData().get(StellaEvokerEntity.DATA_WEAKENED_TICKS);
    }

    public boolean isFalling() {
        return isFallingFromExhaustion;
    }

    // 从 NBT 恢复坠落状态：防止区块卸载后 BOSS 以满法力值复活
    public void setFallingFromExhaustion(boolean falling) {
        this.isFallingFromExhaustion = falling;
        if (falling) {
            evoker.setNoGravity(false);
        }
    }

    public boolean isImpactTriggered() {
        return impactTriggered;
    }

    // 从 NBT 恢复冲击波触发状态
    public void setImpactTriggered(boolean triggered) {
        this.impactTriggered = triggered;
    }

    public int getWeakenedTimer() {
        return weakenedTimer;
    }

    public void setWeakenedTimer(int timer) {
        this.weakenedTimer = timer;
    }

    public int getCrystalManaRecoverTimer() {
        return crystalManaRecoverTimer;
    }

    public void setCrystalManaRecoverTimer(int timer) {
        this.crystalManaRecoverTimer = timer;
    }

    public int getPassiveManaRegenTimer() {
        return passiveManaRegenTimer;
    }

    public void setPassiveManaRegenTimer(int timer) {
        this.passiveManaRegenTimer = timer;
    }
}
