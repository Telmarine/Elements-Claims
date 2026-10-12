package com.tac5studios.elementsclaims.menu;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.claim.Rank;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
import com.tac5studios.elementsclaims.ui.PagedMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Land Config → Player Management: every trusted player's head, highest rank first. Click to manage. */
public final class PlayersMenu extends PagedMenu<UUID> {

    private final Claim claim;

    public PlayersMenu(Claim claim) {
        super(Msg.menu("menu.players_title", "claim", claim.name()), Layout.SLOTS);
        this.claim = claim;
    }

    @Override
    protected boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return player instanceof ServerPlayer sp && Features.on(Features.MENU) && Features.on(Features.MENU_PLAYERS)
                && com.tac5studios.elementsclaims.claim.ClaimStore.byId(claim.id()) == claim
                && Manage.canManagePlayers(sp, claim);
    }

    @Override
    protected List<UUID> entries() {
        List<UUID> out = new ArrayList<>(claim.members().keySet());
        out.sort(Comparator.<UUID>comparingInt(u -> claim.ranks().indexOf(claim.rankOf(u)))
                .thenComparing(u -> Claiming.name(viewer.server, u), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    protected void drawEntry(UUID id, int slot) {
        Rank r = claim.rankOf(id);
        String name = Claiming.name(viewer.server, id);
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.menu("menu.player_rank", "rank", r.name()));
        lore.add(Msg.menu(viewer.server.getPlayerList().getPlayer(id) != null ? "menu.player_online" : "menu.player_offline"));
        if (Manage.canActOn(viewer, claim, id)) lore.add(Msg.menu("menu.player_click"));
        set(slot, Button.of(MenuItems.head(viewer.server, id, name, lore), (p, c) -> {
            if (Manage.canActOn(p, claim, id)) new PlayerMenu(claim, id).open(p);
        }));
    }

    @Override
    protected Component emptyText() {
        return Msg.menu("menu.players_empty");
    }

    @Override
    protected void drawNav() {
        navButton(0, Button.of(MenuItems.back(), (p, c) -> new LandConfigMenu(claim).open(p)));
    }
}
