package com.tac5studios.elementsclaims.subarea;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.menu.LandConfigMenu;
import com.tac5studios.elementsclaims.menu.MenuItems;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
import com.tac5studios.elementsclaims.ui.Icons;
import com.tac5studios.elementsclaims.ui.PagedMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Land Config → Sub-areas: every sub-area of the claim, plus the Sub-area Stick. */
public final class SubAreasMenu extends PagedMenu<SubArea> {

    private final Claim claim;

    public SubAreasMenu(Claim claim) {
        super(Msg.menu("subarea.list_title", "claim", claim.name()), Layout.SLOTS);
        this.claim = claim;
    }

    @Override
    protected boolean stillValid(Player player) {
        return player instanceof ServerPlayer sp && Features.on(Features.SUB_AREAS) && ClaimStore.byId(claim.id()) == claim
                && SubAreas.canManage(sp, claim);
    }

    @Override
    protected List<SubArea> entries() {
        return new ArrayList<>(claim.subAreas());
    }

    @Override
    protected void drawEntry(SubArea a, int slot) {
        BlockPos min = a.min(), max = a.max();
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.menu("subarea.box", "x1", min.getX(), "y1", min.getY(), "z1", min.getZ(), "x2", max.getX(), "y2", max.getY(), "z2", max.getZ()));
        lore.add(Msg.menu("subarea.blocks", "blocks", a.volume()));
        lore.add(Msg.menu("subarea.members_count", "count", a.members().size()));
        lore.add(Msg.menu("menu.player_click"));
        set(slot, Button.of(Icons.named(Items.OAK_SIGN, Msg.menu("subarea.name", "area", a.name()), lore),
                (p, c) -> new SubAreaMenu(claim, a).open(p)));
    }

    @Override
    protected Component emptyText() {
        return Msg.menu("subarea.empty");
    }

    @Override
    protected void drawNav() {
        navButton(0, Button.of(MenuItems.back(), (p, c) -> new LandConfigMenu(claim).open(p)));
        navButton(3, Button.of(Icons.named(Items.STICK, Msg.menu("subarea.get_stick"), List.of(Msg.menu("subarea.get_stick_info"))), (p, c) -> {
            if (!p.getInventory().add(SubAreas.stick())) p.drop(SubAreas.stick(), false);
            Msg.send(p, "stick.given");
            close();
        }));
    }
}
