package com.tac5studios.elementsclaims.subarea;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.menu.ClaimMenu;
import com.tac5studios.elementsclaims.menu.MenuItems;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
import com.tac5studios.elementsclaims.ui.Icons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** What visitors may do inside one sub-area (replaces the claim's Visitor rules there). */
public final class SubAreaFlagsMenu extends ClaimMenu {

    private final SubArea area;

    public SubAreaFlagsMenu(Claim claim, SubArea area) {
        super(6, Msg.menu("subarea.visitors_title", "area", area.name()), claim);
        this.area = area;
    }

    @Override
    protected boolean allowed(ServerPlayer p) {
        return Features.on(Features.SUB_AREA_VISITOR_FLAGS) && claim.subArea(area.id()) == area
                && com.tac5studios.elementsclaims.claim.Manage.full(p, claim); // flags are the owner's only
    }

    @Override
    protected void draw() {
        Set<RankFlag> flags = area.visitorFlags() != null ? area.visitorFlags() : claim.visitorRank().flags();
        int slot = 0;
        for (RankFlag f : RankFlag.values()) {
            if (f.isManagement()) continue;
            boolean on = flags.contains(f);
            set(slot++, Button.of(MenuItems.toggle(MenuItems.icon(f), Msg.raw("rank_flag_names." + f.key()),
                    Msg.raw("rank_flag_info." + f.key()), on, true), (p, c) -> {
                Set<RankFlag> now = area.visitorFlags() != null ? EnumSet.copyOf(area.visitorFlags().isEmpty() ? EnumSet.noneOf(RankFlag.class) : area.visitorFlags())
                        : (claim.visitorRank().flags().isEmpty() ? EnumSet.noneOf(RankFlag.class) : EnumSet.copyOf(claim.visitorRank().flags()));
                if (!now.remove(f)) now.add(f);
                area.setVisitorFlags(now);
                ClaimStore.markDirty();
                com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.SUB_AREAS, p, claim, "sub-area visitor flag", area.name() + ": " + f.key() + " " + (now.contains(f) ? "on" : "off"));
                refresh();
            }));
        }
        set(45, Button.of(MenuItems.back(), (p, c) -> new SubAreaMenu(claim, area).open(p)));
        if (area.visitorFlags() != null) {
            set(49, Button.of(Icons.named(Items.BUCKET, Msg.menu("subarea.visitors_reset"), List.of(Msg.menu("subarea.visitors_same"))), (p, c) -> {
                area.setVisitorFlags(null);
                ClaimStore.markDirty();
                com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.SUB_AREAS, p, claim, "sub-area visitor flags reset", area.name() + " uses the claim's visitor rules again");
                refresh();
            }));
        }
        fillRow(5);
    }
}
