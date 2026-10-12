package com.tac5studios.elementsclaims.importer;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One claim read from another mod: owner (null for admin claims), name, dimension, chunks, trusted players. */
public final class Imported {
    public final UUID owner;
    public final boolean admin;
    public String name;
    public final String dimension;
    public final Set<Long> chunks = new LinkedHashSet<>();
    public final Set<UUID> members = new LinkedHashSet<>();

    public Imported(UUID owner, boolean admin, String name, String dimension) {
        this.owner = owner;
        this.admin = admin;
        this.name = name;
        this.dimension = dimension;
    }
}
