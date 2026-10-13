package com.tac5studios.elementsclaims.importer;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.config.Features;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Importers for the claim mods available on 1.21.1 (SPEC 7). They read the other mod's saved files straight from
 * the world folder, so the other mod doesn't have to be installed. Chunks Elements: Claims already has are skipped.
 *   opac      Open Parties and Claims  world/data/openpartiesandclaims/ (server claims → admin claims, parties → members)
 *   ftb       FTB Chunks + FTB Teams   world/ftbchunks/, world/ftbteams/ (server teams → admin claims)
 *   flan      Flan                     <dimension>/data/claims/*.json (boxes rounded out to whole chunks)
 *   cadmus    Cadmus / Odyssey Claims  world/data/cadmus_claims.dat (no dimension stored: imported into the overworld)
 */
public final class Importers {

    public static final List<String> IDS = List.of("opac", "ftb", "flan", "cadmus");
    private static final UUID OPAC_SERVER = new UUID(0, 0);
    private static final UUID OPAC_EXPIRED = new UUID(0, 1);

    /** What an import would do. */
    public record Preview(int claims, int chunks, int skippedChunks, int admin, List<String> notes) {}

    private Importers() {}

    public static boolean enabled(String id) {
        if (!Features.on(Features.IMPORT)) return false;
        return switch (id) {
            case "opac" -> Features.on(Features.IMPORT_OPAC);
            case "ftb" -> Features.on(Features.IMPORT_FTB);
            case "flan" -> Features.on(Features.IMPORT_FLAN);
            case "cadmus" -> Features.on(Features.IMPORT_CADMUS);
            default -> false;
        };
    }

    public static List<Imported> read(String id, MinecraftServer server, List<String> notes) throws IOException {
        return switch (id) {
            case "opac" -> opac(server, notes);
            case "ftb" -> ftb(server, notes);
            case "flan" -> flan(server, notes);
            case "cadmus" -> cadmus(server, notes);
            default -> throw new IllegalArgumentException(id);
        };
    }

    public static Preview preview(List<Imported> list, List<String> notes) {
        int claims = 0, chunks = 0, skipped = 0, admin = 0;
        for (Imported im : list) {
            int add = 0;
            for (long l : im.chunks) {
                if (ClaimStore.at(im.dimension, l) != null) skipped++;
                else add++;
            }
            if (add > 0) {
                claims++;
                chunks += add;
                if (im.admin) admin++;
            }
        }
        return new Preview(claims, chunks, skipped, admin, notes);
    }

    /** Create the claims. Returns how many were made. */
    public static int apply(List<Imported> list) {
        int made = 0;
        for (Imported im : list) {
            Claim c = Claim.create(im.owner, im.admin, im.name, im.dimension);
            for (long l : im.chunks) if (ClaimStore.at(im.dimension, l) == null) c.chunks().add(l);
            if (c.chunks().isEmpty()) continue;
            for (UUID m : im.members) if (!c.isOwner(m)) c.members().put(m, c.lowestTrusted().id());
            ClaimStore.add(c);
            made++;
        }
        return made;
    }

