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
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.claim.LandFlag;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.config.Features;
import net.minecraft.core.BlockPos;
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
 *   opac      Open Parties and Claims  world/data/openpartiesandclaims/ (server claims → admin claims, parties → members,
 *                                      sub-claims → sub-areas, protection options → land flags)
 *   ftb       FTB Chunks + FTB Teams   world/ftbchunks/, world/ftbteams/ (server teams → admin claims)
 *   flan      Flan                     <dimension>/data/claims/*.json (boxes rounded out to whole chunks, sub-claims → sub-areas)
 *   cadmus    Cadmus / Odyssey Claims  world/data/cadmus_claims.dat (no dimension stored: imported into the overworld)
 * Every player's land comes in as ONE property holding their chunks in every dimension, like Claims works now.
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
            for (Map.Entry<String, Set<Long>> d : im.chunks.entrySet()) {
                for (long l : d.getValue()) {
                    if (ClaimStore.at(d.getKey(), l) != null) skipped++;
                    else add++;
                }
            }
            if (add > 0) {
                claims++;
                chunks += add;
                if (im.admin) admin++;
            }
        }
        return new Preview(claims, chunks, skipped, admin, notes);
    }

    /**
     * Create the properties. Chunks Elements: Claims already has are skipped. Each player's property becomes their
     * active property when they don't have one yet. Returns how many properties / admin claims were made.
     */
    public static int apply(List<Imported> list) {
        int made = 0;
        for (Imported im : list) {
            if (im.chunkCount() == 0) continue;
            String home = im.chunks.keySet().iterator().next();
            String name = im.name;
            if (!im.admin) {
                String base = name;
                for (int n = 2; Claiming.propertyNamed(im.owner, name) != null; n++) name = base + " " + n;
            }
            boolean firstProperty = !im.admin && Claiming.activeProperty(im.owner) == null;
            Claim c = Claim.create(im.owner, im.admin, name, home);
            im.chunks.forEach((dim, set) -> {
                for (long l : set) if (ClaimStore.at(dim, l) == null) c.chunks(dim).add(l);
            });
            if (c.chunkCount() == 0) continue;
            for (UUID m : im.members) if (!c.isOwner(m)) c.members().put(m, c.lowestTrusted().id());
            if (im.everyone) for (RankFlag f : everyoneFlags()) c.visitorRank().set(f, true);
            im.flags.forEach(c::setFlag);
            for (Imported.Area a : im.areas) {
                // Only keep sub-areas whose chunks really came over.
                boolean ok = true;
                for (int cx = a.minX >> 4; cx <= a.maxX >> 4 && ok; cx++)
                    for (int cz = a.minZ >> 4; cz <= a.maxZ >> 4 && ok; cz++)
                        if (!c.has(a.dim, ChunkPos.asLong(cx, cz))) ok = false;
                if (!ok) continue;
                SubArea s = new SubArea(UUID.randomUUID(), Manage.clip(a.name, ClaimsConfig.claimNameMax), a.dim,
                        new BlockPos(a.minX, a.minY, a.minZ), new BlockPos(a.maxX, a.maxY, a.maxZ));
                for (UUID m : a.members) if (!c.isOwner(m)) s.members().put(m, c.lowestTrusted().id());
                if (a.everyone) s.setVisitorFlags(everyoneFlags());
                c.subAreas().add(s);
            }
            ClaimStore.add(c);
            if (firstProperty) Claiming.setActive(im.owner, c);
            made++;
        }
        return made;
    }

    /** "Everyone may build here" from the other mod: every flag except the ones that hand out control. */
    private static java.util.EnumSet<RankFlag> everyoneFlags() {
        java.util.EnumSet<RankFlag> out = java.util.EnumSet.allOf(RankFlag.class);
        out.remove(RankFlag.MANAGE_LAND);
        out.remove(RankFlag.MANAGE_PLAYERS);
        out.remove(RankFlag.SUB_AREAS);
        return out;
    }

    private static Path world(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    // ================= OPAC =================

    /**
     * OPAC keeps one claims file per player with chunk groups per dimension. Each group points at the player's main
     * config (-1) or one of their sub-configs. Settings live in the player's config file, but only the ones they
     * changed: everything else comes from serverconfig/openpartiesandclaims-default-player-config.toml, and a
     * sub-config falls back to the player's main config.
     *
     * Shape here: all of a player's chunks (every dimension) become one property. Chunks under a sub-config also
     * become sub-areas (full height) with that sub-config's trusted players. Server claims become admin claims,
     * one per sub-config, named like they are in OPAC.
     */
    private static List<Imported> opac(MinecraftServer server, List<String> notes) throws IOException {
        Path base = world(server).resolve("data").resolve("openpartiesandclaims");
        Path claimsDir = base.resolve("player-claims");
        if (!Files.isDirectory(claimsDir)) throw new NoData("No OPAC data in " + claimsDir);
        Path serverConfigs = world(server).resolve("serverconfig");
        Config defaults = toml(serverConfigs.resolve("openpartiesandclaims-default-player-config.toml"));

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
            String username = root.getString("username");
            if (!admin && !username.isEmpty() && ClaimStore.player(owner).lastName == null) ClaimStore.player(owner).lastName = username;

            Config main = admin ? toml(serverConfigs.resolve("openpartiesandclaims-server-claim-config.toml"))
                    : toml(base.resolve("player-configs").resolve(owner + ".toml"));
            List<Config> mainChain = admin ? List.of(nonNull(main)) : List.of(nonNull(main), nonNull(defaults));
            String mainName = str(main, "playerConfig.claims.name");

            // dimension -> sub-config index -> chunks
            Map<String, Map<Integer, Set<Long>>> groups = new LinkedHashMap<>();
            CompoundTag dims = root.getCompound("dimensions");
            for (String dim : dims.getAllKeys()) {
                for (Tag ct : dims.getCompound(dim).getList("claims", Tag.TAG_COMPOUND)) {
                    CompoundTag claim = (CompoundTag) ct;
                    CompoundTag state = claim.getCompound("state");
                    int sub = state.contains("subConfigIndex") ? state.getInt("subConfigIndex") : -1;
                    Set<Long> set = groups.computeIfAbsent(dim, k -> new LinkedHashMap<>()).computeIfAbsent(sub, k -> new java.util.LinkedHashSet<>());
                    for (Tag pt : claim.getList("positions", Tag.TAG_COMPOUND)) {
                        CompoundTag pos = (CompoundTag) pt;
                        set.add(ChunkPos.asLong(pos.getInt("x"), pos.getInt("z")));
                    }
                }
            }

            if (admin) {
                // Server claims: one admin claim per sub-config (spawn, hubs...), all dimensions together.
                Map<Integer, Imported> bySub = new LinkedHashMap<>();
                groups.forEach((dim, subs) -> subs.forEach((sub, set) -> {
                    Imported im = bySub.computeIfAbsent(sub, k -> {
                        String name = k >= 0 ? subName(base, owner, k) : mainName;
                        return new Imported(null, true, name == null || name.isEmpty() ? "Server Claim" : name);
                    });
                    im.chunks(dim).addAll(set);
                }));
                out.addAll(bySub.values());
                continue;
            }

            String name = mainName == null || mainName.isEmpty() ? (username.isEmpty() ? "Imported Claim" : username + "'s Claim") : mainName;
            Imported im = new Imported(owner, false, name);
            Access mainAccess = access(mainChain, owner, partyOf, partyIdOf, partyAllies, partyMembers);
            im.members.addAll(mainAccess.players);
            im.everyone = mainAccess.everyone;
            flags(im, mainChain);
            int areaCount = 0;
            for (Map.Entry<String, Map<Integer, Set<Long>>> d : groups.entrySet()) {
                String dim = d.getKey();
                int[] y = heights(server, dim);
                for (Map.Entry<Integer, Set<Long>> g : d.getValue().entrySet()) {
                    im.chunks(dim).addAll(g.getValue());
                    int sub = g.getKey();
                    if (sub < 0) continue;
                    // Sub-config: its own trust (falling back to the player's main config, then the server default).
                    Config subCfg = subConfig(base, owner, sub);
                    List<Config> chain = List.of(nonNull(subCfg), nonNull(main), nonNull(defaults));
                    Access subAccess = access(chain, owner, partyOf, partyIdOf, partyAllies, partyMembers);
                    String subName = subName(base, owner, sub);
                    if (subName == null || subName.isEmpty()) subName = "Area " + (sub + 1);
                    List<int[]> rects = rects(g.getValue());
                    int n = 0;
                    for (int[] r : rects) {
                        String an = rects.size() == 1 ? subName : subName + " " + (++n);
                        Imported.Area a = new Imported.Area(an, dim, r[0] << 4, y[0], r[1] << 4, (r[2] << 4) + 15, y[1], (r[3] << 4) + 15);
                        a.members.addAll(subAccess.players);
                        a.everyone = subAccess.everyone;
                        im.areas.add(a);
                        areaCount++;
                    }
                }
            }
            out.add(im);
            if (areaCount > ClaimsConfig.maxSubAreas) {
                notes.add(name + " got " + areaCount + " sub-areas from OPAC sub-claims (more than sub_areas.max_per_claim). They were all kept.");
            }
        }
        return out;
    }

    private record Access(Set<UUID> players, boolean everyone) {}

    /**
     * Who has full access under OPAC's fullAccess option, looked up through the config chain.
     * N = nobody, P = party (default), A = party and allies, E = everyone, anything else = a custom group id.
     */
    private static Access access(List<Config> chain, UUID owner, Map<UUID, Set<UUID>> partyOf, Map<UUID, UUID> partyIdOf,
                                 Map<UUID, List<UUID>> partyAllies, Map<UUID, Set<UUID>> partyMembers) {
        boolean protect = bool(chain, "playerConfig.claims.protectClaimedChunks", true);
        String v = first(chain, "playerConfig.claims.protection.exceptions.fullAccess");
        if (v == null || v.isEmpty()) v = "P";
        Set<UUID> out = new java.util.LinkedHashSet<>();
        boolean everyone = !protect || v.equals("E");
        if (v.equals("P") || v.equals("A")) {
            Set<UUID> party = partyOf.get(owner);
            if (party != null) out.addAll(party);
            if (v.equals("A") && partyIdOf.containsKey(owner)) {
                for (UUID ally : partyAllies.getOrDefault(partyIdOf.get(owner), List.of())) {
                    out.addAll(partyMembers.getOrDefault(ally, Set.of()));
                }
            }
        } else if (!v.equals("N") && !v.equals("E")) {
            // Custom groups are stored in the player's own main config.
            for (Config c : chain) {
                Set<UUID> g = customGroup(c, v, new java.util.HashSet<>());
                if (!g.isEmpty()) {
                    out.addAll(g);
                    break;
                }
            }
        }
        out.remove(owner);
        return new Access(out, everyone);
    }

    /** OPAC protection options that line up with our land flags. */
    private static void flags(Imported im, List<Config> chain) {
        String p = "playerConfig.claims.protection.";
        im.flags.put(LandFlag.PVP, bool(chain, p + "exceptions.playersByPlayers", true));
        im.flags.put(LandFlag.EXPLOSIONS, bool(chain, p + "exceptions.blocksByExplosions", false));
        im.flags.put(LandFlag.FIRE_SPREAD, bool(chain, p + "exceptions.fireSpread", false));
        im.flags.put(LandFlag.CROP_TRAMPLING, bool(chain, p + "exceptions.cropTrample", false));
        im.flags.put(LandFlag.MONSTER_SPAWNING, bool(chain, p + "exceptions.naturalSpawnHostile", true));
        im.flags.put(LandFlag.ANIMAL_SPAWNING, bool(chain, p + "exceptions.naturalSpawnFriendly", true));
        im.flags.put(LandFlag.MOB_GRIEFING, !bool(chain, p + "overrideMobGriefingRule", true));
        boolean barrier = bool(chain, p + "pistonBarrier", true) || bool(chain, p + "fluidBarrier", true)
                || bool(chain, p + "dispenserBarrier", true);
        im.flags.put(LandFlag.OUTSIDE_GRIEFING, !barrier);
    }

    private static Config subConfig(Path base, UUID owner, int index) {
        Path dir = base.resolve("player-configs").resolve("sub-configs").resolve(owner.toString());
        if (!Files.isDirectory(dir)) return null;
        try {
            for (Path f : list(dir, ".toml")) {
                String n = stem(f);
                int d = n.lastIndexOf('$');
                if (d >= 0 && Integer.toString(index).equals(n.substring(d + 1))) return toml(f);
            }
        } catch (IOException ignored) {
        }
        return null;
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

    /**
     * Members of an OPAC custom group: "Id[included, groups]{Name|uuid, ...}". The id must match exactly;
     * included groups are followed (once each). Members saved by name only are looked up in the profile cache.
     */
    private static Set<UUID> customGroup(Config main, String id, Set<String> seen) {
        Set<UUID> out = new java.util.LinkedHashSet<>();
        if (!seen.add(id)) return out;
        Object v = main == null ? null : get(main, "playerConfig.customPlayerGroups");
        if (!(v instanceof List<?> groups)) return out;
        for (Object g : groups) {
            if (!(g instanceof String s)) continue;
            int sq = s.indexOf('['), sqEnd = s.indexOf(']'), open = s.indexOf('{'), close = s.indexOf('}');
            if (sq < 0 || sqEnd < sq || open < 0 || close < open) continue;
            if (!s.substring(0, sq).trim().equals(id)) continue;
            for (String inc : s.substring(sq + 1, sqEnd).split(",")) {
                if (!inc.isBlank()) out.addAll(customGroup(main, inc.trim(), seen));
            }
            for (String part : s.substring(open + 1, close).split(",")) {
                String m = part.trim();
                if (m.isEmpty()) continue;
                int bar = m.indexOf('|');
                UUID u = bar >= 0 ? uuid(m.substring(bar + 1)) : null;
                if (u == null) {
                    String name = bar >= 0 ? m.substring(0, bar) : m;
                    MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                    GameProfile gp = server == null || server.getProfileCache() == null ? null : server.getProfileCache().get(name).orElse(null);
                    if (gp != null) u = gp.getId();
                }
                if (u != null) out.add(u);
            }
        }
        return out;
    }

    // ================= FTB Chunks / Teams =================

    /** One property per team (all dimensions together). Server teams become admin claims. */
    @SuppressWarnings("unchecked")
    private static List<Imported> ftb(MinecraftServer server, List<String> notes) throws IOException {
        Path chunksDir = world(server).resolve("ftbchunks");
        if (!Files.isDirectory(chunksDir)) throw new NoData("No FTB Chunks data in " + chunksDir);
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
            Imported im = new Imported(owner, admin, name);
            im.members.addAll(members);
            for (Map.Entry<?, ?> d : dims.entrySet()) {
                if (!(d.getValue() instanceof List<?> list)) continue;
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m && m.get("x") instanceof Number x && m.get("z") instanceof Number z) {
                        im.chunks(String.valueOf(d.getKey())).add(ChunkPos.asLong(x.intValue(), z.intValue()));
                    }
                }
            }
            if (im.chunkCount() > 0) out.add(im);
        }
        return out;
    }

    // ================= Flan =================

    /**
     * All of a player's Flan claims (every dimension) become one property; Flan boxes are rounded out to whole
     * chunks. Flan sub-claims become sub-areas with their exact box and their trusted players. Admin claims stay
     * separate admin claims.
     */
    private static List<Imported> flan(MinecraftServer server, List<String> notes) throws IOException {
        Map<UUID, Imported> byOwner = new LinkedHashMap<>();
        List<Imported> adminClaims = new ArrayList<>();
        boolean any = false;
        for (ServerLevel level : server.getAllLevels()) {
            Path dir = DimensionType.getStorageFolder(level.dimension(), world(server)).resolve("data").resolve("claims");
            if (!Files.isDirectory(dir)) continue;
            any = true;
            String dim = ClaimStore.dim(level);
            int topY = level.getMaxBuildHeight() - 1;
            for (Path f : list(dir, ".json")) {
                boolean adminFile = f.getFileName().toString().equals("!AdminClaims.json");
                UUID owner = adminFile ? null : uuid(stem(f));
                if (!adminFile && owner == null) continue;
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
                    boolean claimAdmin = adminFile || (o.has("AdminClaim") && o.get("AdminClaim").getAsBoolean());
                    String flanName = o.has("Name") ? o.get("Name").getAsString() : "";
                    Imported im;
                    if (claimAdmin) {
                        im = new Imported(null, true, flanName.isEmpty() ? "Server Claim" : flanName);
                        adminClaims.add(im);
                    } else {
                        im = byOwner.computeIfAbsent(owner, k -> {
                            String n = flanName.isEmpty() ? "Imported Claim" : flanName;
                            return new Imported(k, false, n);
                        });
                    }
                    for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
                        for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
                            im.chunks(dim).add(ChunkPos.asLong(cx, cz));
                    im.members.addAll(flanPlayers(o, owner));
                    if (o.has("SubClaims") && o.get("SubClaims").isJsonArray()) {
                        int n = 0;
                        for (JsonElement se : o.getAsJsonArray("SubClaims")) {
                            if (!se.isJsonObject() || !se.getAsJsonObject().has("PosxXzZY")) continue;
                            JsonObject so = se.getAsJsonObject();
                            JsonArray sb = so.getAsJsonArray("PosxXzZY");
                            int sMinY = sb.size() > 4 ? sb.get(4).getAsInt() : level.getMinBuildHeight();
                            int sMaxY = so.has("MaxY") ? so.get("MaxY").getAsInt() : topY;
                            String sName = so.has("Name") && !so.get("Name").getAsString().isEmpty() ? so.get("Name").getAsString() : "Sub-claim " + (++n);
                            Imported.Area a = new Imported.Area(sName, dim, sb.get(0).getAsInt(), sMinY, sb.get(2).getAsInt(),
                                    sb.get(1).getAsInt(), sMaxY, sb.get(3).getAsInt());
                            a.members.addAll(flanPlayers(so, owner));
                            im.areas.add(a);
                        }
                    }
                }
            }
        }
        if (!any) throw new NoData("No Flan claims found (data/claims folders).");
        List<Imported> out = new ArrayList<>(byOwner.values());
        out.addAll(adminClaims);
        notes.add("Flan boxes were rounded out to whole chunks. Each player's Flan claims became one property; sub-claims became sub-areas.");
        return out;
    }

    private static Set<UUID> flanPlayers(JsonObject o, UUID owner) {
        Set<UUID> out = new java.util.LinkedHashSet<>();
        if (o.has("PlayerPerms") && o.get("PlayerPerms").isJsonObject()) {
            for (String key : o.getAsJsonObject("PlayerPerms").keySet()) {
                UUID u = uuid(key);
                if (u != null && !u.equals(owner)) out.add(u);
            }
        }
        return out;
    }

    // ================= Cadmus / Odyssey Claims =================

    /** One property per player (or team). Cadmus doesn't save dimensions, so everything goes to the overworld. */
    private static List<Imported> cadmus(MinecraftServer server, List<String> notes) throws IOException {
        Path data = world(server).resolve("data");
        Path claimsFile = data.resolve("cadmus_claims.dat");
        if (!Files.exists(claimsFile)) throw new NoData("No Cadmus data in " + claimsFile);
        CompoundTag root = NbtIo.readCompressed(claimsFile, NbtAccounter.unlimitedHeap()).getCompound("data");

        Map<UUID, String> adminNames = new HashMap<>();
        Path flagsFile = data.resolve("cadmus_flags.dat");
        if (Files.exists(flagsFile)) {
            try {
                CompoundTag names = NbtIo.readCompressed(flagsFile, NbtAccounter.unlimitedHeap()).getCompound("data").getCompound("names");
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
        Map<UUID, Imported> byOwner = new LinkedHashMap<>();
        List<Imported> out = new ArrayList<>();
        for (String provider : root.getAllKeys()) {
            CompoundTag byTeam = root.getCompound(provider);
            for (String key : byTeam.getAllKeys()) {
                UUID id = uuid(key);
                if (id == null) continue;
                long[] packed = byTeam.getLongArray(key);
                Imported im;
                switch (provider) {
                    case "cadmus:admin" -> {
                        im = new Imported(null, true, adminNames.getOrDefault(id, "Server Claim"));
                        out.add(im);
                    }
                    case "cadmus:individual" -> im = byOwner.computeIfAbsent(id, k -> new Imported(k, false, "Imported Claim"));
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
                        im = byOwner.computeIfAbsent(members.get(0), k -> new Imported(k, false, "Imported Claim"));
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
                    im.chunks(overworld).add(ChunkPos.asLong(x, z));
                }
            }
        }
        out.addAll(0, byOwner.values());
        out.removeIf(i -> i.chunkCount() == 0);
        notes.add("Cadmus doesn't store dimensions: all claims were imported into the overworld.");
        return out;
    }

    // ================= helpers =================

    /** Chunks grouped into as few rectangles as practical: {minCx, minCz, maxCx, maxCz}. */
    private static List<int[]> rects(Set<Long> chunks) {
        java.util.TreeMap<Integer, java.util.TreeSet<Integer>> rows = new java.util.TreeMap<>();
        for (long l : chunks) rows.computeIfAbsent(ChunkPos.getZ(l), k -> new java.util.TreeSet<>()).add(ChunkPos.getX(l));
        List<int[]> open = new ArrayList<>(), done = new ArrayList<>();
        Integer prevZ = null;
        for (Map.Entry<Integer, java.util.TreeSet<Integer>> row : rows.entrySet()) {
            int z = row.getKey();
            List<int[]> runs = new ArrayList<>();
            Integer start = null, prev = null;
            for (int x : row.getValue()) {
                if (start == null) start = x;
                else if (x != prev + 1) {
                    runs.add(new int[]{start, prev});
                    start = x;
                }
                prev = x;
            }
            if (start != null) runs.add(new int[]{start, prev});
            List<int[]> next = new ArrayList<>();
            for (int[] run : runs) {
                int[] grow = null;
                if (prevZ != null && prevZ == z - 1) {
                    for (int[] r : open) if (r[0] == run[0] && r[2] == run[1]) grow = r;
                }
                if (grow != null) {
                    open.remove(grow);
                    grow[3] = z;
                    next.add(grow);
                } else {
                    next.add(new int[]{run[0], z, run[1], z});
                }
            }
            done.addAll(open);
            open = next;
            prevZ = z;
        }
        done.addAll(open);
        return done;
    }

    /** Lowest and highest block of a dimension (overworld heights when it isn't loaded). */
    private static int[] heights(MinecraftServer server, String dim) {
        for (ServerLevel l : server.getAllLevels()) {
            if (ClaimStore.dim(l).equals(dim)) return new int[]{l.getMinBuildHeight(), l.getMaxBuildHeight() - 1};
        }
        return new int[]{-64, 319};
    }

    private static Config nonNull(Config c) {
        return c == null ? Config.inMemory() : c;
    }

    /** The first config in the chain that sets this option. */
    private static String first(List<Config> chain, String dotted) {
        for (Config c : chain) {
            String v = str(c, dotted);
            if (v != null) return v;
        }
        return null;
    }

    private static boolean bool(List<Config> chain, String dotted, boolean def) {
        String v = first(chain, dotted);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }


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

    /** The other mod's data isn't on this server. Not an error worth a stack trace. */
    public static final class NoData extends java.io.IOException {
        public NoData(String msg) { super(msg); }
    }
}
