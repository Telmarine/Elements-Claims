package com.tac5studios.elementsclaims.mixin;

import com.tac5studios.elementsclaims.protect.LandRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fluid Flow and Outside Griefing land flags: water and lava can't flow where the claim says no. */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Inject(method = "canSpreadTo", at = @At("HEAD"), cancellable = true)
    private void claims$canSpreadTo(BlockGetter level, BlockPos fromPos, BlockState fromState, Direction dir, BlockPos toPos,
                                    BlockState toState, FluidState toFluid, Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        if (!LandRules.fluidMaySpread(level, fromPos, toPos)) cir.setReturnValue(false);
    }
}
