package com.tac5studios.elementsclaims.claim;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One claim (property): chunks in any dimension with one owner, its ranks, members and flags.
 * Ranks are kept in order: Owner first, Visitor last. Change claims only through {@link ClaimStore}
 * (it keeps the chunk index and saves).
 */
public final class Claim {

    private final UUID id;
    private UUID owner;          // null for admin claims
    private final boolean admin;
    private String name;
    private final String dimension;
    /** dimension -> chunks (ChunkPos.toLong). A property can have chunks in the Overworld, Nether and End. */
    private final Map<String, Set<Long>> chunks = new LinkedHashMap<>();
    private final List<Rank> ranks = new ArrayList<>();
    private final Map<UUID, String> members = new LinkedHashMap<>(); // player -> rank id
    private final Set<UUID> bans = new HashSet<>();
    private boolean privateClaim;
    private final Map<LandFlag, Boolean> landFlags = new EnumMap<>(LandFlag.class);
    private final long created;
    private final java.util.List<SubArea> subAreas = new java.util.ArrayList<>();

    // Expiry
    public long abandonedSince;

    // Admin claim extras
    public boolean keepInventory, noHunger, blockEntry, blockExit;
    public final java.util.List<String> enterCommands = new java.util.ArrayList<>();
    public final java.util.List<String> leaveCommands = new java.util.ArrayList<>();

    public Claim(UUID id, UUID owner, boolean admin, String name, String dimension, long created) {
        this.id = id;
        this.owner = owner;
        this.admin = admin;
        this.name = name;
        this.dimension = dimension;
        this.created = created;
    }

    /** A new claim with the default ranks and land flags. */
    public static Claim create(UUID owner, boolean admin, String name, String dimension) {
        Claim c = new Claim(UUID.randomUUID(), owner, admin, name, dimension, System.currentTimeMillis());
        c.ranks.add(new Rank(Rank.OWNER, "Owner", RankFlag.all()));
        c.ranks.add(new Rank(Rank.ADMIN, "Admin", RankFlag.adminDefaults()));
        c.ranks.add(new Rank(Rank.MEMBER, "Member", RankFlag.memberDefaults()));
        c.ranks.add(new Rank(Rank.VISITOR, "Visitor", admin ? RankFlag.adminVisitorDefaults() : RankFlag.visitorDefaults()));
        for (LandFlag f : LandFlag.values()) c.landFlags.put(f, f.defaultValue());
        return c;
    }

    // ---------- basics ----------

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    /** New owner (sale or abandoned-claim purchase). Members and bans stay; the new owner leaves the member list. */
    public void setOwner(UUID owner) {
        this.owner = owner;
        members.remove(owner);
        bans.remove(owner);
    }

    public java.util.List<SubArea> subAreas() {
        return subAreas;
    }

    public SubArea subAreaAt(String dim, net.minecraft.core.BlockPos pos) {
        for (SubArea a : subAreas) if (a.dimension().equals(dim) && a.contains(pos)) return a;
        return null;
    }

    public SubArea subArea(UUID id) {
        for (SubArea a : subAreas) if (a.id().equals(id)) return a;
        return null;
    }

    /** Rank inside a sub-area: the sub-area's own member rank first, then the claim rank. */
    public Rank rankOf(UUID player, SubArea area) {
        if (area != null && !isOwner(player)) {
            String id = area.members().get(player);
            if (id != null) {
                Rank r = rank(id);
                if (r != null && !r.isOwner() && !r.isVisitor()) return r;
            }
        }
        return rankOf(player);
    }

    public boolean isAdmin() {
        return admin;
    }

    public String name() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public String dimension() {
        return dimension;
    }

    public long created() {
        return created;
    }

    public boolean isOwner(UUID player) {
        return owner != null && owner.equals(player);
    }

    // ---------- chunks ----------

    /** Chunks in one dimension (an empty set is created when there are none yet). */
    public Set<Long> chunks(String dim) {
        return chunks.computeIfAbsent(dim, k -> new HashSet<>());
    }

    /** Every dimension that has chunks of this claim, with its chunks. */
    public Map<String, Set<Long>> chunksByDim() {
        chunks.values().removeIf(Set::isEmpty);
        return chunks;
    }

    /** Chunks in all dimensions together: what counts against the owner's allowance. */
    public int chunkCount() {
        int n = 0;
        for (Set<Long> s : chunks.values()) n += s.size();
        return n;
    }

    public boolean has(String dim, long chunk) {
        Set<Long> s = chunks.get(dim);
        return s != null && s.contains(chunk);
    }

    /** Middle chunk-ish of the claim in its first dimension, for lists ("near x, z"). */
    public ChunkPos center() {
        Set<Long> home = chunks.get(dimension);
        if (home == null || home.isEmpty()) home = chunksByDim().values().stream().findFirst().orElse(Set.of());
        long sx = 0, sz = 0;
        for (long l : home) {
            sx += ChunkPos.getX(l);
            sz += ChunkPos.getZ(l);
        }
        int n = Math.max(1, home.size());
        return new ChunkPos((int) (sx / n), (int) (sz / n));
    }

    // ---------- ranks ----------

    public List<Rank> ranks() {
        return ranks;
    }

    public Rank rank(String id) {
        for (Rank r : ranks) if (r.id().equals(id)) return r;
        return null;
    }

    public Rank ownerRank() {
        return ranks.get(0);
    }

    public Rank visitorRank() {
        return ranks.get(ranks.size() - 1);
    }

    /** The lowest rank above Visitor. /trust puts players here. */
    public Rank lowestTrusted() {
        return ranks.get(Math.max(1, ranks.size() - 2));
    }

    /** The rank this player has here: Owner, a member rank, or Visitor. */
    public Rank rankOf(UUID player) {
        if (isOwner(player)) return ownerRank();
        String id = members.get(player);
        if (id != null) {
            Rank r = rank(id);
            if (r != null && !r.isVisitor()) return r;
        }
        return visitorRank();
    }

    // ---------- members ----------

    public Map<UUID, String> members() {
        return members;
    }

    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }

    /** Owner or a member (any rank above Visitor). */
    public boolean isTrusted(UUID player) {
        return isOwner(player) || isMember(player);
    }

    // ---------- bans / private ----------

    public Set<UUID> bans() {
        return bans;
    }

    public boolean isBanned(UUID player) {
        return bans.contains(player);
    }

    public boolean isPrivate() {
        return privateClaim;
    }

    public void setPrivate(boolean on) {
        this.privateClaim = on;
    }

    // ---------- land flags ----------

    public boolean flag(LandFlag f) {
        Boolean v = landFlags.get(f);
        return v != null ? v : f.defaultValue();
    }

    public void setFlag(LandFlag f, boolean on) {
        landFlags.put(f, on);
    }

    public Map<LandFlag, Boolean> landFlags() {
        return landFlags;
    }

}
