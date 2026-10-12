package com.tac5studios.elementsclaims.mixin;

import com.tac5studios.elementsclaims.protect.LandRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Outside Griefing land flag: a dispenser outside a claim can't dispense into it (fluids, fire, TNT, shears...). */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {

    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void claims$dispense(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (!state.hasProperty(DispenserBlock.FACING)) return;
        BlockPos target = pos.relative(state.getValue(DispenserBlock.FACING));
        if (!LandRules.crossBorderAllowed(level, pos, target)) ci.cancel();
    }
}
