package com.tac5studios.elementsclaims.market;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.PlayerData;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.config.TomlFile;
import com.tac5studios.elementsclaims.log.ActionLog;
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
 *   1. Owner offline longer than their playtime allows (playtime_days) → claim marked abandoned; still protected for grace_days.
 *   2. Grace over → the claim is deleted (free for anyone).
 *   3. The owner logging in before that clears the mark. Exempt: players listed in expiry.toml and anyone with
 *      claims.expiry.exempt (e.g. staff ranks). Admin claims never expire.
 * Also on login: hands back items waiting from admin-claim deaths.
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
        d.lastName = p.getGameProfile().getName();
        // Logged back in still on the death screen: wait until they respawn, or the items would be lost.
        if (p.isAlive()) giveBack(p);
        boolean back = false;
        for (Claim c : ClaimStore.ownedBy(p.getUUID())) {
            if (c.abandonedSince == 0) continue;
            c.abandonedSince = 0;
            back = true;
            ActionLog.log(ActionLog.Category.SYSTEM, p, c, "abandoned mark cleared", "owner logged in");
        }
        ClaimStore.markDirty();
        if (back && Features.on(Features.EXPIRY_NOTIFY)) Msg.send(p, "expiry.welcome_back");
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) giveBack(p);
    }

    /** Hand back items kept from an admin-claim death (saved when the player logged out on the death screen). */
    private static void giveBack(ServerPlayer p) {
        PlayerData d = ClaimStore.player(p.getUUID());
        if (d.pendingItems.isEmpty()) return;
        List<ItemStack> items = new ArrayList<>(d.pendingItems);
        d.pendingItems.clear();
        for (ItemStack s : items) if (!p.getInventory().add(s)) p.drop(s, false);
        ClaimStore.markDirty();
        Msg.send(p, "expiry.items_returned", "count", items.size());
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
        long grace = Math.max(0, FILE.num("grace_days", 14)) * DAY;
        Set<String> exempt = new HashSet<>();
        for (String s : FILE.strings("exempt_players")) exempt.add(s.toLowerCase(Locale.ROOT));
        List<long[]> steps = playtimeSteps();
        java.util.Map<UUID, Long> allowed = new java.util.HashMap<>();
        int changed = 0;
        for (Claim c : new ArrayList<>(ClaimStore.all())) {
            if (c.isAdmin() || c.owner() == null) continue;
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
                long inactive = allowed.computeIfAbsent(owner, id -> (Features.on(Features.EXPIRY_PLAYTIME)
                        ? daysFor(playHours(server, id), steps) : Math.max(1, FILE.num("days_inactive", 60))) * DAY);
                if (now - d.lastSeen < inactive) continue;
                c.abandonedSince = now;
                changed++;
                ElementsClaims.LOGGER.info("[Claims] Claim {} of {} marked abandoned.", c.name(), Claiming.name(server, owner));
                ActionLog.log(ActionLog.Category.SYSTEM, "Server", null, c, "marked abandoned",
                        "owner offline " + (now - d.lastSeen) / DAY + " days, allowed " + inactive / DAY);
                continue;
            }
            if (now - c.abandonedSince < grace) continue;
            changed++;
            ActionLog.log(ActionLog.Category.SYSTEM, "Server", null, c, "abandoned claim freed", c.chunkCount() + " chunks");
            ClaimStore.delete(c);
            ElementsClaims.LOGGER.info("[Claims] Abandoned claim {} of {} was freed.", c.name(), Claiming.name(server, owner));
        }
        if (changed > 0) ClaimStore.markDirty();
        return changed;
    }

    // ---------- playtime ----------

    private static final String[] DEFAULT_STEPS = {"1h=14", "4h=30", "12h=60", "1d=120", "7d=180", "14d=270", "30d=365"};

    /** playtime_days from expiry.toml as {hours, days}, sorted by hours. Bad entries are skipped with a warning. */
    private static List<long[]> playtimeSteps() {
        List<String> raw = FILE.strings("playtime_days");
        if (raw.isEmpty()) raw = List.of(DEFAULT_STEPS);
        List<long[]> out = new ArrayList<>();
        for (String entry : raw) {
            try {
                String[] kv = entry.trim().toLowerCase(Locale.ROOT).split("=");
                String t = kv[0].trim();
                long n = Long.parseLong(t.substring(0, t.length() - 1).trim());
                long hours = switch (t.charAt(t.length() - 1)) {
                    case 'h' -> n;
                    case 'd' -> n * 24;
                    default -> throw new IllegalArgumentException();
                };
                out.add(new long[]{hours, Math.max(1, Long.parseLong(kv[1].trim()))});
            } catch (RuntimeException ex) {
                ElementsClaims.LOGGER.warn("[Claims] expiry.toml: skipping playtime_days entry '{}' (use like \"4h=30\" or \"7d=180\").", entry);
            }
        }
        if (out.isEmpty()) return playtimeStepsDefault();
        out.sort((a, b) -> Long.compare(a[0], b[0]));
        return out;
    }

    private static List<long[]> playtimeStepsDefault() {
        List<long[]> out = new ArrayList<>();
        for (String s : DEFAULT_STEPS) {
            String[] kv = s.split("=");
            long n = Long.parseLong(kv[0].substring(0, kv[0].length() - 1));
            out.add(new long[]{kv[0].endsWith("d") ? n * 24 : n, Long.parseLong(kv[1])});
        }
        return out;
    }

    /** Days offline allowed for this much playtime: the highest step reached. Below the first step, the first step counts. */
    static long daysFor(double hours, List<long[]> steps) {
        long days = steps.get(0)[1];
        for (long[] st : steps) if (hours >= st[0]) days = st[1];
        return days;
    }

    /** Total hours played, from Minecraft's own play-time stat (world/stats/<uuid>.json). 0 if there's no file. */
    private static double playHours(MinecraftServer server, UUID id) {
        java.nio.file.Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_STATS_DIR).resolve(id + ".json");
        if (!java.nio.file.Files.isRegularFile(file)) return 0;
        try (java.io.Reader r = java.nio.file.Files.newBufferedReader(file)) {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
            com.google.gson.JsonObject custom = root.getAsJsonObject("stats").getAsJsonObject("minecraft:custom");
            if (custom == null || !custom.has("minecraft:play_time")) return 0;
            return custom.get("minecraft:play_time").getAsLong() / 20.0 / 3600.0;
        } catch (Exception ex) {
            ElementsClaims.LOGGER.warn("[Claims] Could not read playtime from {}: {}", file.getFileName(), ex.getMessage());
            return 0;
        }
    }
}
