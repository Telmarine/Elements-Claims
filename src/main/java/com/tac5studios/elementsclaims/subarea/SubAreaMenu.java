package com.tac5studios.elementsclaims.subarea;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.Rank;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.market.Market;
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

/** One sub-area: rename, players, visitor rules, selling, renting, delete. */
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
                    if (alive() && !text.isBlank()) {
                        area.rename(text.length() > ClaimsConfig.claimNameMax ? text.substring(0, ClaimsConfig.claimNameMax) : text);
                        ClaimStore.markDirty();
                    }
                    new SubAreaMenu(claim, area).open(who);
                }, who -> new SubAreaMenu(claim, area).open(who))));

        if (Features.on(Features.SUB_AREA_TRUST)) {
            set(12, Button.of(Icons.named(Items.PLAYER_HEAD, Msg.menu("subarea.players"), List.of(Msg.menu("subarea.players_info"))),
                    (p, c) -> new SubAreaMembersMenu(claim, area).open(p)));
        }
        if (Features.on(Features.SUB_AREA_VISITOR_FLAGS)) {
            set(14, Button.of(Icons.named(Items.LEATHER_BOOTS, Msg.menu("subarea.visitors"),
                    List.of(Msg.menu(area.visitorFlags() == null ? "subarea.visitors_same" : "subarea.visitors_own"))),
                    (p, c) -> new SubAreaFlagsMenu(claim, area).open(p)));
        }

        if (Market.selling() && claim.owner() != null) {
            boolean forSale = area.salePrice >= 0;
            List<Component> lore = new ArrayList<>();
            lore.add(forSale ? Msg.menu("market.for_sale", "price", Market.format(area.salePrice)) : Msg.menu("market.not_for_sale"));
            lore.add(Msg.menu("market.sell_area_info"));
            lore.add(Msg.menu("market.click_set_price"));
            if (forSale) lore.add(Msg.menu("market.right_click_stop"));
            set(16, Button.of(glint(Icons.named(Items.GOLD_INGOT, Msg.menu("market.sell_area"), lore), forSale), (p, c) -> {
                if (c.isRight() && forSale) {
                    area.salePrice = -1;
                    ClaimStore.markDirty();
                    refresh();
                    return;
                }
                askPrice(p, "market.price_title", price -> area.salePrice = price);
            }));
        }

        if (Market.renting() && claim.owner() != null) {
            set(22, Button.of(glint(Icons.named(Items.CLOCK, Msg.menu("market.rent_settings"),
                    List.of(Msg.menu(area.rentable ? "market.rent_on" : "market.rent_off"))), area.rentable),
                    (p, c) -> new RentMenu(claim, area).open(p)));
        }

        set(20, Button.of(Icons.named(Items.LAVA_BUCKET, Msg.menu("subarea.delete"), List.of(Msg.menu("subarea.delete_info"))), (p, c) ->
                ConfirmMenu.ask(p, Icons.named(Items.LAVA_BUCKET, Msg.menu("subarea.delete_confirm", "area", area.name())), List.of(), who -> {
                    if (alive()) {
                        claim.subAreas().remove(area);
                        ClaimStore.markDirty();
                        Msg.send(who, "subarea.deleted", "area", area.name());
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

    private void askPrice(ServerPlayer p, String titleKey, java.util.function.LongConsumer apply) {
        TextInput.ask(p, Msg.menu(titleKey), "", (who, text) -> {
            var price = Market.parse(text);
            if (price.isEmpty()) {
                Msg.send(who, "market.bad_price", "input", text);
            } else if (alive()) {
                apply.accept(price.get());
                ClaimStore.markDirty();
            }
            new SubAreaMenu(claim, area).open(who);
        }, who -> new SubAreaMenu(claim, area).open(who));
    }

    /** Renting settings for one sub-area. */
    static final class RentMenu extends ClaimMenu {
        private final SubArea area;

        RentMenu(Claim claim, SubArea area) {
            super(3, Msg.menu("market.rent_title_menu", "area", area.name()), claim);
            this.area = area;
        }

        @Override
        protected boolean allowed(ServerPlayer p) {
            return Market.renting() && claim.subArea(area.id()) == area && SubAreas.canManage(p, claim);
        }

        @Override
        protected void draw() {
            set(10, Button.of(glint(Icons.named(area.rentable ? Items.LIME_DYE : Items.GRAY_DYE,
                    Msg.menu(area.rentable ? "market.rent_on" : "market.rent_off"), List.of(Msg.menu("menu.flag_click"))), area.rentable), (p, c) -> {
                area.rentable = !area.rentable;
                ClaimStore.markDirty();
                refresh();
            }));
            set(11, Button.of(Icons.named(Items.GOLD_NUGGET, Msg.menu("market.rent_price_button", "price", Market.format(area.rentPrice)),
                    List.of(Msg.menu("market.click_set_price"))), (p, c) ->
                    TextInput.ask(p, Msg.menu("market.price_title"), String.valueOf(area.rentPrice), (who, text) -> {
                        var price = Market.parse(text);
                        if (price.isEmpty()) Msg.send(who, "market.bad_price", "input", text);
                        else {
                            area.rentPrice = price.get();
                            ClaimStore.markDirty();
                        }
                        new RentMenu(claim, area).open(who);
                    }, who -> new RentMenu(claim, area).open(who))));
            set(12, Button.of(Icons.named(Items.CLOCK, Msg.menu("market.rent_period", "days", area.rentDays), List.of(Msg.menu("market.click_cycle"))), (p, c) -> {
                List<Integer> periods = Market.rentPeriods();
                int i = periods.indexOf(area.rentDays);
                area.rentDays = periods.get((i + 1) % periods.size());
                ClaimStore.markDirty();
                refresh();
            }));
            Rank rank = claim.rank(area.rentRank) != null ? claim.rank(area.rentRank) : claim.lowestTrusted();
            set(13, Button.of(Icons.named(Items.IRON_HELMET, Msg.menu("market.rent_rank", "rank", rank.name()), List.of(Msg.menu("market.click_cycle"))), (p, c) -> {
                List<Rank> trusted = claim.ranks().subList(1, claim.ranks().size() - 1);
                int i = trusted.indexOf(rank);
                area.rentRank = trusted.get((i + 1) % trusted.size()).id();
                ClaimStore.markDirty();
                refresh();
            }));
            set(14, Button.of(glint(Icons.named(Items.CHEST, Msg.menu(area.rentReturnItems ? "market.return_on" : "market.return_off"),
                    List.of(Msg.menu("market.return_info"))), area.rentReturnItems), (p, c) -> {
                area.rentReturnItems = !area.rentReturnItems;
                ClaimStore.markDirty();
                refresh();
            }));
            if (area.renter != null) {
                set(16, Button.of(Icons.named(Items.BARRIER, Msg.menu("market.renter", "player", Claiming.name(viewer.server, area.renter), "time", Market.timeLeft(area.rentUntil)),
                        List.of(Msg.menu("market.evict_info"))), (p, c) ->
                        ConfirmMenu.ask(p, Icons.named(Items.BARRIER, Msg.menu("market.evict_confirm")), List.of(), who -> {
                            area.rentUntil = 0; // the minute check removes them (and returns items)
                            ClaimStore.markDirty();
                            new RentMenu(claim, area).open(who);
                        }, who -> new RentMenu(claim, area).open(who))));
            }
            set(18, Button.of(MenuItems.back(), (p, c) -> new SubAreaMenu(claim, area).open(p)));
            for (int r = 0; r < rows(); r++) fillRow(r);
        }
    }
}
