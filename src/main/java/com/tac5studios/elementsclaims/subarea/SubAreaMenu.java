package com.tac5studios.elementsclaims.subarea;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.claim.Rank;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.menu.ClaimMenu;
import com.tac5studios.elementsclaims.menu.MenuItems;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
import com.tac5studios.elementsclaims.ui.ConfirmMenu;
import com.tac5studios.elementsclaims.ui.Icons;
import com.tac5studios.elementsclaims.ui.TextInput;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** One sub-area: rename, players, visitor rules, delete. */
public final class SubAreaMenu extends ClaimMenu {

    private final SubArea area;

    public SubAreaMenu(Claim claim, SubArea area) {
        super(4, Msg.menu("subarea.title", "area", area.name()), claim);
        this.area = area;
    }

    @Override
    protected boolean allowed(ServerPlayer p) {
        return Features.on(Features.SUB_AREAS) && claim.subArea(area.id()) == area && SubAreas.canManage(p, claim);
    }

    private boolean alive() {
        return ClaimStore.byId(claim.id()) == claim && claim.subArea(area.id()) == area;
    }

    @Override
    protected void draw() {
        set(4, Button.display(Icons.named(Items.OAK_SIGN, Msg.menu("subarea.name", "area", area.name()),
                List.of(Msg.menu("subarea.blocks", "blocks", area.volume()), Msg.menu("subarea.members_count", "count", area.members().size())))));

        set(10, Button.of(Icons.named(Items.NAME_TAG, Msg.menu("subarea.rename"), List.of(Msg.menu("menu.rename_info", "max", ClaimsConfig.claimNameMax))), (p, c) ->
                TextInput.ask(p, Msg.menu("subarea.rename_title"), area.name(), (who, text) -> {
                    String name = Manage.clip(text, ClaimsConfig.claimNameMax);
                    if (alive() && !name.isBlank()) {
                        String old = area.name();
                        area.rename(name);
                        ClaimStore.markDirty();
                        com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.SUB_AREAS, who, claim, "sub-area renamed", old + " -> " + name);
                    }
                    new SubAreaMenu(claim, area).open(who);
                }, who -> new SubAreaMenu(claim, area).open(who))));

        // Who has which rank in the sub-area is the owner's call only.
        if (Features.on(Features.SUB_AREA_TRUST) && SubAreas.canManageMembers(viewer, claim, area)) {
            set(12, Button.of(Icons.named(Items.PLAYER_HEAD, Msg.menu("subarea.players"), List.of(Msg.menu("subarea.players_info"))),
                    (p, c) -> new SubAreaMembersMenu(claim, area).open(p)));
        }
        // Owner only: pick the sub-area's manager (a trusted player). Blank clears it.
        if (Features.on(Features.SUB_AREA_TRUST) && Features.on(Features.SUB_AREA_MANAGER) && com.tac5studios.elementsclaims.claim.Manage.full(viewer, claim)) {
            String current = area.manager == null ? Msg.raw("subarea.manager_none") : Claiming.name(viewer.server, area.manager);
            set(16, Button.of(Icons.named(Items.GOLDEN_HELMET, Msg.menu("subarea.manager", "player", current),
                    List.of(Msg.menu("subarea.manager_info"), Msg.menu("subarea.manager_info2"))), (p, c) ->
                    TextInput.ask(p, Msg.menu("subarea.manager_title"), "", (who, text) -> {
                        if (!alive() || !com.tac5studios.elementsclaims.claim.Manage.full(who, claim)) return;
                        String t = text.trim();
                        if (t.isEmpty()) {
                            area.manager = null;
                            ClaimStore.markDirty();
                            Msg.send(who, "subarea.manager_cleared", "area", area.name());
                            com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.SUB_AREAS, who, claim, "sub-area manager cleared", area.name());
                        } else {
                            ServerPlayer online = who.server.getPlayerList().getPlayerByName(t);
                            com.mojang.authlib.GameProfile gp = online != null ? online.getGameProfile()
                                    : who.server.getProfileCache() == null ? null : who.server.getProfileCache().get(t).orElse(null);
                            if (gp == null) {
                                Msg.send(who, "menu.player_unknown", "player", t);
                            } else if (!area.members().containsKey(gp.getId())) {
                                Msg.send(who, "subarea.manager_not_trusted", "player", gp.getName());
                            } else {
                                area.manager = gp.getId();
                                ClaimStore.markDirty();
                                Msg.send(who, "subarea.manager_set", "player", gp.getName(), "area", area.name());
                                com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.SUB_AREAS, who, claim, "sub-area manager set", gp.getName() + " for " + area.name());
                            }
                        }
                        new SubAreaMenu(claim, area).open(who);
                    }, who -> new SubAreaMenu(claim, area).open(who))));
        }
        if (Features.on(Features.SUB_AREA_VISITOR_FLAGS) && com.tac5studios.elementsclaims.claim.Manage.full(viewer, claim)) {
            set(14, Button.of(Icons.named(Items.LEATHER_BOOTS, Msg.menu("subarea.visitors"),
                    List.of(Msg.menu(area.visitorFlags() == null ? "subarea.visitors_same" : "subarea.visitors_own"))),
                    (p, c) -> new SubAreaFlagsMenu(claim, area).open(p)));
        }

        set(20, Button.of(Icons.named(Items.LAVA_BUCKET, Msg.menu("subarea.delete"), List.of(Msg.menu("subarea.delete_info"))), (p, c) ->
                ConfirmMenu.ask(p, Icons.named(Items.LAVA_BUCKET, Msg.menu("subarea.delete_confirm", "area", area.name())), List.of(), who -> {
                    if (alive()) {
                        claim.subAreas().remove(area);
                        ClaimStore.markDirty();
                        Msg.send(who, "subarea.deleted", "area", area.name());
                        com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.SUB_AREAS, who, claim, "sub-area deleted", area.name());
                    }
                    new SubAreasMenu(claim).open(who);
                }, who -> new SubAreaMenu(claim, area).open(who))));

        set(27, Button.of(MenuItems.back(), (p, c) -> new SubAreasMenu(claim).open(p)));
        for (int r = 0; r < rows(); r++) fillRow(r);
    }

    private static ItemStack glint(ItemStack s, boolean on) {
        if (on) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }
}
