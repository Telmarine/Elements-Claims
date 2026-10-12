package com.tac5studios.elementsclaims.mixin;

import com.tac5studios.elementsclaims.claim.LandFlag;
import com.tac5studios.elementsclaims.protect.LandRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fire Spread land flag: fire doesn't spread into, or burn blocks in, claims with it off. */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private void claims$igniteOdds(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (level instanceof Level l && !LandRules.allows(l, pos, LandFlag.FIRE_SPREAD)) cir.setReturnValue(0);
    }

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void claims$burnOut(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face, CallbackInfo ci) {
        if (!LandRules.allows(level, pos, LandFlag.FIRE_SPREAD)) ci.cancel();
    }
}
