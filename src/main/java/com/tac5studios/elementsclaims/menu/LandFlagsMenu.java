package com.tac5studios.elementsclaims.menu;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.LandFlag;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
import net.minecraft.server.level.ServerPlayer;

/** Land Config → Land Flags: claim-wide switches (SPEC section 4). */
public final class LandFlagsMenu extends ClaimMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public LandFlagsMenu(Claim claim) {
        super(4, Msg.menu("menu.land_flags_title", "claim", claim.name()), claim);
    }

    @Override
    protected boolean allowed(ServerPlayer p) {
        return Features.on(Features.MENU_LAND_FLAGS) && Manage.canManageLand(p, claim);
    }

    @Override
    protected void draw() {
        LandFlag[] flags = LandFlag.values();
        for (int i = 0; i < flags.length && i < SLOTS.length; i++) {
            LandFlag f = flags[i];
            set(SLOTS[i], Button.of(MenuItems.toggle(MenuItems.icon(f), Msg.raw("land_flag_names." + f.key()),
                    Msg.raw("land_flag_info." + f.key()), claim.flag(f), true), (p, c) -> {
                Manage.setLandFlag(p, claim, f, !claim.flag(f));
                refresh();
            }));
        }
        set(27, Button.of(MenuItems.back(), (p, c) -> new LandConfigMenu(claim).open(p)));
        for (int r = 0; r < rows(); r++) fillRow(r);
    }
}
