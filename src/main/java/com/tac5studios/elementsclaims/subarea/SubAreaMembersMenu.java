package com.tac5studios.elementsclaims.subarea;

import com.mojang.authlib.GameProfile;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.Rank;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.menu.MenuItems;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
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

/** Players with a rank inside one sub-area only. Left-click: next rank. Right-click: remove. */
public final class SubAreaMembersMenu extends PagedMenu<UUID> {

    private final Claim claim;
    private final SubArea area;

    public SubAreaMembersMenu(Claim claim, SubArea area) {
        super(Msg.menu("subarea.players_title", "area", area.name()), Layout.SLOTS);
        this.claim = claim;
        this.area = area;
    }

    @Override
    protected boolean stillValid(Player player) {
        return player instanceof ServerPlayer sp && Features.on(Features.SUB_AREA_TRUST) && ClaimStore.byId(claim.id()) == claim
                && claim.subArea(area.id()) == area && SubAreas.canManageMembers(sp, claim, area); // owner, or the manager they picked
    }

    @Override
    protected List<UUID> entries() {
        return new ArrayList<>(area.members().keySet());
    }

    @Override
    protected void drawEntry(UUID id, int slot) {
        Rank r = claim.rank(area.members().get(id));
        String name = Claiming.name(viewer.server, id);
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.menu("menu.player_rank", "rank", r == null ? "?" : r.name()));
        Long leaving = area.leaving.get(id);
        if (leaving != null) {
            lore.add(Msg.menu("subarea.leaving_in", "time", SubAreaLeave.timeLeft(leaving)));
            lore.add(Msg.menu("subarea.leaving_cancel"));
        }
        lore.add(Msg.menu("subarea.member_click"));
        set(slot, Button.of(MenuItems.head(viewer.server, id, name, lore), (p, c) -> {
            if (claim.subArea(area.id()) != area) return;
            boolean owner = com.tac5studios.elementsclaims.claim.Manage.full(p, claim);
            // A manager can't change their own entry or anyone at or above their own rank.
            if (!owner && (id.equals(p.getUUID()) || (r != null && !SubAreas.assignable(p, claim, area).contains(r)))) {
                Msg.send(p, "subarea.manager_cant", "player", name);
                return;
            }
            if (c.isShift() && leaving != null) {
                SubAreaLeave.cancel(p, claim, area, id);
                Msg.send(p, "subarea.leave_cancelled", "player", name, "area", area.name());
            } else if (c.isRight()) {
                if (SubAreaLeave.remove(p, claim, area, id)) Msg.send(p, "trust.sub_removed", "player", name, "area", area.name());
            } else {
                List<Rank> trusted = SubAreas.assignable(p, claim, area);
                if (trusted.isEmpty()) return;
                int i = r == null ? -1 : trusted.indexOf(r);
                Rank next = trusted.get((i + 1) % trusted.size());
                area.members().put(id, next.id());
                com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.SUB_AREAS, p, claim, "sub-area rank changed", name + " in " + area.name() + " to " + next.name());
            }
            ClaimStore.markDirty();
            refresh();
        }));
    }

    @Override
    protected Component emptyText() {
        return Msg.menu("subarea.players_empty");
    }

    @Override
    protected void drawNav() {
        navButton(0, Button.of(MenuItems.back(), (p, c) -> new SubAreaMenu(claim, area).open(p)));
        navButton(3, Button.of(Icons.named(Items.WRITABLE_BOOK, Msg.menu("subarea.add_player"), List.of(Msg.menu("subarea.add_player_info"))), (p, c) ->
                TextInput.ask(p, Msg.menu("subarea.add_player_title"), "", (who, text) -> {
                    ServerPlayer online = who.server.getPlayerList().getPlayerByName(text.trim());
                    GameProfile gp = online != null ? online.getGameProfile()
                            : who.server.getProfileCache() == null ? null : who.server.getProfileCache().get(text.trim()).orElse(null);
                    if (gp == null) Msg.send(who, "menu.player_unknown", "player", text);
                    else if (claim.isBanned(gp.getId())) Msg.send(who, "trust.sub_banned", "player", gp.getName(), "claim", claim.name());
                    else if (claim.isOwner(gp.getId()) || gp.getId().equals(who.getUUID())) Msg.send(who, "trust.sub_self");
                    else if (claim.subArea(area.id()) == area && !area.members().containsKey(gp.getId())) {
                        area.members().put(gp.getId(), claim.lowestTrusted().id());
                        ClaimStore.markDirty();
                        com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.SUB_AREAS, who, claim, "sub-area trusted", gp.getName() + " in " + area.name() + " as " + claim.lowestTrusted().name());
                    }
                    new SubAreaMembersMenu(claim, area).open(who);
                }, who -> new SubAreaMembersMenu(claim, area).open(who))));
    }
}
