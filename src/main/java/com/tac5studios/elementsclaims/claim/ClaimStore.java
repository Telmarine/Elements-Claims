package com.tac5studios.elementsclaims.claim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.log.ActionLog;
import com.tac5studios.elementsclaims.util.Alerts;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Every claim, plus a chunk index for fast "who owns this chunk?" lookups.
 * Saved as JSON in <world>/elements_claims/claims.json: written in the background a few seconds
 * after a change, and once more when the server stops.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ClaimStore {

    private static final int FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Map<UUID, Claim> CLAIMS = new LinkedHashMap<>();
    /** dimension -> chunk (ChunkPos.toLong) -> claim */
    private static final Map<String, Long2ObjectOpenHashMap<Claim>> INDEX = new HashMap<>();
    /** Players who already got their first-join Deed. */
    private static final Set<UUID> DEED_GIVEN = new HashSet<>();
    /** Personal settings, last seen and pending items per player. */
    private static final Map<UUID, PlayerData> PLAYERS = new HashMap<>();
    private static net.minecraft.core.HolderLookup.Provider registries;

    private static Path file;
    private static boolean dirty;
    /** True when claims.json and every backup failed to load: nothing gets saved until staff fix it. */
    private static boolean saveBlocked;
    private static long lastHourly, lastDaily;
    private static final long HOUR = 60L * 60 * 1000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static int saveTimer;
    /** Goes up on every change, so outlines know when to redraw. */
    private static int version;
    private static ExecutorService writer;

    private ClaimStore() {}

    // ---------- lifecycle ----------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onStarting(ServerStartingEvent e) {
        MinecraftServer server = e.getServer();
        file = server.getWorldPath(LevelResource.ROOT).resolve("elements_claims").resolve("claims.json");
        registries = server.registryAccess();
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ElementsClaims-Save");
            t.setDaemon(true);
            return t;
        });
        load();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (!dirty) return;
        if (++saveTimer < 100) return; // ~5 seconds after the first change
        saveTimer = 0;
        saveAsync();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        if (writer != null) {
            String json = dirty ? serialize() : null;
            writer.shutdown();
            try {
                writer.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (json != null) write(json);
            writer = null;
        }
        CLAIMS.clear();
        INDEX.clear();
        DEED_GIVEN.clear();
        PLAYERS.clear();
        dirty = false;
    }

    public static void markDirty() {
        dirty = true;
        version++;
    }

    public static int version() {
        return version;
    }

    // ---------- lookups ----------

    public static String dim(Level level) {
        return level.dimension().location().toString();
    }

    public static Claim at(Level level, BlockPos pos) {
        return at(dim(level), new ChunkPos(pos));
    }

    public static Claim at(Level level, ChunkPos pos) {
        return at(dim(level), pos);
    }

    public static Claim at(String dimension, ChunkPos pos) {
        Long2ObjectOpenHashMap<Claim> m = INDEX.get(dimension);
        return m == null ? null : m.get(pos.toLong());
    }

    public static Claim at(String dimension, long chunk) {
        Long2ObjectOpenHashMap<Claim> m = INDEX.get(dimension);
        return m == null ? null : m.get(chunk);
    }

    public static Claim byId(UUID id) {
        return CLAIMS.get(id);
    }

    public static Collection<Claim> all() {
        return Collections.unmodifiableCollection(CLAIMS.values());
    }

    /** Player claims owned by this player (admin claims never count). */
    public static List<Claim> ownedBy(UUID player) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : CLAIMS.values()) if (!c.isAdmin() && c.isOwner(player)) out.add(c);
        return out;
    }

    /** Chunks this player has claimed — the Deed's counter. */
    public static int chunksUsed(UUID player) {
        int n = 0;
        for (Claim c : CLAIMS.values()) if (!c.isAdmin() && c.isOwner(player)) n += c.chunkCount();
        return n;
    }

    public static PlayerData player(UUID id) {
        return PLAYERS.computeIfAbsent(id, k -> new PlayerData());
    }

    public static Map<UUID, PlayerData> players() {
        return PLAYERS;
    }

    /** Move every chunk of a claim to the index (used after changing a claim's owner or importing). */
    public static void add(Claim c) {
        CLAIMS.put(c.id(), c);
        c.chunksByDim().forEach((dim, set) -> {
            for (long l : set) index(dim).put(l, c);
        });
        markDirty();
    }

    /** Delete a whole claim. */
    public static void delete(Claim c) {
        CLAIMS.remove(c.id());
        c.chunksByDim().forEach((dim, set) -> {
            Long2ObjectOpenHashMap<Claim> m = INDEX.get(dim);
            if (m != null) for (long l : set) if (m.get(l) == c) m.remove(l);
        });
        markDirty();
    }

    /**
     * Move every chunk (in every dimension) and sub-area of one claim into another claim, then delete the first one.
     * Only used when a player accepts a claim transfer and chooses to add it to their current property.
     * The moved claim's own members, bans, ranks and land flags are dropped: the receiving property's rules apply.
     */
    public static void moveInto(Claim from, Claim into) {
        Map<String, List<Long>> moved = new LinkedHashMap<>();
        from.chunksByDim().forEach((dim, set) -> moved.put(dim, new ArrayList<>(set)));
        List<SubArea> areas = new ArrayList<>(from.subAreas());
        delete(from);
        moved.forEach((dim, list) -> {
            for (long l : list) {
                into.chunks(dim).add(l);
                index(dim).put(l, into);
            }
        });
        into.subAreas().addAll(areas);
        Manage.fixMissingRanks(into);
        markDirty();
    }

    public static boolean deedGiven(UUID player) {
        return DEED_GIVEN.contains(player);
    }

    public static void setDeedGiven(UUID player) {
        if (DEED_GIVEN.add(player)) markDirty();
    }

    // ---------- changes ----------

    /**
     * Add a chunk. With a target it joins that claim; with null it starts a new claim. Claims are never merged:
     * a chunk joins exactly one claim even when it touches several. The caller has already checked the rules.
     */
    public static Claim addChunk(Claim target, UUID owner, boolean admin, String dimension, ChunkPos pos, String newName) {
        if (target == null) {
            target = Claim.create(owner, admin, newName, dimension);
            CLAIMS.put(target.id(), target);
        }
        target.chunks(dimension).add(pos.toLong());
        index(dimension).put(pos.toLong(), target);
        markDirty();
        return target;
    }

    /** Claims next to this chunk (north, south, west, east) that pass the test. */
    public static List<Claim> touching(String dimension, ChunkPos pos, java.util.function.Predicate<Claim> test) {
        List<Claim> out = new ArrayList<>();
        for (ChunkPos n : new ChunkPos[]{
                new ChunkPos(pos.x + 1, pos.z), new ChunkPos(pos.x - 1, pos.z),
                new ChunkPos(pos.x, pos.z + 1), new ChunkPos(pos.x, pos.z - 1)}) {
            Claim c = at(dimension, n);
            if (c != null && test.test(c) && !out.contains(c)) out.add(c);
        }
        return out;
    }

    /** Remove one chunk. A claim with no chunks left is deleted. Returns the claim it was in, or null. */
    public static Claim removeChunk(String dimension, ChunkPos pos) {
        Claim c = at(dimension, pos);
        if (c == null) return null;
        c.chunks(dimension).remove(pos.toLong());
        index(dimension).remove(pos.toLong());
        if (c.chunkCount() == 0) CLAIMS.remove(c.id());
        markDirty();
        return c;
    }

    private static Long2ObjectOpenHashMap<Claim> index(String dimension) {
        return INDEX.computeIfAbsent(dimension, k -> new Long2ObjectOpenHashMap<>());
    }

    // ---------- saving ----------

    private static void load() {
        clearAll();
        saveBlocked = false;
        lastHourly = lastDaily = 0;
        if (file == null) return;
        scanBackups();
        if (!Files.exists(file)) {
            ElementsClaims.LOGGER.info("[Claims] No claims yet ({}).", file);
            return;
        }
        try {
            readFrom(file);
            ElementsClaims.LOGGER.info("[Claims] Loaded {} claims.", CLAIMS.size());
            return;
        } catch (Exception ex) {
            ElementsClaims.LOGGER.error("[Claims] Could not read {}.", file, ex);
        }

        // claims.json is broken. Keep it for staff, then try the backups, newest first.
        String kept = "claims.json.broken-" + STAMP.format(LocalDateTime.now());
        try {
            Files.copy(file, file.resolveSibling(kept), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
        for (Path b : backupsNewestFirst()) {
            clearAll();
            try {
                readFrom(b);
                dirty = true; // write the restored claims back to claims.json
                ElementsClaims.LOGGER.info("[Claims] Loaded {} claims from backup {}.", CLAIMS.size(), b.getFileName());
                ActionLog.log(ActionLog.Category.SYSTEM, "Server", null, null, "restored from backup", b.getFileName() + ", broken file kept as " + kept);
                Alerts.staff("claims.json could not be read, so claims were restored from backup " + b.getFileName()
                        + ". Anything changed after that backup is missing. The broken file was kept as " + kept + ".");
                return;
            } catch (Exception ex) {
                ElementsClaims.LOGGER.error("[Claims] Backup {} could not be read either.", b.getFileName());
            }
        }
        clearAll();
        saveBlocked = true;
        Alerts.staff("claims.json could not be read and no backup worked. Saving is paused so nothing gets overwritten."
                + " Fix " + file.getFileName() + " (a copy is kept as " + kept + ") and restart the server.");
    }

    private static void clearAll() {
        CLAIMS.clear();
        INDEX.clear();
        DEED_GIVEN.clear();
        PLAYERS.clear();
        dirty = false;
    }

    /** Read a claims file into the store. Throws when the file, or any claim in it, can't be read. */
    private static void readFrom(Path path) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.has("deed_given")) for (JsonElement e : root.getAsJsonArray("deed_given")) DEED_GIVEN.add(UUID.fromString(e.getAsString()));
        if (root.has("players")) {
            for (Map.Entry<String, JsonElement> pe : root.getAsJsonObject("players").entrySet()) {
                JsonObject po = pe.getValue().getAsJsonObject();
                PlayerData d = new PlayerData();
                if (po.has("titles")) d.titles = po.get("titles").getAsBoolean();
                if (po.has("trust_messages")) d.trustMessages = po.get("trust_messages").getAsBoolean();
                if (po.has("last_seen")) d.lastSeen = po.get("last_seen").getAsLong();
                if (po.has("name")) d.lastName = po.get("name").getAsString();
                if (po.has("active")) d.activeProperty = UUID.fromString(po.get("active").getAsString());
                if (po.has("pending_items")) {
                    for (JsonElement ie : po.getAsJsonArray("pending_items")) {
                        ItemStack st = itemFromJson(ie.getAsString());
                        if (!st.isEmpty()) d.pendingItems.add(st);
                    }
                }
                PLAYERS.put(UUID.fromString(pe.getKey()), d);
            }
        }
        // One unreadable claim counts as a broken file: loading the rest and saving would delete that claim for good.
        for (JsonElement e : root.getAsJsonArray("claims")) {
            Claim c = read(e.getAsJsonObject());
            CLAIMS.put(c.id(), c);
            c.chunksByDim().forEach((dim, set) -> {
                for (long l : set) {
                    Claim before = index(dim).put(l, c);
                    if (before != null && before != c) {
                        ElementsClaims.LOGGER.warn("[Claims] Chunk {} in {} was in two claims; kept {}.",
                                new ChunkPos(l), dim, c.name());
                    }
                }
            });
        }
        int fixed = 0;
        for (Claim c : CLAIMS.values()) if (Manage.fixMissingRanks(c)) fixed++;
        if (fixed > 0) {
            ElementsClaims.LOGGER.info("[Claims] Fixed ranks that no longer exist in {} claims.", fixed);
            dirty = true;
        }
    }

    // ---------- backups ----------

    private static Path backupDir() {
        return file.resolveSibling("backups");
    }

    private static List<Path> backups(String prefix) {
        List<Path> out = new ArrayList<>();
        Path dir = backupDir();
        if (!Files.isDirectory(dir)) return out;
        try (var stream = Files.list(dir)) {
            stream.filter(f -> f.getFileName().toString().startsWith(prefix) && f.getFileName().toString().endsWith(".json"))
                    .forEach(out::add);
        } catch (IOException ignored) {
        }
        out.sort(Comparator.comparing((Path f) -> f.getFileName().toString()).reversed()); // names hold the date: newest first
        return out;
    }

    private static List<Path> backupsNewestFirst() {
        List<Path> all = new ArrayList<>(backups("hourly-"));
        all.addAll(backups("daily-"));
        all.sort(Comparator.comparing((Path f) -> {
            try {
                return Files.getLastModifiedTime(f).toMillis();
            } catch (IOException ex) {
                return 0L;
            }
        }).reversed());
        return all;
    }

    /** Pick up when the last backups were made, so a restart doesn't make extra ones. */
    private static void scanBackups() {
        for (String prefix : new String[]{"hourly-", "daily-"}) {
            List<Path> list = backups(prefix);
            if (list.isEmpty()) continue;
            try {
                long t = Files.getLastModifiedTime(list.get(0)).toMillis();
                if (prefix.equals("hourly-")) lastHourly = t;
                else lastDaily = t;
            } catch (IOException ignored) {
            }
        }
    }

    /** After a good save: copy claims.json to backups/ once an hour and once a day (how many are kept: claims.toml [backups]). */
    private static void backupAfterSave() {
        if (!Features.on(Features.BACKUPS)) return;
        long now = System.currentTimeMillis();
        try {
            if (Features.on(Features.BACKUPS_HOURLY) && now - lastHourly >= HOUR) {
                copyBackup("hourly-", ClaimsConfig.backupsKeepHourly);
                lastHourly = now;
            }
            if (Features.on(Features.BACKUPS_DAILY) && now - lastDaily >= 24 * HOUR) {
                copyBackup("daily-", ClaimsConfig.backupsKeepDaily);
                lastDaily = now;
            }
        } catch (IOException ex) {
            ElementsClaims.LOGGER.warn("[Claims] Could not write a backup of claims.json: {}", ex.toString());
        }
    }

    private static void copyBackup(String prefix, int keep) throws IOException {
        Files.createDirectories(backupDir());
        Files.copy(file, backupDir().resolve(prefix + STAMP.format(LocalDateTime.now()) + ".json"), StandardCopyOption.REPLACE_EXISTING);
        List<Path> list = backups(prefix);
        for (int k = keep; k < list.size(); k++) Files.deleteIfExists(list.get(k));
    }

    /** Write a copy of all claims next to claims.json (before an import). Returns the file name, or null. */
    public static String backup(String tag) {
        if (file == null) return null;
        String name = "claims.json.before-" + tag + "-" + System.currentTimeMillis();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file.resolveSibling(name), serialize(), StandardCharsets.UTF_8);
            return name;
        } catch (IOException ex) {
            ElementsClaims.LOGGER.error("[Claims] Could not write backup {}.", name, ex);
            return null;
        }
    }

    private static void saveAsync() {
        dirty = false;
        String json = serialize();
        if (writer != null) writer.execute(() -> write(json));
        else write(json);
    }

    private static synchronized void write(String json) {
        if (saveBlocked) {
            ElementsClaims.LOGGER.error("[Claims] Not saving: claims.json couldn't be loaded and saving is paused.");
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("claims.json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            backupAfterSave();
        } catch (IOException ex) {
            ElementsClaims.LOGGER.error("[Claims] Could not save {}.", file, ex);
            dirty = true;
        }
    }

    private static String serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        JsonArray given = new JsonArray();
        for (UUID u : DEED_GIVEN) given.add(u.toString());
        root.add("deed_given", given);
        JsonObject players = new JsonObject();
        PLAYERS.forEach((id, d) -> {
            JsonObject po = new JsonObject();
            po.addProperty("titles", d.titles);
            po.addProperty("trust_messages", d.trustMessages);
            po.addProperty("last_seen", d.lastSeen);
            if (d.lastName != null) po.addProperty("name", d.lastName);
            if (d.activeProperty != null) po.addProperty("active", d.activeProperty.toString());
            if (!d.pendingItems.isEmpty()) {
                JsonArray items = new JsonArray();
                for (ItemStack st : d.pendingItems) {
                    String j = itemToJson(st);
                    if (j != null) items.add(j);
                }
                po.add("pending_items", items);
            }
            players.add(id.toString(), po);
        });
        root.add("players", players);
        JsonArray arr = new JsonArray();
        for (Claim c : CLAIMS.values()) arr.add(write(c));
        root.add("claims", arr);
        return GSON.toJson(root);
    }

    private static String itemToJson(ItemStack st) {
        if (registries == null || st.isEmpty()) return null;
        try {
            return st.save(registries).toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ItemStack itemFromJson(String snbt) {
        if (registries == null) return ItemStack.EMPTY;
        try {
            return ItemStack.parseOptional(registries, net.minecraft.nbt.TagParser.parseTag(snbt));
        } catch (Exception e) {
            ElementsClaims.LOGGER.warn("[Claims] Could not read a pending item: {}", e.toString());
            return ItemStack.EMPTY;
        }
    }

    private static JsonObject write(Claim c) {
        JsonObject o = new JsonObject();
        o.addProperty("id", c.id().toString());
        if (c.owner() != null) o.addProperty("owner", c.owner().toString());
        o.addProperty("admin", c.isAdmin());
        o.addProperty("name", c.name());
        o.addProperty("dimension", c.dimension());
        o.addProperty("created", c.created());
        o.addProperty("private", c.isPrivate());
        // chunks per dimension: {"minecraft:overworld": [[x, z], ...], "minecraft:the_nether": [...]}
        JsonObject byDim = new JsonObject();
        c.chunksByDim().forEach((dim, set) -> {
            JsonArray chunks = new JsonArray();
            for (long l : set) {
                JsonArray xz = new JsonArray();
                xz.add(ChunkPos.getX(l));
                xz.add(ChunkPos.getZ(l));
                chunks.add(xz);
            }
            byDim.add(dim, chunks);
        });
        o.add("chunks_by_dim", byDim);
        JsonArray ranks = new JsonArray();
        for (Rank r : c.ranks()) {
            JsonObject ro = new JsonObject();
            ro.addProperty("id", r.id());
            ro.addProperty("name", r.name());
            JsonArray fl = new JsonArray();
            for (RankFlag f : r.flags()) fl.add(f.key());
            ro.add("flags", fl);
            ranks.add(ro);
        }
        o.add("ranks", ranks);
        JsonObject members = new JsonObject();
        c.members().forEach((u, r) -> members.addProperty(u.toString(), r));
        o.add("members", members);
        JsonArray bans = new JsonArray();
        for (UUID u : c.bans()) bans.add(u.toString());
        o.add("bans", bans);
        JsonObject flags = new JsonObject();
        c.landFlags().forEach((f, v) -> flags.addProperty(f.key(), v));
        o.add("land_flags", flags);
        if (c.abandonedSince > 0) o.addProperty("abandoned_since", c.abandonedSince);
        if (c.isAdmin()) {
            JsonObject a = new JsonObject();
            a.addProperty("keep_inventory", c.keepInventory);
            a.addProperty("no_hunger", c.noHunger);
            a.addProperty("block_entry", c.blockEntry);
            a.addProperty("block_exit", c.blockExit);
            a.add("enter_commands", strings(c.enterCommands));
            a.add("leave_commands", strings(c.leaveCommands));
            o.add("admin_extras", a);
        }
        if (!c.subAreas().isEmpty()) {
            JsonArray subs = new JsonArray();
            for (SubArea s : c.subAreas()) subs.add(writeSub(s));
            o.add("sub_areas", subs);
        }
        return o;
    }

    private static JsonArray strings(java.util.List<String> list) {
        JsonArray a = new JsonArray();
        for (String x : list) a.add(x);
        return a;
    }

    private static JsonObject writeSub(SubArea s) {
        JsonObject o = new JsonObject();
        o.addProperty("id", s.id().toString());
        o.addProperty("name", s.name());
        o.addProperty("dimension", s.dimension());
        JsonArray box = new JsonArray();
        BlockPos a = s.min(), b = s.max();
        for (int v : new int[]{a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()}) box.add(v);
        o.add("box", box);
        JsonObject members = new JsonObject();
        s.members().forEach((u, r) -> members.addProperty(u.toString(), r));
        o.add("members", members);
        if (s.visitorFlags() != null) {
            JsonArray fl = new JsonArray();
            for (RankFlag f : s.visitorFlags()) fl.add(f.key());
            o.add("visitor_flags", fl);
        }
        if (!s.placed.isEmpty()) {
            JsonObject pl = new JsonObject();
            s.placed.forEach((pos, who) -> pl.addProperty(Long.toString(pos), who.toString()));
            o.add("placed", pl);
        }
        if (s.manager != null) o.addProperty("manager", s.manager.toString());
        if (!s.leaving.isEmpty()) {
            JsonObject lv = new JsonObject();
            s.leaving.forEach((who, until) -> lv.addProperty(who.toString(), until));
            o.add("leaving", lv);
        }
        return o;
    }

    private static SubArea readSub(JsonObject o, String claimDim) {
        JsonArray b = o.getAsJsonArray("box");
        SubArea s = new SubArea(UUID.fromString(o.get("id").getAsString()), o.get("name").getAsString(),
                o.has("dimension") ? o.get("dimension").getAsString() : claimDim,
                new BlockPos(b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt()),
                new BlockPos(b.get(3).getAsInt(), b.get(4).getAsInt(), b.get(5).getAsInt()));
        if (o.has("members")) for (Map.Entry<String, JsonElement> m : o.getAsJsonObject("members").entrySet())
            s.members().put(UUID.fromString(m.getKey()), m.getValue().getAsString());
        if (o.has("visitor_flags")) {
            Set<RankFlag> fl = EnumSet.noneOf(RankFlag.class);
            for (JsonElement f : o.getAsJsonArray("visitor_flags")) {
                RankFlag rf = RankFlag.byKey(f.getAsString());
                if (rf != null) fl.add(rf);
            }
            s.setVisitorFlags(fl);
        }
        if (o.has("placed")) {
            for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("placed").entrySet()) {
                s.placed.put(Long.parseLong(en.getKey()), UUID.fromString(en.getValue().getAsString()));
            }
        }
        if (o.has("manager")) s.manager = UUID.fromString(o.get("manager").getAsString());
        if (o.has("leaving")) {
            for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("leaving").entrySet()) {
                s.leaving.put(UUID.fromString(en.getKey()), en.getValue().getAsLong());
            }
        }
        return s;
    }

    private static Claim read(JsonObject o) {
        UUID owner = o.has("owner") ? UUID.fromString(o.get("owner").getAsString()) : null;
        boolean admin = o.has("admin") && o.get("admin").getAsBoolean();
        if (owner == null && !admin) throw new IllegalStateException("player claim without an owner");
        Claim c = new Claim(UUID.fromString(o.get("id").getAsString()), owner, admin,
                o.get("name").getAsString(), o.get("dimension").getAsString(),
                o.has("created") ? o.get("created").getAsLong() : System.currentTimeMillis());
        c.setPrivate(o.has("private") && o.get("private").getAsBoolean());
        if (o.has("chunks_by_dim")) {
            for (Map.Entry<String, JsonElement> d : o.getAsJsonObject("chunks_by_dim").entrySet()) {
                for (JsonElement e : d.getValue().getAsJsonArray()) {
                    JsonArray xz = e.getAsJsonArray();
                    c.chunks(d.getKey()).add(ChunkPos.asLong(xz.get(0).getAsInt(), xz.get(1).getAsInt()));
                }
            }
        }
        if (o.has("chunks")) { // older saves: every chunk in the claim's one dimension
            for (JsonElement e : o.getAsJsonArray("chunks")) {
                JsonArray xz = e.getAsJsonArray();
                c.chunks(c.dimension()).add(ChunkPos.asLong(xz.get(0).getAsInt(), xz.get(1).getAsInt()));
            }
        }
        for (JsonElement e : o.getAsJsonArray("ranks")) {
            JsonObject ro = e.getAsJsonObject();
            Set<RankFlag> fl = EnumSet.noneOf(RankFlag.class);
            for (JsonElement f : ro.getAsJsonArray("flags")) {
                RankFlag rf = RankFlag.byKey(f.getAsString());
                if (rf != null) fl.add(rf);
            }
            c.ranks().add(new Rank(ro.get("id").getAsString(), ro.get("name").getAsString(), fl));
        }
        // Owner first and Visitor last are required; repair a hand-edited file instead of failing.
        if (c.rank(Rank.OWNER) == null) c.ranks().add(0, new Rank(Rank.OWNER, "Owner", RankFlag.all()));
        if (c.rank(Rank.VISITOR) == null) c.ranks().add(new Rank(Rank.VISITOR, "Visitor", RankFlag.visitorDefaults()));
        Rank ownerRank = c.rank(Rank.OWNER), visitor = c.rank(Rank.VISITOR);
        c.ranks().remove(ownerRank);
        c.ranks().remove(visitor);
        c.ranks().add(0, ownerRank);
        c.ranks().add(visitor);
        if (o.has("members")) {
            for (Map.Entry<String, JsonElement> m : o.getAsJsonObject("members").entrySet()) {
                String rankId = m.getValue().getAsString();
                if (c.rank(rankId) == null || Rank.OWNER.equals(rankId) || Rank.VISITOR.equals(rankId)) rankId = c.lowestTrusted().id();
                c.members().put(UUID.fromString(m.getKey()), rankId);
            }
        }
        if (o.has("bans")) for (JsonElement e : o.getAsJsonArray("bans")) c.bans().add(UUID.fromString(e.getAsString()));
        for (LandFlag f : LandFlag.values()) c.setFlag(f, f.defaultValue());
        if (o.has("land_flags")) {
            for (Map.Entry<String, JsonElement> m : o.getAsJsonObject("land_flags").entrySet()) {
                LandFlag f = LandFlag.byKey(m.getKey());
                if (f != null) c.setFlag(f, m.getValue().getAsBoolean());
            }
        }
        if (o.has("abandoned_since")) c.abandonedSince = o.get("abandoned_since").getAsLong();
        if (o.has("admin_extras")) {
            JsonObject a = o.getAsJsonObject("admin_extras");
            c.keepInventory = a.has("keep_inventory") && a.get("keep_inventory").getAsBoolean();
            c.noHunger = a.has("no_hunger") && a.get("no_hunger").getAsBoolean();
            c.blockEntry = a.has("block_entry") && a.get("block_entry").getAsBoolean();
            c.blockExit = a.has("block_exit") && a.get("block_exit").getAsBoolean();
            if (a.has("enter_commands")) for (JsonElement e : a.getAsJsonArray("enter_commands")) c.enterCommands.add(e.getAsString());
            if (a.has("leave_commands")) for (JsonElement e : a.getAsJsonArray("leave_commands")) c.leaveCommands.add(e.getAsString());
        }
        if (o.has("sub_areas")) {
            for (JsonElement e : o.getAsJsonArray("sub_areas")) {
                try {
                    c.subAreas().add(readSub(e.getAsJsonObject(), c.dimension()));
                } catch (RuntimeException ex) {
                    ElementsClaims.LOGGER.warn("[Claims] Skipped a sub-area in {}: {}", c.name(), ex.toString());
                }
            }
        }
        return c;
    }
}
