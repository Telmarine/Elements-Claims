package com.tac5studios.elementsclaims.mixin;

import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.integration.MachineRules;
import com.tac5studios.elementsclaims.protect.Access;
import dev.willyelton.crystal_tools.common.levelable.tool.LevelableTool;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crystal Tools: area stripping sets blocks without an event. Each block needs Build there. */
@Pseudo
@Mixin(targets = "dev.willyelton.crystal_tools.utils.ToolUseUtils")
public abstract class CrystalToolsStripMixin {

    @Inject(method = "stripBlock", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void claims$strip(LevelableTool tool, Level level, ItemStack stack, Player player, BlockPos pos,
                                     InteractionHand hand, BlockState newState, CallbackInfo ci) {
        if (MachineRules.crystalTools() && player != null && !Access.check(player, level, pos, RankFlag.BUILD)) ci.cancel();
    }
}
