package com.tac5studios.elementsclaims.market;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.PlayerData;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.config.TomlFile;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.perms.Perm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Abandoned claims (SPEC 8), all times in expiry.toml:
 *   1. Owner offline for days_inactive days → claim marked abandoned; still protected for grace_days.
 *   2. Grace over → with selling on (and sell_abandoned): the claim goes up for sale at the abandoned price and its
 *      chunks no longer count for the old owner. Otherwise the claim is deleted (free for anyone).
 *   3. The owner logging in before that clears the mark. Exempt: players listed in expiry.toml and anyone with
 *      claims.expiry.exempt (e.g. staff ranks). Admin claims never expire.
 * Also on login: hands back items waiting from ended rents or admin-claim deaths.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Expiry {

    private static final TomlFile FILE = new TomlFile("expiry.toml");
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static int timer;

    private Expiry() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        FILE.load();
    }

    public static void reload() {
        FILE.load();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        PlayerData d = ClaimStore.player(p.getUUID());
        d.lastSeen = System.currentTimeMillis();
        if (!d.pendingItems.isEmpty()) {
            List<ItemStack> items = new ArrayList<>(d.pendingItems);
            d.pendingItems.clear();
            for (ItemStack s : items) if (!p.getInventory().add(s)) p.drop(s, false);
            Msg.send(p, "market.items_returned", "count", items.size());
        }
        boolean back = false;
        for (Claim c : ClaimStore.ownedBy(p.getUUID())) {
            if (c.abandonedSince == 0 && !c.forfeited) continue;
            c.abandonedSince = 0;
            if (c.forfeited) {
                c.forfeited = false;
                c.salePrice = -1;
            }
            back = true;
        }
        ClaimStore.markDirty();
        if (back && Features.on(Features.EXPIRY_NOTIFY)) Msg.send(p, "expiry.welcome_back");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ClaimStore.player(e.getEntity().getUUID()).lastSeen = System.currentTimeMillis();
        ClaimStore.markDirty();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (++timer < 12000) return; // every 10 minutes
        timer = 0;
        if (!Features.on(Features.EXPIRY)) return;
        check(e.getServer());
    }

    /** Run the abandoned-claims check now. Returns how many claims changed. */
    public static int check(MinecraftServer server) {
        long now = System.currentTimeMillis();
        long inactive = Math.max(1, FILE.num("days_inactive", 60)) * DAY;
        long grace = Math.max(0, FILE.num("grace_days", 14)) * DAY;
        Set<String> exempt = new HashSet<>();
        for (String s : FILE.strings("exempt_players")) exempt.add(s.toLowerCase(Locale.ROOT));
        int changed = 0;
        for (Claim c : new ArrayList<>(ClaimStore.all())) {
            if (c.isAdmin() || c.owner() == null || c.forfeited) continue;
            UUID owner = c.owner();
            if (server.getPlayerList().getPlayer(owner) != null) continue;
            if (exempt.contains(owner.toString()) || exempt.contains(Claiming.name(server, owner).toLowerCase(Locale.ROOT))) continue;
            if (Perm.offline(owner, Perm.EXPIRY_EXEMPT)) continue;
            PlayerData d = ClaimStore.player(owner);
            if (d.lastSeen == 0) {
                d.lastSeen = now; // unknown: start counting from today
                ClaimStore.markDirty();
                continue;
            }
            if (c.abandonedSince == 0) {
                if (now - d.lastSeen < inactive) continue;
                c.abandonedSince = now;
                changed++;
                ElementsClaims.LOGGER.info("[Claims] Claim {} of {} marked abandoned.", c.name(), Claiming.name(server, owner));
                continue;
            }
            if (now - c.abandonedSince < grace) continue;
            changed++;
            if (Market.selling() && Features.on(Features.SELL_ABANDONED)) {
                c.forfeited = true;
                c.salePrice = Market.abandonedPricePerChunk() * c.chunks().size();
                ElementsClaims.LOGGER.info("[Claims] Abandoned claim {} is now for sale.", c.name());
            } else {
                ClaimStore.delete(c);
                ElementsClaims.LOGGER.info("[Claims] Abandoned claim {} of {} was freed.", c.name(), Claiming.name(server, owner));
            }
        }
        if (changed > 0) ClaimStore.markDirty();
        return changed;
    }
}
