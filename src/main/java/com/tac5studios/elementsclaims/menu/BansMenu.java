package com.tac5studios.elementsclaims.menu;

import com.mojang.authlib.GameProfile;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
import com.tac5studios.elementsclaims.ui.ConfirmMenu;
import com.tac5studios.elementsclaims.ui.Icons;
import com.tac5studios.elementsclaims.ui.PagedMenu;
import com.tac5studios.elementsclaims.ui.TextInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Land Config → Bans: banned players (click to unban) and a button to ban someone by name. */
public final class BansMenu extends PagedMenu<UUID> {

    private final Claim claim;

    public BansMenu(Claim claim) {
        super(Msg.menu("menu.bans_title", "claim", claim.name()), Layout.SLOTS);
        this.claim = claim;
    }

    @Override
    protected boolean stillValid(Player player) {
        return player instanceof ServerPlayer sp && Features.on(Features.MENU) && Features.on(Features.MENU_BANS)
                && ClaimStore.byId(claim.id()) == claim && Manage.canBan(sp, claim);
    }

    @Override
    protected List<UUID> entries() {
        List<UUID> out = new ArrayList<>(claim.bans());
        out.sort((a, b) -> Claiming.name(viewer.server, a).compareToIgnoreCase(Claiming.name(viewer.server, b)));
        return out;
    }

    @Override
    protected void drawEntry(UUID id, int slot) {
        String name = Claiming.name(viewer.server, id);
        set(slot, Button.of(MenuItems.head(viewer.server, id, name, List.of(Msg.menu("menu.unban_click"))), (p, c) ->
                ConfirmMenu.ask(p, Icons.named(Items.PLAYER_HEAD, Msg.menu("menu.unban_confirm", "player", name)), List.of(), who -> {
                    if (Manage.unban(who, claim, id)) Msg.send(who, "menu.unbanned", "player", name, "claim", claim.name());
                    new BansMenu(claim).open(who);
                }, who -> new BansMenu(claim).open(who))));
    }

    @Override
    protected Component emptyText() {
        return Msg.menu("menu.bans_empty");
    }

    @Override
    protected void drawNav() {
        navButton(0, Button.of(MenuItems.back(), (p, c) -> new LandConfigMenu(claim).open(p)));
        navButton(3, Button.of(Icons.named(Items.BARRIER, Msg.menu("menu.ban_by_name"), List.of(Msg.menu("menu.ban_by_name_info"))), (p, c) ->
                TextInput.ask(p, Msg.menu("menu.ban_by_name_title"), "", (who, text) -> {
                    banByName(who, text);
                    new BansMenu(claim).open(who);
                }, who -> new BansMenu(claim).open(who))));
    }

    private void banByName(ServerPlayer p, String name) {
        if (name.isBlank() || ClaimStore.byId(claim.id()) != claim) return;
        ServerPlayer online = p.server.getPlayerList().getPlayerByName(name);
        GameProfile profile = online != null ? online.getGameProfile()
                : p.server.getProfileCache() == null ? null : p.server.getProfileCache().get(name).orElse(null);
        if (profile == null) {
            Msg.send(p, "menu.player_unknown", "player", name);
            return;
        }
        if (Manage.ban(p, claim, profile.getId())) {
            Msg.send(p, "menu.banned", "player", profile.getName(), "claim", claim.name());
            if (online != null) Msg.send(online, "menu.banned_target", "claim", claim.name());
        } else {
            Msg.send(p, "menu.cant_ban", "player", profile.getName());
        }
    }
}
