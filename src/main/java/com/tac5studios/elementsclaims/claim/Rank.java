package com.tac5studios.elementsclaims.claim;

import java.util.EnumSet;
import java.util.Set;

/** A rank inside one claim. The land owner names ranks and picks their flags. */
public final class Rank {

    public static final String OWNER = "owner";
    public static final String ADMIN = "admin";
    public static final String MEMBER = "member";
    public static final String VISITOR = "visitor";

    private final String id;
    private String name;
    private final EnumSet<RankFlag> flags = EnumSet.noneOf(RankFlag.class);

    public Rank(String id, String name, Set<RankFlag> flags) {
        this.id = id;
        this.name = name;
        this.flags.addAll(flags);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public boolean isOwner() {
        return OWNER.equals(id);
    }

    public boolean isVisitor() {
        return VISITOR.equals(id);
    }

    public boolean has(RankFlag flag) {
        return isOwner() || flags.contains(flag);
    }

    public Set<RankFlag> flags() {
        return flags;
    }

    public void set(RankFlag flag, boolean on) {
        if (on) flags.add(flag);
        else flags.remove(flag);
    }
}
