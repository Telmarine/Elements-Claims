package com.tac5studios.elementsclaims.menu;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
import com.tac5studios.elementsclaims.ui.Icons;
import com.tac5studios.elementsclaims.ui.TextInput;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Land Config — the main claim menu (SPEC section 2).
 *   Land Flags · Ranks · Player Management · Bans · Visitor · Rename · Private
 * Buttons the viewer can't use are not shown.
 */
public final class LandConfigMenu extends ClaimMenu {

    public LandConfigMenu(Claim claim) {
        super(4, Msg.menu("menu.main_title", "claim", claim.name()), claim);
    }

    /** Open the menu if the player may use it; otherwise say why. Returns true when opened. */
    public static boolean open(ServerPlayer p, Claim c) {
        if (!Features.on(Features.MENU)) return false;
        if (!Manage.canOpenMenu(p, c)) {
            Msg.send(p, "menu.no_access", "claim", c.name());
            return false;
        }
        new LandConfigMenu(c).open(p);
        return true;
    }

    @Override
    protected boolean allowed(ServerPlayer p) {
        return Manage.canOpenMenu(p, claim);
    }

    @Override
    protected void draw() {
        ServerPlayer p = viewer;
        boolean land = Manage.canManageLand(p, claim);
        boolean players = Manage.canManagePlayers(p, claim);

        // Info
        List<Component> info = new ArrayList<>();
        info.add(Msg.menu("menu.info_owner", "owner", Claiming.ownerName(p.server, claim)));
        info.add(Msg.menu("menu.info_chunks", "chunks", claim.chunkCount()));
        info.add(Msg.menu("menu.info_members", "members", claim.members().size()));
        info.add(Msg.menu("menu.info_rank", "rank", claim.rankOf(p.getUUID()).name()));
        set(4, Button.display(Icons.named(Items.FILLED_MAP, Msg.menu("menu.info_name", "claim", claim.name()), info)));

        boolean owner = Manage.full(p, claim); // every flag is the owner's call
        if (owner && Features.on(Features.MENU_LAND_FLAGS)) {
            set(10, Button.of(Icons.named(Items.GRASS_BLOCK, Msg.menu("menu.land_flags"),
                    List.of(Msg.menu("menu.land_flags_info"))), (pl, c) -> new LandFlagsMenu(claim).open(pl)));
        }
        if (land && Features.on(Features.MENU_RANKS)) {
            set(11, Button.of(Icons.named(Items.NAME_TAG, Msg.menu("menu.ranks"),
                    List.of(Msg.menu("menu.ranks_info"))), (pl, c) -> new RanksMenu(claim).open(pl)));
        }
        if (players && Features.on(Features.MENU_PLAYERS)) {
            set(12, Button.of(Icons.named(Items.PLAYER_HEAD, Msg.menu("menu.players"),
                    List.of(Msg.menu("menu.players_info", "members", claim.members().size()))), (pl, c) -> new PlayersMenu(claim).open(pl)));
        }
        if (Manage.canBan(p, claim) && Features.on(Features.MENU_BANS)) {
            set(13, Button.of(Icons.named(Items.BARRIER, Msg.menu("menu.bans"),
                    List.of(Msg.menu("menu.bans_info", "count", claim.bans().size()))), (pl, c) -> new BansMenu(claim).open(pl)));
        }
        if (land && Features.on(Features.MENU_RANKS) && Manage.canEditRankFlags(p, claim, claim.visitorRank())) {
            set(14, Button.of(Icons.named(Items.LEATHER_BOOTS, Msg.menu("menu.visitor", "rank", claim.visitorRank().name()),
                    List.of(Msg.menu("menu.visitor_info"))), (pl, c) -> new RankEditMenu(claim, claim.visitorRank()).open(pl)));
        }
        if (land && Features.on(Features.MENU_RENAME)) {
            set(16, Button.of(Icons.named(Items.WRITABLE_BOOK, Msg.menu("menu.rename"),
                    List.of(Msg.menu("menu.rename_info", "max", ClaimsConfig.claimNameMax))), (pl, c) ->
                    TextInput.ask(pl, Msg.menu("menu.rename_title"), claim.name(), (who, text) -> {
                        if (ClaimStore.byId(claim.id()) == claim && Manage.rename(who, claim, text)) {
                            Msg.send(who, "menu.renamed", "claim", claim.name());
                        }
                        new LandConfigMenu(claim).open(who);
                    }, who -> new LandConfigMenu(claim).open(who))));
        }
        if (owner && Features.on(Features.MENU_PRIVATE)) {
            boolean priv = claim.isPrivate();
            ItemStack door = Icons.named(priv ? Items.IRON_DOOR : Items.OAK_DOOR,
                    Msg.menu(priv ? "menu.private_on" : "menu.private_off"), List.of(Msg.menu("menu.private_info")));
            if (priv) door.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            set(21, Button.of(door, (pl, c) -> {
                Manage.setPrivate(pl, claim, !claim.isPrivate());
                refresh();
            }));
        }
        // Owner only: nobody else sees this button.
        if (Features.on(Features.SUB_AREAS) && com.tac5studios.elementsclaims.subarea.SubAreas.canManage(p, claim)) {
            set(15, Button.of(Icons.named(Items.STICK, Msg.menu("subarea.list"),
                    List.of(Msg.menu("subarea.list_info", "count", claim.subAreas().size()))),
                    (pl, c) -> new com.tac5studios.elementsclaims.subarea.SubAreasMenu(claim).open(pl)));
        }
        if (claim.isAdmin() && Features.on(Features.ADMIN_CLAIMS) && com.tac5studios.elementsclaims.protect.Access.staff(p, claim)) {
            set(23, Button.of(Icons.named(Items.COMMAND_BLOCK, Msg.menu("admin.settings"), List.of(Msg.menu("admin.settings_info"))),
                    (pl, c) -> new AdminSettingsMenu(claim).open(pl)));
        }
        for (int r = 0; r < rows(); r++) fillRow(r);
    }
}
