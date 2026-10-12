package com.tac5studios.elementsclaims.mixin;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.tac5studios.elementsclaims.integration.MachineRules;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create contraption harvesters: a contraption assembled outside a claim can't work blocks inside it.
 * Contraptions assembled inside the claim always run there. Skipped when Create isn't installed.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour")
public abstract class CreateHarvesterMovementBehaviourMixin {

    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void claims$visit(MovementContext context, BlockPos pos, CallbackInfo ci) {
        if (context == null || context.contraption == null || context.contraption.anchor == null) return;
        if (!MachineRules.createMayAct(context.world, context.contraption.anchor, pos)) ci.cancel();
    }
}
