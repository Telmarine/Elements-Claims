package com.tac5studios.elementsclaims.menu;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.claim.Rank;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
import com.tac5studios.elementsclaims.ui.ConfirmMenu;
import com.tac5studios.elementsclaims.ui.Icons;
import com.tac5studios.elementsclaims.ui.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * One rank's flags (SPEC section 3). Click a flag to switch it.
 * Giving a rank "Manage Players" asks "Are you sure?" first.
 * The Visitor rank can't get the management flags.
 */
public final class RankEditMenu extends ClaimMenu {

    private final Rank rank;

    public RankEditMenu(Claim claim, Rank rank) {
        super(6, Msg.menu("menu.rank_edit_title", "rank", rank.name()), claim);
        this.rank = rank;
    }

    @Override
    protected boolean allowed(ServerPlayer p) {
        return Features.on(Features.MENU_RANKS) && claim.ranks().contains(rank)
                && (Manage.canEditRankFlags(p, claim, rank) || (rank.isOwner() && Manage.canManageLand(p, claim)));
    }

    private static boolean shown(Rank r, RankFlag f) {
        if (!r.isVisitor()) return true;
        return !f.isManagement();
    }

    @Override
    protected void draw() {
        ServerPlayer p = viewer;
        boolean edit = Manage.canEditRankFlags(p, claim, rank);
        int slot = 0;
        for (RankFlag f : RankFlag.values()) {
            if (!shown(rank, f)) continue;
            // Admin Mode: only the owner sees it.
            if (f == RankFlag.ADMIN_MODE && (!Manage.full(p, claim) || !Features.on(Features.MENU_DELEGATE))) continue;
            boolean on = rank.has(f);
            int s = slot++;
            set(s, Button.of(MenuItems.toggle(MenuItems.icon(f), Msg.raw("rank_flag_names." + f.key()),
                    Msg.raw("rank_flag_info." + f.key()), on, edit && Manage.canGrant(p, claim, f)), (pl, c) -> toggle(pl, f)));
        }

        set(45, Button.of(MenuItems.back(), (pl, c) -> new RanksMenu(claim).open(pl)));
        boolean editList = Features.on(Features.MENU_CUSTOM_RANKS) && Manage.canEditRankList(p, claim);
        if (editList) {
            set(48, Button.of(Icons.named(Items.NAME_TAG, Msg.menu("menu.rank_rename"),
                    List.of(Msg.menu("menu.rank_rename_info", "max", ClaimsConfig.rankNameMax))), (pl, c) ->
                    TextInput.ask(pl, Msg.menu("menu.rank_rename_title"), rank.name(), (who, text) -> {
                        if (ClaimStore.byId(claim.id()) == claim && claim.ranks().contains(rank)) Manage.renameRank(who, claim, rank, text);
                        new RankEditMenu(claim, rank).open(who);
                    }, who -> new RankEditMenu(claim, rank).open(who))));
        }
        if (editList && !rank.isOwner() && !rank.isVisitor() && claim.ranks().size() > 3) {
            set(50, Button.of(Icons.named(Items.LAVA_BUCKET, Msg.menu("menu.rank_delete"),
                    List.of(Msg.menu("menu.rank_delete_info"))), (pl, c) ->
                    ConfirmMenu.ask(pl, Icons.named(Items.LAVA_BUCKET, Msg.menu("menu.rank_delete_confirm", "rank", rank.name())),
                            List.of(Msg.menu("menu.rank_delete_info")), who -> {
                                if (Manage.deleteRank(who, claim, rank)) Msg.send(who, "menu.rank_deleted", "rank", rank.name());
                                new RanksMenu(claim).open(who);
                            }, who -> new RankEditMenu(claim, rank).open(who))));
        }
        fillRow(5);
    }

    private void toggle(ServerPlayer p, RankFlag f) {
        if (!Manage.canEditRankFlags(p, claim, rank)) return;
        boolean on = !rank.has(f);
        if (on && (f == RankFlag.MANAGE_PLAYERS || f == RankFlag.ADMIN_MODE)) {
            // SPEC: giving power over other players to a rank needs an "Are you sure?".
            String k = f == RankFlag.ADMIN_MODE ? "menu.admin_mode_confirm" : "menu.manage_players_confirm";
            ConfirmMenu.ask(p, Icons.named(MenuItems.icon(f), Msg.menu(k, "rank", rank.name())),
                    List.of(Msg.menu(k + "_info", "rank", rank.name())), who -> {
                        Manage.setRankFlag(who, claim, rank, f, true);
                        new RankEditMenu(claim, rank).open(who);
                    }, who -> new RankEditMenu(claim, rank).open(who));
            return;
        }
        Manage.setRankFlag(p, claim, rank, f, on);
        refresh();
    }
}
