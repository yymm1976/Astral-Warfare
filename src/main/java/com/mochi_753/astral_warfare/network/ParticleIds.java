package com.mochi_753.astral_warfare.network;

/**
 * Side-neutral particle ID constants.
 *
 * <p>Mirrors the ID constants from {@code client.particle.StellaParticles} so that
 * server-side code (entities, effects, AI goals) can reference particle type IDs
 * without triggering client-only class loading. The original StellaParticles class
 * transitively imports {@code ClientLevel} and other client-only types, which causes
 * {@link NoClassDefFoundError} on dedicated servers when referenced from
 * server-side packages.</p>
 *
 * <p>Values here must stay in sync with {@code StellaParticles} and
 * {@code ClientboundLodestoneParticlePacket.particleTypeId}.</p>
 */
public final class ParticleIds {

    private ParticleIds() {}

    public static final int ID_STELLA_WISP       = 0;
    public static final int ID_VOID_SPARK        = 1;
    public static final int ID_ASTRAL_BEAM       = 2;
    public static final int ID_IMPACT_WAVE       = 3;
    public static final int ID_DYING_EMBER       = 4;
    public static final int ID_TRANSITION_BURST  = 5;
    public static final int ID_VOID_TWINKLE      = 6;
    public static final int ID_VOID_THREAD       = 7;
}
