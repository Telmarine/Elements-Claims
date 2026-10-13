package com.tac5studios.elementsclaims.log;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Action log: one line per change made through Claims, in <world>/elements_claims/logs/actions-YYYY-MM-DD.log.
 *
 * Line format (fields split by " | "):
 *   time | category | who | claim (owner) | action | details
 *
 * Files are written on a background thread, one file per day, and files older than log.keep_days
 * (claims.toml) are deleted. Staff search them with /claims log. Other mods can listen with
 * {@link com.tac5studios.elementsclaims.api.ClaimsAPI#onAction}.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ActionLog {

    /** What kind of action it was. Each one has its own switch under [log] in features.toml. */
    public enum Category {
        CLAIMING("claiming"),     // claim / unclaim chunks, create / switch / rename properties
        MEMBERS("members"),       // trust, untrust, rank changes
        BANS("bans"),             // ban, unban
        SETTINGS("settings"),     // land flags, rank flags, ranks, private, hand out control
        SUB_AREAS("sub_areas"),   // sub-areas made / deleted / renamed, sub-area players and manager, removal countdowns
        TRANSFERS("transfers"),   // property transfers
        STAFF("staff"),           // admin claims, admin claim settings, imports, staff bypass, /claims commands
        SYSTEM("system");         // abandoned claims, backups restored

        public final String key;

        Category(String key) {
            this.key = key;
        }
    }

    /** One logged action. Handed to API listeners as-is. */
    public record Entry(LocalDateTime time, Category category, String actor, UUID actorId, String claim, UUID claimId,
                        String claimOwner, String action, String details) {
        public String line() {
            return TIME.format(time) + " | " + category.key + " | " + actor + " | "
                    + (claim == null ? "-" : claim + (claimOwner == null ? "" : " (" + claimOwner + ")"))
                    + " | " + action + " | " + (details == null || details.isEmpty() ? "-" : details);
        }
    }

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private static final List<Consumer<Entry>> LISTENERS = new CopyOnWriteArrayList<>();
    private static ExecutorService writer;
    private static Path dir;
    private static MinecraftServer server;

    private ActionLog() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST) // before ClaimStore loads, so a restore can be logged
    public static void onStarting(ServerStartingEvent e) {
        server = e.getServer();
        dir = server.getWorldPath(LevelResource.ROOT).resolve("elements_claims").resolve("logs");
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Claims action log");
            t.setDaemon(true);
            return t;
        });
        writer.execute(ActionLog::cleanOld);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        if (writer == null) return;
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        writer = null;
    }

    /** For other mods: called for every logged action, on the server thread. */
    public static void listen(Consumer<Entry> listener) {
        LISTENERS.add(listener);
    }

    // ---------- logging ----------

    public static void log(Category cat, ServerPlayer actor, Claim c, String action, String details) {
        log(cat, actor == null ? "Console" : actor.getGameProfile().getName(), actor == null ? null : actor.getUUID(), c, action, details);
    }

    public static void log(Category cat, String actor, UUID actorId, Claim c, String action, String details) {
        if (!enabled(cat)) return;
        String owner = null;
        if (c != null) owner = c.isAdmin() ? "admin claim" : server == null ? String.valueOf(c.owner()) : Claiming.ownerName(server, c);
        Entry entry = new Entry(LocalDateTime.now(), cat, actor, actorId, c == null ? null : c.name(), c == null ? null : c.id(),
                owner, action, details);
        for (Consumer<Entry> l : LISTENERS) {
            try {
                l.accept(entry);
            } catch (RuntimeException ex) {
                ElementsClaims.LOGGER.warn("[Claims] Action log listener failed: {}", ex.toString());
            }
        }
        if (writer == null || dir == null) return;
        String line = entry.line() + System.lineSeparator();
        Path file = dir.resolve("actions-" + DAY.format(entry.time()) + ".log");
        writer.execute(() -> {
            try {
                Files.createDirectories(dir);
                Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                ElementsClaims.LOGGER.warn("[Claims] Could not write the action log: {}", ex.toString());
            }
        });
    }

    public static boolean enabled(Category cat) {
        ModConfigSpec.BooleanValue sw = Features.LOG_CATEGORIES.get(cat);
        return Features.on(Features.LOG) && (sw == null || Features.on(sw));
    }

    private static void cleanOld() {
        if (dir == null || !Files.isDirectory(dir)) return;
        LocalDate keepFrom = LocalDate.now().minusDays(Math.max(1, ClaimsConfig.logKeepDays) - 1L);
        try (var files = Files.list(dir)) {
            for (Path f : files.toList()) {
                LocalDate d = dateOf(f);
                if (d != null && d.isBefore(keepFrom)) Files.deleteIfExists(f);
            }
        } catch (IOException ex) {
            ElementsClaims.LOGGER.warn("[Claims] Could not clean up old action logs: {}", ex.toString());
        }
    }

    static LocalDate dateOf(Path f) {
        String n = f.getFileName().toString();
        if (!n.startsWith("actions-") || !n.endsWith(".log")) return null;
        try {
            return LocalDate.parse(n.substring(8, n.length() - 4), DAY);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // ---------- searching ----------

    /**
     * Lines matching the filter, newest first. Reads the log files on the calling thread, so run it off the
     * server thread. filter null = everything.
     */
    public static List<String> search(String filter, int days, int max) {
        List<String> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) return out;
        String f = filter == null ? null : filter.toLowerCase(Locale.ROOT);
        LocalDate day = LocalDate.now();
        for (int i = 0; i < days && out.size() < max; i++, day = day.minusDays(1)) {
            Path file = dir.resolve("actions-" + DAY.format(day) + ".log");
            if (!Files.isRegularFile(file)) continue;
            try {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int j = lines.size() - 1; j >= 0 && out.size() < max; j--) {
                    String l = lines.get(j);
                    if (f == null || l.toLowerCase(Locale.ROOT).contains(f)) out.add(l);
                }
            } catch (IOException ex) {
                ElementsClaims.LOGGER.warn("[Claims] Could not read {}: {}", file.getFileName(), ex.toString());
            }
        }
        return out;
    }

    /** Short text for a chunk position, e.g. "overworld 3,-1". */
    public static String chunk(String dim, int x, int z) {
        String d = dim.startsWith("minecraft:") ? dim.substring(10) : dim;
        return d + " " + x + "," + z;
    }

    public static String name(UUID id) {
        return server == null ? String.valueOf(id) : Claiming.name(server, id);
    }
}
