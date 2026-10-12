package com.tac5studios.elementsclaims.claim;

import java.util.Locale;

/**
 * Claim-wide flags set by the land owner (SPEC section 4).
 * Stored with every claim from 1.0.0 Phase 1; enforced from Phase 3.
 */
public enum LandFlag {
    PVP(false),
    EXPLOSIONS(false),
    MOB_GRIEFING(false),
    OUTSIDE_GRIEFING(false),
    FIRE_SPREAD(false),
    FLUID_FLOW(true),
    MONSTER_SPAWNING(true),
    ANIMAL_SPAWNING(true),
    CROP_TRAMPLING(false),
    GROWTH(true),
    DECAY_MELT(true),
    TITLES(true);

    private final boolean def;

    LandFlag(boolean def) {
        this.def = def;
    }

    public boolean defaultValue() {
        return def;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LandFlag byKey(String key) {
        try {
            return valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
