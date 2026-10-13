package com.tac5studios.elementsclaims.claim;

import net.minecraft.core.BlockPos;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A block-precise box inside a claim (SPEC 9, sub-areas): shop plots, rooms, farms with their own rules.
 * Inside it a player's rank comes from the sub-area's own members first, then the claim.
 * Visitor flags, when set, replace the claim's Visitor flags inside the box.
 */
public final class SubArea {

    private final UUID id;
    private String name;
    /** The dimension the box is in (a property can have chunks in several). */
    private final String dimension;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private EnumSet<RankFlag> visitorFlags; // null = same as the claim's Visitor rank
    /** Blocks placed here by players other than the owner: BlockPos.asLong() -> who placed it. */
    public final Map<Long, UUID> placed = new java.util.HashMap<>();
    /** Players the owner is removing who still had blocks here: player -> time their removal goes through. */
    public final Map<UUID, Long> leaving = new LinkedHashMap<>();
    /** The sub-area's manager: a trusted player the owner picked to add, remove and rank players here while they're away. */
    public UUID manager;


    public SubArea(UUID id, String name, String dimension, BlockPos a, BlockPos b) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        setBox(a, b);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void rename(String n) {
        this.name = n;
    }

    public void setBox(BlockPos a, BlockPos b) {
        minX = Math.min(a.getX(), b.getX());
        minY = Math.min(a.getY(), b.getY());
        minZ = Math.min(a.getZ(), b.getZ());
        maxX = Math.max(a.getX(), b.getX());
        maxY = Math.max(a.getY(), b.getY());
        maxZ = Math.max(a.getZ(), b.getZ());
    }

    public String dimension() {
        return dimension;
    }

    public boolean contains(BlockPos p) {
        return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY && p.getZ() >= minZ && p.getZ() <= maxZ;
    }

    public BlockPos min() {
        return new BlockPos(minX, minY, minZ);
    }

    public BlockPos max() {
        return new BlockPos(maxX, maxY, maxZ);
    }

    public int volume() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public Map<UUID, String> members() {
        return members;
    }

    public Set<RankFlag> visitorFlags() {
        return visitorFlags;
    }

    public void setVisitorFlags(Set<RankFlag> flags) {
        this.visitorFlags = flags == null ? null : (flags.isEmpty() ? EnumSet.noneOf(RankFlag.class) : EnumSet.copyOf(flags));
    }
}
