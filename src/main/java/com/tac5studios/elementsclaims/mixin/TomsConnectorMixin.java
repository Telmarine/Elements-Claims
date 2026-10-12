package com.tac5studios.elementsclaims.mixin;

import com.tac5studios.elementsclaims.integration.MachineRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tom's Simple Storage: an Inventory Connector can't link containers in a claim owned by someone else.
 * Skipped when Tom's Simple Storage isn't installed.
 */
@Pseudo
@Mixin(targets = "com.tom.storagemod.block.entity.InventoryConnectorBlockEntity")
public abstract class TomsConnectorMixin {

    @Redirect(method = "detectTouchingInventories", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lcom/tom/storagemod/inventory/PlatformInventoryAccess$BlockInventoryAccess;hasInventoryAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"))
    private boolean claims$hasInventoryAt(Level level, BlockPos pos, BlockState state, Direction dir) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (!MachineRules.storageMayLink(level, self.getBlockPos(), pos)) return false;
        return MachineRules.tomsHasInventory(level, pos, state, dir);
    }
}
