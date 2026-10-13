package com.tac5studios.elementsclaims.claim;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What a rank may do inside a claim. Set per rank by the land owner (SPEC section 3).
 * The Owner rank always has every flag.
 */
public enum RankFlag {
    ENTER("Walk into the claim."),
    BUILD("Break and place blocks, and use items on blocks."),
    CONTAINERS("Open chests, barrels, furnaces and any block with an inventory."),
    DOORS("Open doors, trapdoors and fence gates."),
    REDSTONE("Use buttons, levers, repeaters, comparators and note blocks."),
    INTERACT("Use any other block or mob (beds, crafting tables, sitting, leashing...)."),
    FARMING("Harvest, plant, shear, breed and feed animals; use hoes and bone meal."),
    TRADING("Trade with villagers and wandering traders."),
    VEHICLES("Ride and place boats, minecarts and rideable mobs."),
    ITEM_PICKUP("Pick up items (your own dropped items can always be picked up)."),
    FIRE("Light fires (flint and steel, fire charges)."),
    TELEPORT_ITEMS("Land ender pearls and chorus fruit teleports in the claim."),
    FLIGHT("Fly and use elytra in the claim."),
    ATTACK_PLAYERS("Hit players in the claim."),
    ATTACK_PETS("Hit tamed pets in the claim."),
    ATTACK_ANIMALS("Hit animals, villagers and other passive mobs."),
    ATTACK_MONSTERS("Hit hostile mobs."),
    SHOPS_USE("Use shops in the claim."),
    SHOPS_CREATE("Create shops in the claim."),
    MANAGE_PLAYERS("Promote, demote and remove players."),
    MANAGE_LAND("Rename the claim and see its ranks. Flags are always set by the owner."),
    SUB_AREAS("Create and edit sub-areas."),
    ADMIN_MODE("Trust, untrust, promote, demote, remove and ban players ranked below them. Only the owner sees and sets this.");

    private final String description;

    RankFlag(String description) {
        this.description = description;
    }

    /** Management rights: never given to Visitors, and never part of sub-area visitor rules. */
    public boolean isManagement() {
        return this == MANAGE_PLAYERS || this == MANAGE_LAND || this == SUB_AREAS || this == ADMIN_MODE;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String description() {
        return description;
    }

    /** Flags checked by the world protection (the rest are menu/management rights or come with later features). */
    public boolean checkedInWorld() {
        return switch (this) {
            case SHOPS_USE, SHOPS_CREATE, MANAGE_PLAYERS, MANAGE_LAND, SUB_AREAS, ADMIN_MODE -> false;
            default -> true;
        };
    }

    public static RankFlag byKey(String key) {
        try {
            return valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Set<RankFlag> all() {
        return EnumSet.allOf(RankFlag.class);
    }

    public static Set<RankFlag> adminDefaults() {
        Set<RankFlag> s = all();
        s.remove(MANAGE_PLAYERS);
        s.remove(MANAGE_LAND);
        s.remove(ADMIN_MODE);
        return s;
    }

    public static Set<RankFlag> memberDefaults() {
        return EnumSet.of(ENTER, BUILD, CONTAINERS, DOORS, REDSTONE, INTERACT, FARMING, TRADING, VEHICLES,
                ITEM_PICKUP, TELEPORT_ITEMS, FLIGHT, ATTACK_ANIMALS, ATTACK_MONSTERS, SHOPS_USE);
    }

    /** Visitor = everyone who isn't a member: walk around, use shops and fight hostile mobs. Farm animals stay safe. */
    public static Set<RankFlag> visitorDefaults() {
        return EnumSet.of(ENTER, SHOPS_USE, ATTACK_MONSTERS);
    }

    /** Admin claims (SPEC 8c): visitors may also use ender/community chests, crates and NPC shops. */
    public static Set<RankFlag> adminVisitorDefaults() {
        return EnumSet.of(ENTER, SHOPS_USE, CONTAINERS, INTERACT, TRADING, DOORS, ATTACK_MONSTERS);
    }
}
