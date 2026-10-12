package com.tac5studios.elementsclaims.config;

import com.tac5studios.elementsclaims.claim.LandFlag;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.integration.KnownMods;
import com.tac5studios.elementsclaims.limits.LagLimits;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Master Control File: config/elements_claims/features.toml
 * Feature switch off = everything under it off. Feature on = each child switch decides.
 * Switches marked (restart) need a server restart.
 * Per-mod switches ([mods], [limits.mods]) are only written for mods that are installed (auto-detect rule).
 */
public final class Features {

    public static ModConfigSpec SPEC;

    // Claiming
    public static BooleanValue CLAIMS, CLAIM_DEED, CLAIM_UNCLAIM, CLAIM_MERGE, CLAIM_MIN_DISTANCE, CLAIM_DEED_FIRST_JOIN;

    // Trust
    public static BooleanValue TRUST, TRUST_OFFLINE;

    // Protection (rank flags)
    public static BooleanValue PROTECTION, PROTECT_DENY_MESSAGES;
    private static final Map<RankFlag, BooleanValue> FLAGS = new EnumMap<>(RankFlag.class);

    // Land flags
    public static BooleanValue LAND_FLAGS, TITLES_ACTIONBAR, PERSONAL_SETTINGS;
    private static final Map<LandFlag, BooleanValue> LAND = new EnumMap<>(LandFlag.class);

    // Admin claims
    public static BooleanValue ADMIN_CLAIMS, ADMIN_KEEP_INVENTORY, ADMIN_NO_HUNGER, ADMIN_BLOCK_ENTRY_EXIT, ADMIN_COMMANDS;

    // Auto-detect
    public static BooleanValue DETECT, DETECT_AUTO_SORT, DETECT_MACHINES, DETECT_REPORT;

    // Sub-areas
    public static BooleanValue SUB_AREAS, SUB_AREA_TRUST, SUB_AREA_VISITOR_FLAGS;

    // Integrations
    public static BooleanValue INTEGRATIONS, API;
    private static final Map<String, BooleanValue> MODS = new LinkedHashMap<>();

    // Lag and entity limits
    public static BooleanValue LIMITS, LIMITS_BYPASS_ADMIN_CLAIMS, ENTITY_LIMITS, ENTITY_LIMITS_SERVER_WIDE, ENTITY_LIMITS_PER_CLAIM;
    private static final Map<String, BooleanValue> LIMIT_SWITCHES = new LinkedHashMap<>();
    private static final Map<String, BooleanValue> MOD_LIMITS = new LinkedHashMap<>();

    // Expiry, selling, renting
    public static BooleanValue EXPIRY, EXPIRY_NOTIFY, SELLING, SELL_ABANDONED, RENTING;

    // Importers and maps
    public static BooleanValue IMPORT, IMPORT_OPAC, IMPORT_FTB, IMPORT_FLAN, IMPORT_CADMUS, MAPS, MAP_BLUEMAP, MAP_SQUAREMAP;

    // Land Config menu
    public static BooleanValue MENU, MENU_SOUNDS, MENU_LAND_FLAGS, MENU_RANKS, MENU_CUSTOM_RANKS, MENU_PLAYERS,
            MENU_BANS, MENU_PRIVATE, MENU_RENAME;

    // Borders
    public static BooleanValue BORDERS, BORDERS_DEED, BORDERS_PREVIEW;

    // Admin
    public static BooleanValue ADMIN, ADMIN_INFO, ADMIN_RELOAD;

