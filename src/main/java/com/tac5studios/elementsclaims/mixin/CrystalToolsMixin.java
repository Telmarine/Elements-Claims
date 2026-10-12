package com.tac5studios.elementsclaims.mixin;

import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.integration.MachineRules;
import com.tac5studios.elementsclaims.protect.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crystal Tools: vein mining, 3x3 mining and tree chopping break extra blocks without any event.
 * Each extra block now needs Build there. Skipped when Crystal Tools isn't installed.
 */
@Pseudo
@Mixin(targets = "dev.willyelton.crystal_tools.common.levelable.tool.LevelableTool")
public abstract class CrystalToolsMixin {

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void claims$breakBlock(ItemStack tool, Level level, BlockPos pos, LivingEntity entity, CallbackInfo ci) {
        if (!MachineRules.crystalTools()) return;
        if (entity instanceof Player p ? !Access.check(p, level, pos, RankFlag.BUILD) : !MachineRules.machineMayAct(level, entity == null ? pos : entity.blockPosition(), pos)) {
            ci.cancel();
        }
    }
}