    private static Path world(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    // ================= OPAC =================

    private static List<Imported> opac(MinecraftServer server, List<String> notes) throws IOException {
        Path base = world(server).resolve("data").resolve("openpartiesandclaims");
        Path claimsDir = base.resolve("player-claims");
        if (!Files.isDirectory(claimsDir)) throw new IOException("No OPAC data in " + claimsDir);

        // Parties: player -> everyone in their party; party id -> allies.
        Map<UUID, Set<UUID>> partyOf = new HashMap<>();
        Map<UUID, List<UUID>> partyAllies = new HashMap<>();
        Map<UUID, UUID> partyIdOf = new HashMap<>();
        Map<UUID, Set<UUID>> partyMembers = new HashMap<>();
        Path parties = base.resolve("parties");
        if (Files.isDirectory(parties)) {
            for (Path f : list(parties, ".nbt")) {
                try {
                    CompoundTag t = NbtIo.read(f);
                    UUID partyId = UUID.fromString(stem(f));
                    Set<UUID> members = new java.util.LinkedHashSet<>();
                    members.add(NbtUtils.loadUUID(t.getCompound("owner").get("uuid")));
                    for (Tag m : t.getList("members", Tag.TAG_COMPOUND)) members.add(NbtUtils.loadUUID(((CompoundTag) m).get("uuid")));
                    List<UUID> allies = new ArrayList<>();
                    for (Tag a : t.getList("allies", Tag.TAG_INT_ARRAY)) allies.add(NbtUtils.loadUUID(a));
                    partyMembers.put(partyId, members);
                    partyAllies.put(partyId, allies);
                    for (UUID u : members) {
                        partyOf.put(u, members);
                        partyIdOf.put(u, partyId);
                    }
                } catch (Exception ex) {
                    notes.add("Skipped party file " + f.getFileName() + ": " + ex.getMessage());
                }
            }
        }

        List<Imported> out = new ArrayList<>();
        for (Path f : list(claimsDir, ".nbt")) {
            UUID owner;
            try {
                owner = UUID.fromString(stem(f));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (owner.equals(OPAC_EXPIRED)) {
                notes.add("Skipped OPAC expired claims.");
                continue;
            }
            boolean admin = owner.equals(OPAC_SERVER);
            CompoundTag root;
            try {
                root = NbtIo.read(f);
            } catch (Exception ex) {
                notes.add("Skipped " + f.getFileName() + ": " + ex.getMessage());
                continue;
            }
            Config main = admin ? toml(world(server).resolve("serverconfig").resolve("openpartiesandclaims-server-claim-config.toml"))
                    : toml(base.resolve("player-configs").resolve(owner + ".toml"));
            String username = root.getString("username");
            if (!admin && !username.isEmpty() && ClaimStore.player(owner).lastName == null) ClaimStore.player(owner).lastName = username;
            String mainName = str(main, "playerConfig.claims.name");
            Set<UUID> trusted = new java.util.LinkedHashSet<>();
            if (!admin) {
                String access = str(main, "playerConfig.claims.protection.exceptions.fullAccess");
                if (access == null || access.isEmpty()) access = "P";
                if (access.equals("P") || access.equals("A")) {
                    Set<UUID> party = partyOf.get(owner);
                    if (party != null) trusted.addAll(party);
                    if (access.equals("A") && partyIdOf.containsKey(owner)) {
                        for (UUID ally : partyAllies.getOrDefault(partyIdOf.get(owner), List.of())) {
                            trusted.addAll(partyMembers.getOrDefault(ally, Set.of()));
                        }
                    }
                } else if (!access.equals("N") && !access.equals("E")) {
                    trusted.addAll(customGroup(main, access));
                }
                trusted.remove(owner);
            }
            CompoundTag dims = root.getCompound("dimensions");
            for (String dim : dims.getAllKeys()) {
                Map<Integer, Imported> bySub = new LinkedHashMap<>();
                for (Tag ct : dims.getCompound(dim).getList("claims", Tag.TAG_COMPOUND)) {
                    CompoundTag claim = (CompoundTag) ct;
                    CompoundTag state = claim.getCompound("state");
                    int sub = state.contains("subConfigIndex") ? state.getInt("subConfigIndex") : -1;
                    Imported im = bySub.computeIfAbsent(sub, k -> {
                        String name = k >= 0 ? subName(base, owner, k) : null;
                        if (name == null || name.isEmpty()) name = mainName;
                        if (name == null || name.isEmpty()) name = admin ? "Server Claim" : (username.isEmpty() ? "Imported Claim" : username + "'s Claim");
                        Imported n = new Imported(admin ? null : owner, admin, name, dim);
                        n.members.addAll(trusted);
                        return n;
                    });
                    for (Tag pt : claim.getList("positions", Tag.TAG_COMPOUND)) {
                        CompoundTag pos = (CompoundTag) pt;
                        im.chunks.add(ChunkPos.asLong(pos.getInt("x"), pos.getInt("z")));
                    }
                }
                out.addAll(bySub.values());
            }
        }
        return out;
    }

    private static String subName(Path base, UUID owner, int index) {
        Path dir = base.resolve("player-configs").resolve("sub-configs").resolve(owner.toString());
        if (!Files.isDirectory(dir)) return null;
        try {
            for (Path f : list(dir, ".toml")) {
                String n = stem(f);
                int d = n.lastIndexOf('$');
                if (d < 0) continue;
                if (Integer.toString(index).equals(n.substring(d + 1))) {
                    String name = str(toml(f), "playerConfig.claims.name");
                    return name == null || name.isEmpty() ? n.substring(0, d) : name;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /** OPAC custom group "Id[...]{Name|uuid, ...}" members. */
    private static Set<UUID> customGroup(Config main, String id) {
        Set<UUID> out = new java.util.LinkedHashSet<>();
        Object v = main == null ? null : get(main, "playerConfig.customPlayerGroups");
        if (!(v instanceof List<?> groups)) return out;
        for (Object g : groups) {
            if (!(g instanceof String s) || !s.startsWith(id)) continue;
            int open = s.indexOf('{'), close = s.lastIndexOf('}');
            if (open < 0 || close < open) continue;
            for (String part : s.substring(open + 1, close).split(",")) {
                int bar = part.indexOf('|');
                if (bar < 0) continue;
                try {
                    out.add(UUID.fromString(part.substring(bar + 1).trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return out;
    }

    // ================= FTB Chunks / Teams =================

    @SuppressWarnings("unchecked")
    private static List<Imported> ftb(MinecraftServer server, List<String> notes) throws IOException {
        Path chunksDir = world(server).resolve("ftbchunks");
        if (!Files.isDirectory(chunksDir)) throw new IOException("No FTB Chunks data in " + chunksDir);
        Path teamsDir = world(server).resolve("ftbteams");
        List<Imported> out = new ArrayList<>();
        for (Path f : list(chunksDir, ".snbt")) {
            String id = stem(f);
            UUID teamId;
            try {
                teamId = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Map<String, Object> data;
            try {
                data = (Map<String, Object>) Snbt.parse(Files.readString(f, StandardCharsets.UTF_8));
            } catch (Exception ex) {
                notes.add("Skipped " + f.getFileName() + ": " + ex.getMessage());
                continue;
            }
            Map<String, Object> team = null;
            String type = "player";
            for (String t : List.of("player", "party", "server")) {
                Path tf = teamsDir.resolve(t).resolve(id + ".snbt");
                if (Files.exists(tf)) {
                    try {
                        team = (Map<String, Object>) Snbt.parse(Files.readString(tf, StandardCharsets.UTF_8));
                        type = t;
                    } catch (Exception ex) {
                        notes.add("Could not read team " + tf.getFileName() + ": " + ex.getMessage());
                    }
                    break;
                }
            }
            boolean admin = type.equals("server");
            UUID owner = admin ? null : teamId;
            Set<UUID> members = new java.util.LinkedHashSet<>();
            String name = null;
            if (team != null) {
                if (type.equals("party") && team.get("owner") instanceof String o) owner = uuid(o);
                if (team.get("ranks") instanceof Map<?, ?> ranks) {
                    for (Map.Entry<?, ?> r : ranks.entrySet()) {
                        String rank = String.valueOf(r.getValue()).toLowerCase(Locale.ROOT);
                        UUID u = uuid(String.valueOf(r.getKey()));
                        if (u != null && (rank.equals("owner") || rank.equals("officer") || rank.equals("member") || rank.equals("ally"))) members.add(u);
                    }
                }
                if (team.get("properties") instanceof Map<?, ?> props && props.get("ftbteams:display_name") instanceof String dn) name = dn;
                if (name == null && team.get("player_name") instanceof String pn) name = pn + "'s Claim";
            }
            if (!admin && owner == null) {
                notes.add("Skipped FTB team " + id + " (no owner).");
                continue;
            }
            if (owner != null) members.remove(owner);
            if (name == null || name.isEmpty()) name = admin ? "Server Claim" : "Imported Claim";
            if (!(data.get("chunks") instanceof Map<?, ?> dims)) continue;
            for (Map.Entry<?, ?> d : dims.entrySet()) {
                Imported im = new Imported(owner, admin, name, String.valueOf(d.getKey()));
                im.members.addAll(members);
                if (d.getValue() instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m && m.get("x") instanceof Number x && m.get("z") instanceof Number z) {
                            im.chunks.add(ChunkPos.asLong(x.intValue(), z.intValue()));
                        }
                    }
                }
                if (!im.chunks.isEmpty()) out.add(im);
            }
        }
        return out;
    }

    // ================= Flan =================

    private static List<Imported> flan(MinecraftServer server, List<String> notes) throws IOException {
        List<Imported> out = new ArrayList<>();
        boolean any = false;
        for (ServerLevel level : server.getAllLevels()) {
            Path dir = DimensionType.getStorageFolder(level.dimension(), world(server)).resolve("data").resolve("claims");
            if (!Files.isDirectory(dir)) continue;
            any = true;
            String dim = ClaimStore.dim(level);
            for (Path f : list(dir, ".json")) {
                boolean admin = f.getFileName().toString().equals("!AdminClaims.json");
                UUID owner = admin ? null : uuid(stem(f));
                if (!admin && owner == null) continue;
                JsonArray arr;
                try {
                    JsonElement root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
                    if (!root.isJsonArray()) continue;
                    arr = root.getAsJsonArray();
                } catch (Exception ex) {
                    notes.add("Skipped " + f + ": " + ex.getMessage());
                    continue;
                }
                for (JsonElement e : arr) {
                    if (!e.isJsonObject()) continue;
                    JsonObject o = e.getAsJsonObject();
                    if (!o.has("PosxXzZY")) continue;
                    JsonArray box = o.getAsJsonArray("PosxXzZY");
                    int minX = box.get(0).getAsInt(), maxX = box.get(1).getAsInt(), minZ = box.get(2).getAsInt(), maxZ = box.get(3).getAsInt();
                    boolean claimAdmin = admin || (o.has("AdminClaim") && o.get("AdminClaim").getAsBoolean());
                    String name = o.has("Name") && !o.get("Name").getAsString().isEmpty() ? o.get("Name").getAsString() : (claimAdmin ? "Server Claim" : "Imported Claim");
                    Imported im = new Imported(claimAdmin ? null : owner, claimAdmin, name, dim);
                    for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
                        for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
                            im.chunks.add(ChunkPos.asLong(cx, cz));
                    if (o.has("PlayerPerms") && o.get("PlayerPerms").isJsonObject()) {
                        for (String key : o.getAsJsonObject("PlayerPerms").keySet()) {
                            UUID u = uuid(key);
                            if (u != null && !u.equals(owner)) im.members.add(u);
                        }
                    }
                    out.add(im);
                }
            }
        }
        if (!any) throw new IOException("No Flan claims found (data/claims folders).");
        notes.add("Flan boxes were rounded out to whole chunks; sub-claims were merged into their claim.");
        return out;
    }

    // ================= Cadmus / Odyssey Claims =================

    private static List<Imported> cadmus(MinecraftServer server, List<String> notes) throws IOException {
        Path data = world(server).resolve("data");
        Path claimsFile = data.resolve("cadmus_claims.dat");
        if (!Files.exists(claimsFile)) throw new IOException("No Cadmus data in " + claimsFile);
        CompoundTag root = NbtIo.readCompressed(claimsFile, NbtAccounter.unlimitedHeap()).getCompound("data");

        Map<UUID, String> adminNames = new HashMap<>();
        Path flags = data.resolve("cadmus_flags.dat");
        if (Files.exists(flags)) {
            try {
                CompoundTag names = NbtIo.readCompressed(flags, NbtAccounter.unlimitedHeap()).getCompound("data").getCompound("names");
                for (String n : names.getAllKeys()) adminNames.put(NbtUtils.loadUUID(names.get(n)), n);
            } catch (Exception ex) {
                notes.add("Could not read admin claim names: " + ex.getMessage());
            }
        }
        Map<UUID, List<String>> teams = new HashMap<>();
        Path scoreboard = data.resolve("scoreboard.dat");
        if (Files.exists(scoreboard)) {
            try {
                ListTag list = NbtIo.readCompressed(scoreboard, NbtAccounter.unlimitedHeap()).getCompound("data").getList("Teams", Tag.TAG_COMPOUND);
                for (Tag t : list) {
                    CompoundTag team = (CompoundTag) t;
                    List<String> players = new ArrayList<>();
                    for (Tag p : team.getList("Players", Tag.TAG_STRING)) players.add(p.getAsString());
                    teams.put(UUID.nameUUIDFromBytes(team.getString("Name").getBytes(StandardCharsets.UTF_8)), players);
                }
            } catch (Exception ex) {
                notes.add("Could not read scoreboard teams: " + ex.getMessage());
            }
        }

        String overworld = "minecraft:overworld";
        List<Imported> out = new ArrayList<>();
        for (String provider : root.getAllKeys()) {
            CompoundTag byTeam = root.getCompound(provider);
            for (String key : byTeam.getAllKeys()) {
                UUID id = uuid(key);
                if (id == null) continue;
                long[] packed = byTeam.getLongArray(key);
                Imported im;
                switch (provider) {
                    case "cadmus:admin" -> im = new Imported(null, true, adminNames.getOrDefault(id, "Server Claim"), overworld);
                    case "cadmus:individual" -> im = new Imported(id, false, "Imported Claim", overworld);
                    case "cadmus:vanilla_teams" -> {
                        List<UUID> members = new ArrayList<>();
                        for (String n : teams.getOrDefault(id, List.of())) {
                            GameProfile gp = server.getProfileCache() == null ? null : server.getProfileCache().get(n).orElse(null);
                            if (gp != null) members.add(gp.getId());
                        }
                        if (members.isEmpty()) {
                            notes.add("Skipped a team claim with no known players.");
                            continue;
                        }
                        im = new Imported(members.get(0), false, "Imported Claim", overworld);
                        im.members.addAll(members.subList(1, members.size()));
                    }
                    default -> {
                        notes.add("Skipped claims from unknown provider " + provider + ".");
                        continue;
                    }
                }
                for (long v : packed) {
                    int x = (int) (v >> 38);
                    int z = (int) (v << 26 >> 38);
                    im.chunks.add(ChunkPos.asLong(x, z));
                }
                if (!im.chunks.isEmpty()) out.add(im);
            }
        }
        notes.add("Cadmus doesn't store dimensions: all claims were imported into the overworld.");
        return out;
    }

    // ================= helpers =================

    private static List<Path> list(Path dir, String ext) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> {
                String n = p.getFileName().toString();
                return n.endsWith(ext) && !n.startsWith("._") && Files.isRegularFile(p);
            }).sorted().toList();
        }
    }

    private static String stem(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? n : n.substring(0, dot);
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Config toml(Path p) {
        if (p == null || !Files.exists(p)) return null;
        try {
            return new TomlParser().parse(new StringReader(Files.readString(p, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            ElementsClaims.LOGGER.warn("[Claims] Could not read {}: {}", p, e.toString());
            return null;
        }
    }

    /** A dotted option, written either as nested tables or as one quoted key. */
    private static Object get(Config c, String dotted) {
        if (c == null) return null;
        Object v = c.get(dotted);
        if (v == null) v = c.get(List.of(dotted));
        return v;
    }

    private static String str(Config c, String dotted) {
        Object v = get(c, dotted);
        return v == null ? null : String.valueOf(v);
    }
}