    private static void build() {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment(
                "Elements: Claims master switches (Master Control File).",
                "Feature off turns off everything under it.",
                "Feature on lets each switch under it decide.",
                "Switches marked (restart) need a server restart.",
                "Numbers and names live in claims.toml and messages.toml."
        );

        b.comment("Claiming land with the Claim Deed.").push("claims");
        CLAIMS = sw(b, "enabled", true, "Turn claiming on or off. Off = no new claims; existing claims stay protected while [protection] is on.");
        CLAIM_DEED = sw(b, "deed", true, "Players claim chunks with the Claim Deed and can get a new one with /claim deed. (restart)");
        CLAIM_UNCLAIM = sw(b, "unclaim", true, "Players can unclaim their chunks (sneak + right-click with the Deed).");
        CLAIM_MERGE = sw(b, "merge", true, "A chunk claimed next to your own claim joins it. Off = every chunk starts a new claim.");
        CLAIM_MIN_DISTANCE = sw(b, "min_distance", false, "Keep a gap between different players' claims. Gap size: distance.chunks in claims.toml.");
        CLAIM_DEED_FIRST_JOIN = sw(b, "deed_on_first_join", false, "Give every new player a Claim Deed the first time they join.");
        b.pop();

        b.comment("Trusting players with /trust and /untrust.").push("trust");
        TRUST = sw(b, "enabled", true, "Turn /trust, /untrust and /trustlist on or off. (restart)");
        TRUST_OFFLINE = sw(b, "offline_players", true, "Players can trust someone who is offline (they must have joined before).");
        b.pop();

        b.comment("Protection. Each rank flag can be switched off here; a switched-off flag is never checked (everyone may).").push("protection");
        PROTECTION = sw(b, "enabled", true, "Turn all claim protection on or off.");
        PROTECT_DENY_MESSAGES = sw(b, "deny_messages", true, "Tell players (above the hotbar) when something is blocked.");
        b.push("flags");
        for (RankFlag f : RankFlag.values()) {
            if (!f.checkedInWorld()) continue;
            FLAGS.put(f, sw(b, f.key(), true, f.description()));
        }
        b.pop();
        b.pop();

        b.comment("The Land Config menu (Deed right-click in your claim, or /claim).").push("menu");
        MENU = sw(b, "enabled", true, "Turn the Land Config menu on or off.");
        MENU_SOUNDS = sw(b, "sounds", true, "Click sounds in menus.");
        MENU_LAND_FLAGS = sw(b, "land_flags", true, "Land owners can change land flags.");
        MENU_RANKS = sw(b, "ranks", true, "Land owners can change what each rank may do.");
        MENU_CUSTOM_RANKS = sw(b, "custom_ranks", true, "Land owners can add, rename, reorder and delete ranks. Max ranks: ranks.max in claims.toml.");
        MENU_PLAYERS = sw(b, "player_management", true, "Promote, demote and remove trusted players from the menu.");
        MENU_BANS = sw(b, "bans", true, "Land owners can ban players from their claim.");
        MENU_PRIVATE = sw(b, "private_claims", true, "Land owners can make a claim private (only trusted players may enter).");
        MENU_RENAME = sw(b, "rename", true, "Land owners can rename their claim.");
        b.pop();

        b.comment("Glowing claim outlines.").push("borders");
        BORDERS = sw(b, "enabled", true, "Turn claim outlines on or off. Colours are in claims.toml.");
        BORDERS_DEED = sw(b, "while_holding_deed", true, "Show nearby claim outlines while holding the Claim Deed.");
        BORDERS_PREVIEW = sw(b, "preview", true, "Show a white outline around the unclaimed chunk you stand in while holding the Deed.");
        b.pop();

        b.comment("Land flags (claim-wide rules set by the land owner). A switched-off flag is never enforced.").push("land_flags");
        LAND_FLAGS = sw(b, "enabled", true, "Turn all land flags on or off.");
        TITLES_ACTIONBAR = sw(b, "titles_above_hotbar", true, "Enter/leave messages show above the hotbar. Off = big title on screen.");
        PERSONAL_SETTINGS = sw(b, "personal_settings", true, "Players can turn enter/leave messages and trust messages off for themselves (My Claims menu).");
        b.push("flags");
        for (LandFlag f : LandFlag.values()) LAND.put(f, sw(b, f.key(), true, "Enforce the " + f.key() + " land flag."));
        b.pop();
        b.pop();

        b.comment("Admin claims (spawn, hubs, jail). Made with the Admin Deed: /claim deed admin.").push("admin_claims");
        ADMIN_CLAIMS = sw(b, "enabled", true, "Turn admin claims on or off. (restart)");
        ADMIN_KEEP_INVENTORY = sw(b, "keep_inventory", true, "Admin claims can be set to keep inventory on death.");
        ADMIN_NO_HUNGER = sw(b, "no_hunger", true, "Admin claims can be set to stop hunger.");
        ADMIN_BLOCK_ENTRY_EXIT = sw(b, "block_entry_exit", true, "Admin claims can block entry or exit (e.g. a jail).");
        ADMIN_COMMANDS = sw(b, "enter_leave_commands", true, "Admin claims can run console commands when a player walks in or out.");
        b.pop();

        b.comment("Auto-detect: sorts every block, mob and machine from every mod into the right flag.").push("detect");
        DETECT = sw(b, "enabled", true, "Turn auto-detect on or off.");
        DETECT_AUTO_SORT = sw(b, "auto_sort", true, "Sort every registered block, item and mob into a flag at startup. Overrides in detected.toml.");
        DETECT_MACHINES = sw(b, "machines", true, "Record machines (fake players) the first time they act in a claim. Allow or deny them with /claims detected.");
        DETECT_REPORT = sw(b, "report", true, "Write the auto-sort results to detected.toml at startup.");
        b.pop();

        b.comment("Sub-areas: block-precise areas inside a claim, drawn with the Sub-area Stick.").push("sub_areas");
        SUB_AREAS = sw(b, "enabled", true, "Turn sub-areas on or off.");
        SUB_AREA_TRUST = sw(b, "trust", true, "Land owners can give players a rank inside one sub-area only.");
        SUB_AREA_VISITOR_FLAGS = sw(b, "visitor_flags", true, "Each sub-area can have its own rules for visitors.");
        b.pop();

        b.comment("Links to other mods. Each one is only used when that mod is installed.").push("integrations");
        INTEGRATIONS = sw(b, "enabled", true, "Turn all mod links on or off. (restart)");
        API = sw(b, "api", true, "Other mods can ask Elements: Claims \"may this player do this here?\".");
        b.push("mods");
        for (KnownMods.Mod m : KnownMods.ALL) {
            if (!ModList.get().isLoaded(m.id())) continue;
            MODS.put(m.id(), sw(b, m.id(), true, m.description()));
        }
        b.pop();
        b.pop();

        b.comment("Lag limits: stop new placements of laggy blocks and entities over a limit. Numbers are in limits.toml.",
                "Nothing already placed is ever removed or switched off.").push("limits");
        LIMITS = sw(b, "enabled", true, "Turn lag limits on or off.");
        LIMITS_BYPASS_ADMIN_CLAIMS = sw(b, "ignore_admin_claims", true, "Admin claims have no lag limits.");
        b.push("vanilla");
        for (LagLimits.Entry e : LagLimits.VANILLA) LIMIT_SWITCHES.put(e.key(), sw(b, e.key(), false, "Limit " + e.key() + " (" + e.scope() + ")."));
        b.pop();
        b.comment("Per-mod limits. Each has its own file in config/elements_claims/mods/.").push("mods");
        for (String modid : LagLimits.modsWithLimits()) {
            if (!ModList.get().isLoaded(modid)) continue;
            MOD_LIMITS.put(modid, sw(b, modid, false, "Use config/elements_claims/mods/" + modid + ".toml limits."));
        }
        b.pop();
        b.pop();

        b.comment("Entity limits: stop breeding, spawn eggs and spawners adding mobs over a limit. Numbers are in limits.toml.").push("entity_limits");
        ENTITY_LIMITS = sw(b, "enabled", false, "Turn entity limits on or off.");
        ENTITY_LIMITS_PER_CLAIM = sw(b, "per_claim", true, "Limits per claim.");
        ENTITY_LIMITS_SERVER_WIDE = sw(b, "per_chunk_everywhere", false, "Limits per chunk everywhere, claimed or not.");
        b.pop();

        b.comment("Abandoned claims. Times are in expiry.toml.").push("expiry");
        EXPIRY = sw(b, "enabled", false, "Mark claims of players who haven't logged in for a while as abandoned, and free them after a grace period.");
        EXPIRY_NOTIFY = sw(b, "notify_owner", true, "Tell owners on login when their claim was marked abandoned.");
        b.pop();

        b.comment("Selling and renting land. Needs Elements: Economy. Prices are in market.toml. When off, none of it shows in game.").push("market");
        SELLING = sw(b, "selling", false, "Land owners can sell a whole claim or a sub-area.");
        SELL_ABANDONED = sw(b, "sell_abandoned", true, "With selling on, abandoned claims go up for sale instead of being freed.");
        RENTING = sw(b, "renting", false, "Land owners can rent out sub-areas (each owner decides whether to use it).");
        b.pop();

        b.comment("Importing claims from other claim mods: /claims import <mod>.").push("import");
        IMPORT = sw(b, "enabled", true, "Turn importers on or off. (restart)");
        IMPORT_OPAC = sw(b, "opac", true, "Import from Open Parties and Claims.");
        IMPORT_FTB = sw(b, "ftb_chunks", true, "Import from FTB Chunks.");
        IMPORT_FLAN = sw(b, "flan", true, "Import from Flan.");
        IMPORT_CADMUS = sw(b, "cadmus", true, "Import from Cadmus / Odyssey Claims.");
        b.pop();

        b.comment("Show claims on web maps. Only used when that map mod is installed.").push("maps");
        MAPS = sw(b, "enabled", true, "Turn map markers on or off. (restart)");
        MAP_BLUEMAP = sw(b, "bluemap", true, "Show claims on BlueMap.");
        MAP_SQUAREMAP = sw(b, "squaremap", true, "Show claims on squaremap.");
        b.pop();

        b.comment("Staff commands under /claims.").push("admin");
        ADMIN = sw(b, "enabled", true, "Turn staff commands on or off. (restart)");
        ADMIN_INFO = sw(b, "info", true, "Staff can see a claim's owner, ranks and flags with /claims info. (restart)");
        ADMIN_RELOAD = sw(b, "reload", true, "Reload claims.toml and messages.toml with /claims reload. (restart)");
        b.pop();

        SPEC = b.build();
    }

