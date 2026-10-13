package com.tac5studios.elementsclaims.mixin;

import com.tac5studios.elementsclaims.claim.LandFlag;
import com.tac5studios.elementsclaims.protect.LandRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Decay & Melt land flag: ice melts only where the claim allows it.
 * Frosted ice from Frost Walker is skipped so it always melts back to water.
 */
@Mixin(IceBlock.class)
public abstract class IceBlockMixin {

    @Inject(method = "melt", at = @At("HEAD"), cancellable = true)
    private void claims$melt(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if ((Object) this instanceof FrostedIceBlock) return;
        if (!LandRules.allows(level, pos, LandFlag.DECAY_MELT)) ci.cancel();
    }
}
