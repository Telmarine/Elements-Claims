package com.tac5studios.elementsclaims.importer;

import com.tac5studios.elementsclaims.claim.LandFlag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One property (or admin claim) read from another claim mod, shaped the way Elements: Claims works:
 *  - a player's land becomes ONE property holding their chunks in every dimension
 *  - the other mod's sub-claims become sub-areas inside it, with their own trusted players
 *  - admin/server claims stay separate admin claims (one per named area)
 */
public final class Imported {
    public final UUID owner;            // null for admin claims
    public final boolean admin;
    public String name;
    /** dimension -> chunks (ChunkPos.toLong) */
    public final Map<String, Set<Long>> chunks = new LinkedHashMap<>();
    /** Trusted in the whole property (they get the lowest trusted rank). */
    public final Set<UUID> members = new LinkedHashSet<>();
    /** The other mod let everyone build here: Visitors get every flag. */
    public boolean everyone;
    /** Land flags read from the other mod's settings. Missing = our default. */
    public final Map<LandFlag, Boolean> flags = new EnumMap<>(LandFlag.class);
    public final List<Area> areas = new ArrayList<>();

    public Imported(UUID owner, boolean admin, String name) {
        this.owner = owner;
        this.admin = admin;
        this.name = name;
    }

    public Set<Long> chunks(String dim) {
        return chunks.computeIfAbsent(dim, k -> new LinkedHashSet<>());
    }

    public int chunkCount() {
        int n = 0;
        for (Set<Long> s : chunks.values()) n += s.size();
        return n;
    }

    /** A sub-area to create: a block box in one dimension, with players who get the lowest trusted rank inside it. */
    public static final class Area {
        public final String name;
        public final String dim;
        public final int minX, minY, minZ, maxX, maxY, maxZ;
        public final Set<UUID> members = new LinkedHashSet<>();
        public boolean everyone;

        public Area(String name, String dim, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.name = name;
            this.dim = dim;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }
    }
}
