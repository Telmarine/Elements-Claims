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
import com.tac5studios.elementsclaims.ui.Click;
import com.tac5studios.elementsclaims.ui.Icons;
import com.tac5studios.elementsclaims.ui.TextInput;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Land Config → Ranks. Ranks are shown top (Owner) to bottom (Visitor).
 *   Left-click a rank to pick it up, then left-click where it should go (click it again to put it back).
 *   Shift + left / right-click moves it up / down one step.
 *   Right-click opens the rank's flags.
 * Owner always stays first and Visitor last.
 */
public final class RanksMenu extends ClaimMenu {

    private Rank picked;

    public RanksMenu(Claim claim) {
        super(4, Msg.menu("menu.ranks_title", "claim", claim.name()), claim);
    }

    @Override
    protected boolean allowed(ServerPlayer p) {
        return Features.on(Features.MENU_RANKS) && Manage.canManageLand(p, claim);
    }

    @Override
    protected void draw() {
        ServerPlayer p = viewer;
        boolean editList = Features.on(Features.MENU_CUSTOM_RANKS) && Manage.canEditRankList(p, claim);
        List<Rank> ranks = claim.ranks();
        for (int i = 0; i < ranks.size() && i < 27; i++) {
            Rank r = ranks.get(i);
            int index = i;
            set(i, Button.of(icon(p, r, editList), (pl, c) -> click(pl, r, index, c, editList)));
        }

        set(27, Button.of(MenuItems.back(), (pl, c) -> new LandConfigMenu(claim).open(pl)));
        if (editList && ranks.size() < ClaimsConfig.maxRanks) {
            set(31, Button.of(Icons.named(Items.WRITABLE_BOOK, Msg.menu("menu.rank_add"),
                    List.of(Msg.menu("menu.rank_add_info", "count", ranks.size(), "max", ClaimsConfig.maxRanks))), (pl, c) ->
                    TextInput.ask(pl, Msg.menu("menu.rank_add_title"), "", (who, text) -> {
                        if (ClaimStore.byId(claim.id()) == claim) {
                            Rank added = Manage.addRank(who, claim, text);
                            if (added != null) Msg.send(who, "menu.rank_added", "rank", added.name());
                        }
                        new RanksMenu(claim).open(who);
                    }, who -> new RanksMenu(claim).open(who))));
        }
        List<Component> help = new ArrayList<>();
        if (editList) {
            help.add(Msg.menu("menu.ranks_help_pick"));
            help.add(Msg.menu("menu.ranks_help_shift"));
        }
        help.add(Msg.menu("menu.ranks_help_edit"));
        set(35, Button.display(Icons.named(Items.BOOK, Msg.menu("menu.ranks_help"), help)));
        for (int r = 0; r < rows(); r++) fillRow(r);
    }

    private ItemStack icon(ServerPlayer p, Rank r, boolean editList) {
        Item item = r.isOwner() ? Items.GOLDEN_HELMET : r.isVisitor() ? Items.LEATHER_HELMET
                : r.id().equals(Rank.ADMIN) ? Items.DIAMOND_HELMET : Items.IRON_HELMET;
        int members = 0;
        for (String id : claim.members().values()) if (id.equals(r.id())) members++;
        int flags = 0;
        for (RankFlag f : RankFlag.values()) if (r.has(f)) flags++;
        List<Component> lore = new ArrayList<>();
        if (r.isOwner()) lore.add(Msg.menu("menu.rank_owner_info"));
        else if (r.isVisitor()) lore.add(Msg.menu("menu.rank_visitor_info"));
        else lore.add(Msg.menu("menu.rank_members", "count", members));
        lore.add(Msg.menu("menu.rank_flags", "count", flags, "total", RankFlag.values().length));
        if (picked == r) lore.add(Msg.menu("menu.rank_picked"));
        else if (editList && !r.isOwner() && !r.isVisitor()) lore.add(Msg.menu("menu.ranks_help_pick"));
        if (Manage.canEditRankFlags(p, claim, r)) lore.add(Msg.menu("menu.ranks_help_edit"));
        ItemStack s = Icons.named(item, Msg.menu(picked == r ? "menu.rank_name_picked" : "menu.rank_name", "rank", r.name()), lore);
        if (picked == r) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    private void click(ServerPlayer p, Rank r, int index, Click c, boolean editList) {
        if (c.isRight() && !c.isShift()) {
            picked = null;
            if (Manage.canEditRankFlags(p, claim, r) || r.isOwner()) new RankEditMenu(claim, r).open(p);
            return;
        }
        if (!editList) return;
        if (c == Click.SHIFT_LEFT) {
            Manage.moveRank(p, claim, r, index - 1);
            picked = null;
        } else if (c == Click.SHIFT_RIGHT) {
            Manage.moveRank(p, claim, r, index + 1);
            picked = null;
        } else if (c == Click.LEFT) {
            if (picked == null) {
                if (!r.isOwner() && !r.isVisitor()) picked = r;
            } else {
                if (picked != r) Manage.moveRank(p, claim, picked, index);
                picked = null;
            }
        }
        refresh();
    }
}
