package com.tac5studios.elementsclaims.mixin;

import com.tac5studios.elementsclaims.claim.LandFlag;
import com.tac5studios.elementsclaims.protect.LandRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Decay & Melt land flag: leaves decay only where the claim allows it. */
@Mixin(LeavesBlock.class)
public abstract class LeavesBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void claims$randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!LandRules.allows(level, pos, LandFlag.DECAY_MELT)) ci.cancel();
    }
}
