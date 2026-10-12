package com.tac5studios.elementsclaims.integration;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.config.Features;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;

/** Rules for machines that work blocks without a player (used by the mixins). */
public final class MachineRules {

    private static Method tomsHasInv;
    private static boolean tomsLooked;

    private MachineRules() {}

    /**
     * May a machine standing at {@code from} work the block at {@code to}?
     * Yes when {@code to} is unclaimed, in the same claim, or in another claim of the same owner.
     * Machines inside a claim always run there; machines outside can't reach in.
     */
    public static boolean machineMayAct(Level level, BlockPos from, BlockPos to) {
        if (level == null || level.isClientSide() || !Features.on(Features.PROTECTION)) return true;
        Claim target = ClaimStore.at(level, to);
        if (target == null) return true;
        return Integrations.sameOwner(ClaimStore.at(level, from), target);
    }

    /** Create: the Create rule (machines inside always run, nothing from outside breaks in). */
    public static boolean createMayAct(Level level, BlockPos from, BlockPos to) {
        return !Integrations.createOn() || machineMayAct(level, from, to);
    }

    public static boolean crystalTools() {
        return Integrations.crystalToolsOn();
    }

    /** Tom's Simple Storage: may a connector at {@code from} link the container at {@code to}? */
    public static boolean storageMayLink(Level level, BlockPos from, BlockPos to) {
        return !Integrations.tomsOn() || machineMayAct(level, from, to);
    }

    /** Calls Tom's own inventory test (the call the mixin replaced). */
    public static boolean tomsHasInventory(Level level, BlockPos pos, BlockState state, Direction dir) {
        if (!tomsLooked) {
            tomsLooked = true;
            try {
                Class<?> c = Class.forName("com.tom.storagemod.inventory.PlatformInventoryAccess$BlockInventoryAccess");
                tomsHasInv = c.getMethod("hasInventoryAt", Level.class, BlockPos.class, BlockState.class, Direction.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                tomsHasInv = null;
            }
        }
        if (tomsHasInv == null) return false;
        try {
            return Boolean.TRUE.equals(tomsHasInv.invoke(null, level, pos, state, dir));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
