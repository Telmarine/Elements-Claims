package com.tac5studios.elementsclaims.protect;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.LandFlag;
import com.tac5studios.elementsclaims.config.Features;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/**
 * Land flag checks used by the world (fluids, fire, decay, pistons, dispensers, explosions).
 * Kept small and allocation-free because some run on every fluid or fire tick.
 */
public final class LandRules {

    private LandRules() {}

    /** Is this land flag on at this position? Unclaimed land and switched-off flags always say yes. */
    public static boolean allows(Level level, BlockPos pos, LandFlag flag) {
        if (level == null || level.isClientSide() || !Features.landFlagOn(flag)) return true;
        Claim c = ClaimStore.at(level, pos);
        return c == null || c.flag(flag);
    }

    /**
     * Something at {@code from} acts on {@code to}: blocked when {@code to} is in a claim with Outside Griefing off
     * and {@code from} is not in that same claim.
     */
    public static boolean crossBorderAllowed(Level level, BlockPos from, BlockPos to) {
        if (level == null || level.isClientSide() || !Features.landFlagOn(LandFlag.OUTSIDE_GRIEFING)) return true;
        Claim target = ClaimStore.at(level, to);
        if (target == null || target.flag(LandFlag.OUTSIDE_GRIEFING)) return true;
        return ClaimStore.at(level, from) == target;
    }

    /** Fluid flowing from one block to the next. */
    public static boolean fluidMaySpread(BlockGetter getter, BlockPos from, BlockPos to) {
        if (!(getter instanceof Level level) || level.isClientSide()) return true;
        if (!Features.on(Features.LAND_FLAGS)) return true;
        Claim target = ClaimStore.at(level, to);
        if (target == null) return true;
        if (Features.landFlagOn(LandFlag.FLUID_FLOW) && !target.flag(LandFlag.FLUID_FLOW)) return false;
        return crossBorderAllowed(level, from, to);
    }
}
