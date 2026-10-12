package com.tac5studios.elementsclaims.mixin;

import com.tac5studios.elementsclaims.integration.MachineRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Create stationary drills and saws: they always run inside their own claim, but can't break blocks across a claim border.
 * Create breaks blocks without a player, so no event fires. Skipped when Create isn't installed.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity")
public abstract class CreateDrillMixin {

    private static Field claims$breakingPos;
    private static boolean claims$looked;

    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void claims$canBreak(BlockState state, float hardness, CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        BlockPos target = claims$pos(self);
        if (target != null && !MachineRules.createMayAct(self.getLevel(), self.getBlockPos(), target)) cir.setReturnValue(false);
    }

    private static BlockPos claims$pos(Object self) {
        if (!claims$looked) {
            claims$looked = true;
            for (Class<?> c = self.getClass(); c != null && claims$breakingPos == null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField("breakingPos");
                    f.setAccessible(true);
                    claims$breakingPos = f;
                } catch (NoSuchFieldException ignored) {
                } catch (RuntimeException ignored) {
                    break;
                }
            }
        }
        try {
            return claims$breakingPos == null ? null : (BlockPos) claims$breakingPos.get(self);
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }
}
