package com.tac5studios.elementsclaims.integration;

import java.util.List;

/** Mods Elements: Claims has a link for. A link (and its features.toml switch) only exists when the mod is installed. */
public final class KnownMods {

    public record Mod(String id, String description) {}

    public static final List<Mod> ALL = List.of(
            new Mod("create", "Create: machines inside a claim always run; machines and contraptions from outside can't break into it."),
            new Mod("sophisticatedcore", "Sophisticated Backpacks/Storage: pump upgrades respect claims."),
            new Mod("supplementaries", "Supplementaries: cannonballs respect the Explosions flag."),
            new Mod("crystal_tools", "Crystal Tools: vein mining, 3x3 mining and tree chopping respect claims."),
            new Mod("ftbultimine", "FTB Ultimine: area mining respects claims."),
            new Mod("gravestone", "Gravestone: a grave's owner can always open their grave."),
            new Mod("lootr", "Lootr: loot chests stay open to everyone."),
            new Mod("waystones", "Waystones: teleporting into claims you can't enter is blocked."),
            new Mod("easy_npc", "Easy NPC: everyone can talk to NPCs; only the land owner can edit or move them."),
            new Mod("carryon", "Carry On: picking up blocks and mobs respects claims; shops, crates, NPCs and waystones can't be picked up."),
            new Mod("toms_storage", "Tom's Simple Storage: connectors can't reach containers in another player's claim."),
            new Mod("xyndras_crates", "Xyndra's Crates: crates can always be opened."),
            new Mod("spudaciousshops", "Spud's Shops: shops use the Shops: Use / Shops: Create flags."),
            new Mod("elements_economy", "Elements: Economy: shops use the Shops flags; selling and renting land."),
            new Mod("minecolonies", "MineColonies: no claims over colonies."),
            new Mod("apothic_spawners", "Apothic Spawners: changing spawners needs Build."),
            new Mod("piggyback", "Piggyback: riding players uses Vehicles & Riding.")
    );

    private KnownMods() {}
}
