package com.tac5studios.elementsclaims.menu;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.ui.Menu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** A menu about one claim. Closes itself if the claim is deleted or the menu is switched off. */
public abstract class ClaimMenu extends Menu {

    protected final Claim claim;

    protected ClaimMenu(int rows, Component title, Claim claim) {
        super(rows, title);
        this.claim = claim;
    }

    /** True while the viewer may still use this menu. */
    protected abstract boolean allowed(ServerPlayer p);

    @Override
    protected boolean stillValid(Player player) {
        return player instanceof ServerPlayer sp && Features.on(Features.MENU)
                && ClaimStore.byId(claim.id()) == claim && allowed(sp);
    }
}
