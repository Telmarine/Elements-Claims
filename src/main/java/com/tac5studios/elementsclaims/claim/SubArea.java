package com.tac5studios.elementsclaims.claim;

import net.minecraft.core.BlockPos;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A block-precise box inside a claim (SPEC 9, sub-areas): shop plots, rented rooms, farms with their own rules.
 * Inside it a player's rank comes from the sub-area's own members first, then the claim.
 * Visitor flags, when set, replace the claim's Visitor flags inside the box.
 */
public final class SubArea {

    private final UUID id;
    private String name;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private EnumSet<RankFlag> visitorFlags; // null = same as the claim's Visitor rank

    // Selling and renting
    public long salePrice = -1;
    public boolean rentable;
    public long rentPrice;
    public int rentDays = 7;
    public String rentRank = Rank.MEMBER;
    public UUID renter;
    public long rentUntil;
    public boolean rentReturnItems = true;

    public SubArea(UUID id, String name, BlockPos a, BlockPos b) {
        this.id = id;
        this.name = name;
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
