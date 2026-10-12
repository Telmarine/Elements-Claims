package com.tac5studios.elementsclaims.market;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.PlayerData;
import com.tac5studios.elementsclaims.claim.Rank;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.config.TomlFile;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.ConfirmMenu;
import com.tac5studios.elementsclaims.ui.Icons;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Selling claims / sub-areas and renting sub-areas (SPEC 8). Both are off by default (features.toml [market]);
 * when off nothing of it shows in game. Money goes through Elements: Vault (Elements: Economy).
 *
 *   Selling a claim: the buyer pays the owner and becomes the new owner (members and bans are cleared).
 *   Selling a sub-area: the buyer pays and gets the sub-area's rank there for good.
 *   Renting a sub-area: paid in advance per period; the renter gets the chosen rank inside the sub-area only.
 *       Renewing adds time. When it runs out the renter is removed; with "return items" on, what they left in
 *       containers inside the sub-area is handed back to them.
 * Buyers and renters right-click with their Claim Deed inside the land to see the offer.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Market {

    private static final TomlFile FILE = new TomlFile("market.toml");
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static int timer;

    private Market() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        FILE.load();
    }

    public static void reload() {
        FILE.load();
    }

    /** Elements: Economy (through Elements: Vault) is installed and running. */
    public static boolean economy() {
        return ModList.get().isLoaded("elements_vault") && VaultBridge.available();
    }

    public static boolean selling() {
        return Features.on(Features.SELLING) && economy();
    }

    public static boolean renting() {
        return Features.on(Features.RENTING) && economy();
    }

    public static Component format(long amount) {
        return economy() ? VaultBridge.format(amount) : Component.literal(String.valueOf(amount));
    }

    public static Optional<Long> parse(String text) {
        return economy() ? VaultBridge.parse(text) : Optional.empty();
    }

    public static long abandonedPricePerChunk() {
        return Math.max(0, FILE.lng("abandoned.price_per_chunk", 1000));
    }

    public static List<Integer> rentPeriods() {
        List<Integer> out = new ArrayList<>();
        for (String s : FILE.strings("rent.periods_days")) {
            try {
                out.add(Math.max(1, Integer.parseInt(s.trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        if (out.isEmpty()) out.addAll(List.of(1, 7));
        return out;
    }

    // ---------- offers ----------

    /** Called when a non-manager right-clicks with the Deed inside a claim. Returns true when an offer opened. */
    public static boolean offer(ServerPlayer p, Claim c) {
        if (!selling() && !renting()) return false;
        SubArea area = c.subAreaAt(p.blockPosition());
        if (area != null) {
            if (renting() && area.rentable) {
                rentOffer(p, c, area);
                return true;
            }
            if (selling() && area.salePrice >= 0) {
                buySubOffer(p, c, area);
                return true;
            }
        }
        if (selling() && c.salePrice >= 0 && !c.isOwner(p.getUUID())) {
            buyClaimOffer(p, c);
            return true;
        }
        return false;
    }

    private static void buyClaimOffer(ServerPlayer p, Claim c) {
        int used = ClaimStore.chunksUsed(p.getUUID());
        int max = Claiming.allowance(p);
        if (used + c.chunks().size() > max) {
            Msg.send(p, "market.too_big", "chunks", c.chunks().size(), "used", used, "max", max);
            return;
        }
        long price = c.salePrice;
        ConfirmMenu.ask(p, Icons.named(Items.FILLED_MAP, Msg.menu("market.buy_claim_title", "claim", c.name())),
                List.of(Msg.menu("market.price", "price", format(price)), Msg.menu("market.chunks", "chunks", c.chunks().size())),
                who -> {
                    if (ClaimStore.byId(c.id()) != c || c.salePrice != price) return;
                    UUID seller = c.forfeited ? null : c.owner();
                    Component fail = VaultBridge.pay(who.getUUID(), seller, price, "Bought claim " + c.name());
                    if (fail != null) {
                        Msg.send(who, "market.payment_failed", "reason", fail.getString());
                        return;
                    }
                    String old = c.owner() == null ? Msg.raw("general.server_name") : Claiming.name(who.server, c.owner());
                    c.setOwner(who.getUUID());
                    c.members().clear();
                    c.bans().clear();
                    c.salePrice = -1;
                    c.abandonedSince = 0;
                    c.forfeited = false;
                    for (SubArea a : c.subAreas()) {
                        a.members().clear();
                        a.renter = null;
                        a.salePrice = -1;
                        a.rentable = false;
                    }
                    ClaimStore.markDirty();
                    Msg.send(who, "market.bought_claim", "claim", c.name(), "price", format(price).getString());
                    if (seller != null) notify(who.server, seller, "market.sold_claim", "claim", c.name(), "player", who.getGameProfile().getName(), "price", format(price).getString());
                    ElementsClaims.LOGGER.info("[Claims] {} bought claim {} from {} for {}.", who.getGameProfile().getName(), c.name(), old, price);
                }, null);
    }

    private static void buySubOffer(ServerPlayer p, Claim c, SubArea a) {
        if (a.members().containsKey(p.getUUID())) {
            Msg.send(p, "market.already_yours", "area", a.name());
            return;
        }
        long price = a.salePrice;
        Rank rank = c.rank(a.rentRank) != null ? c.rank(a.rentRank) : c.lowestTrusted();
        ConfirmMenu.ask(p, Icons.named(Items.OAK_SIGN, Msg.menu("market.buy_area_title", "area", a.name())),
                List.of(Msg.menu("market.price", "price", format(price)), Msg.menu("market.rank", "rank", rank.name())),
                who -> {
                    if (ClaimStore.byId(c.id()) != c || c.subArea(a.id()) != a || a.salePrice != price) return;
                    Component fail = VaultBridge.pay(who.getUUID(), c.owner(), price, "Bought area " + a.name());
                    if (fail != null) {
                        Msg.send(who, "market.payment_failed", "reason", fail.getString());
                        return;
                    }
                    a.members().put(who.getUUID(), rank.id());
                    a.salePrice = -1;
                    ClaimStore.markDirty();
                    Msg.send(who, "market.bought_area", "area", a.name(), "claim", c.name(), "price", format(price).getString());
                    if (c.owner() != null) notify(who.server, c.owner(), "market.sold_area", "area", a.name(), "player", who.getGameProfile().getName(), "price", format(price).getString());
                }, null);
    }

    private static void rentOffer(ServerPlayer p, Claim c, SubArea a) {
        boolean renewing = p.getUUID().equals(a.renter);
        if (a.renter != null && !renewing) {
            Msg.send(p, "market.rented_by_other", "area", a.name());
            return;
        }
        long price = a.rentPrice;
        Rank rank = c.rank(a.rentRank) != null ? c.rank(a.rentRank) : c.lowestTrusted();
        List<Component> details = new ArrayList<>();
        details.add(Msg.menu("market.rent_price", "price", format(price), "days", a.rentDays));
        details.add(Msg.menu("market.rank", "rank", rank.name()));
        if (renewing) details.add(Msg.menu("market.rent_left", "time", timeLeft(a.rentUntil)));
        ConfirmMenu.ask(p, Icons.named(Items.CLOCK, Msg.menu(renewing ? "market.renew_title" : "market.rent_title", "area", a.name())), details,
                who -> {
                    if (ClaimStore.byId(c.id()) != c || c.subArea(a.id()) != a || !a.rentable) return;
                    if (a.renter != null && !a.renter.equals(who.getUUID())) return;
                    Component fail = VaultBridge.pay(who.getUUID(), c.owner(), price, "Rent " + a.name());
                    if (fail != null) {
                        Msg.send(who, "market.payment_failed", "reason", fail.getString());
                        return;
                    }
                    long now = System.currentTimeMillis();
                    a.renter = who.getUUID();
                    a.rentUntil = Math.max(now, a.rentUntil) + a.rentDays * DAY;
                    a.members().put(who.getUUID(), rank.id());
                    ClaimStore.markDirty();
                    Msg.send(who, "market.rented", "area", a.name(), "time", timeLeft(a.rentUntil));
                    if (c.owner() != null) notify(who.server, c.owner(), "market.rent_paid", "area", a.name(), "player", who.getGameProfile().getName(), "price", format(price).getString());
                }, null);
    }

    public static String timeLeft(long until) {
        long ms = Math.max(0, until - System.currentTimeMillis());
        long days = ms / DAY, hours = (ms % DAY) / (60L * 60 * 1000);
        return days > 0 ? days + "d " + hours + "h" : hours + "h " + (ms % (60L * 60 * 1000)) / 60000 + "m";
    }

    private static void notify(MinecraftServer server, UUID who, String key, Object... pairs) {
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p != null) Msg.send(p, key, pairs);
    }

    // ---------- rent running out ----------

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (++timer < 1200) return; // once a minute
        timer = 0;
        long now = System.currentTimeMillis();
        MinecraftServer server = e.getServer();
        for (Claim c : new ArrayList<>(ClaimStore.all())) {
            for (SubArea a : c.subAreas()) {
                if (a.renter == null || a.rentUntil > now) continue;
                UUID renter = a.renter;
                a.members().remove(renter);
                a.renter = null;
                a.rentUntil = 0;
                if (a.rentReturnItems) returnItems(server, c, a, renter);
                ClaimStore.markDirty();
                notify(server, renter, "market.rent_ended", "area", a.name(), "claim", c.name());
                if (c.owner() != null) notify(server, c.owner(), "market.rent_ended_owner", "area", a.name(), "player", Claiming.name(server, renter));
            }
        }
    }

    /** Empty the containers inside the sub-area (loaded chunks) and hand the items to the renter. */
    private static void returnItems(MinecraftServer server, Claim c, SubArea a, UUID renter) {
        ServerLevel level = null;
        for (ServerLevel l : server.getAllLevels()) if (ClaimStore.dim(l).equals(c.dimension())) level = l;
        if (level == null) return;
        List<ItemStack> items = new ArrayList<>();
        BlockPos min = a.min(), max = a.max();
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!a.contains(be.getBlockPos()) || !(be instanceof Container box)) continue;
                    for (int i = 0; i < box.getContainerSize(); i++) {
                        ItemStack s = box.removeItemNoUpdate(i);
                        if (!s.isEmpty()) items.add(s);
                    }
                    box.setChanged();
                }
            }
        }
        if (items.isEmpty()) return;
        ServerPlayer online = server.getPlayerList().getPlayer(renter);
        if (online != null) {
            for (ItemStack s : items) if (!online.getInventory().add(s)) online.drop(s, false);
        } else {
            PlayerData d = ClaimStore.player(renter);
            d.pendingItems.addAll(items);
        }
    }
}
