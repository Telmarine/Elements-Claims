package com.tac5studios.elementsclaims.menu;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.claim.Rank;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
import com.tac5studios.elementsclaims.ui.ConfirmMenu;
import com.tac5studios.elementsclaims.ui.Icons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** One trusted player: promote, demote, remove, ban. */
public final class PlayerMenu extends ClaimMenu {

    private final UUID target;

    public PlayerMenu(Claim claim, UUID target) {
        super(3, Msg.menu("menu.player_title", "claim", claim.name()), claim);
        this.target = target;
    }

    @Override
    protected boolean allowed(ServerPlayer p) {
        return Features.on(Features.MENU_PLAYERS) && claim.isMember(target) && Manage.canActOn(p, claim, target);
    }

    @Override
    protected void draw() {
        ServerPlayer p = viewer;
        String name = Claiming.name(p.server, target);
        Rank r = claim.rankOf(target);
        set(4, Button.display(MenuItems.head(p.server, target, name, List.of(Msg.menu("menu.player_rank", "rank", r.name())))));

        set(11, Button.of(Icons.named(Items.LIME_DYE, Msg.menu("menu.promote"), List.of(Msg.menu("menu.promote_info"))), (pl, c) -> {
            Rank to = Manage.promote(pl, claim, target);
            if (to != null) {
                Msg.send(pl, "menu.promoted", "player", name, "rank", to.name());
                notifyTarget(pl, "menu.promoted_target", to);
            } else {
                Msg.send(pl, "menu.cant_promote", "player", name);
            }
            refresh();
        }));
        set(13, Button.of(Icons.named(Items.ORANGE_DYE, Msg.menu("menu.demote"), List.of(Msg.menu("menu.demote_info"))), (pl, c) -> {
            Rank to = Manage.demote(pl, claim, target);
            if (to != null) {
                Msg.send(pl, "menu.demoted", "player", name, "rank", to.name());
                notifyTarget(pl, "menu.demoted_target", to);
            } else {
                Msg.send(pl, "menu.cant_demote", "player", name);
            }
            refresh();
        }));
        set(15, Button.of(Icons.named(Items.RED_DYE, Msg.menu("menu.remove"), List.of(Msg.menu("menu.remove_info"))), (pl, c) ->
                ConfirmMenu.ask(pl, Icons.named(Items.RED_DYE, Msg.menu("menu.remove_confirm", "player", name)), List.of(), who -> {
                    if (Manage.remove(who, claim, target)) Msg.send(who, "trust.removed", "player", name, "claim", claim.name());
                    new PlayersMenu(claim).open(who);
                }, who -> new PlayerMenu(claim, target).open(who))));
        if (Features.on(Features.MENU_BANS) && Manage.canBan(viewer, claim)) {
            set(22, Button.of(Icons.named(Items.BARRIER, Msg.menu("menu.ban"), List.of(Msg.menu("menu.ban_info"))), (pl, c) ->
                    ConfirmMenu.ask(pl, Icons.named(Items.BARRIER, Msg.menu("menu.ban_confirm", "player", name)), List.of(), who -> {
                        if (Manage.ban(who, claim, target)) Msg.send(who, "menu.banned", "player", name, "claim", claim.name());
                        new PlayersMenu(claim).open(who);
                    }, who -> new PlayerMenu(claim, target).open(who))));
        }
        set(18, Button.of(MenuItems.back(), (pl, c) -> new PlayersMenu(claim).open(pl)));
        for (int row = 0; row < rows(); row++) fillRow(row);
    }

    private void notifyTarget(ServerPlayer actor, String key, Rank to) {
        ServerPlayer t = actor.server.getPlayerList().getPlayer(target);
        if (t != null && com.tac5studios.elementsclaims.claim.ClaimStore.player(target).trustMessages) Msg.send(t, key, "claim", claim.name(), "rank", to.name());
    }
}