    private Features() {}

    private static BooleanValue sw(ModConfigSpec.Builder b, String key, boolean def, String comment) {
        return b.comment(comment).define(key, def);
    }

    /** Registers config/elements_claims/features.toml. Call from the mod constructor. */
    public static void register(ModContainer container) {
        build();
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "elements_claims/features.toml");
    }

    public static boolean on(BooleanValue feature) {
        if (feature == null) return false;
        try {
            return feature.getAsBoolean();
        } catch (IllegalStateException notLoaded) {
            return feature.getDefault();
        }
    }

    /** True only when the parent feature and the child switch are both on. */
    public static boolean on(BooleanValue feature, BooleanValue child) {
        return on(feature) && on(child);
    }

    /** True when protection is on and this rank flag is checked. */
    public static boolean flagOn(RankFlag flag) {
        if (!on(PROTECTION)) return false;
        BooleanValue v = FLAGS.get(flag);
        return v == null || on(v);
    }

    /** True when land flags are on and this land flag is enforced. */
    public static boolean landFlagOn(LandFlag flag) {
        if (!on(LAND_FLAGS)) return false;
        BooleanValue v = LAND.get(flag);
        return v == null || on(v);
    }

    /** True when integrations are on and this installed mod's link is on. */
    public static boolean mod(String modid) {
        if (!on(INTEGRATIONS)) return false;
        BooleanValue v = MODS.get(modid);
        return v != null && on(v);
    }

    /** True when lag limits are on and this vanilla limit is switched on. */
    public static boolean limitOn(String key) {
        if (!on(LIMITS)) return false;
        BooleanValue v = LIMIT_SWITCHES.get(key);
        return v != null && on(v);
    }

    /** True when lag limits are on and this mod's limits file is switched on. */
    public static boolean modLimitsOn(String modid) {
        if (!on(LIMITS)) return false;
        BooleanValue v = MOD_LIMITS.get(modid);
        return v != null && on(v);
    }
}
