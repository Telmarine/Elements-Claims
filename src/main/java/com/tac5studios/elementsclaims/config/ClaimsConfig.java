package com.tac5studios.elementsclaims.config;

import com.tac5studios.elementsclaims.ElementsClaims;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.HashSet;
import java.util.Set;

/** config/elements_claims/claims.toml — numbers, items and colours. Read at start and by /claims reload. */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ClaimsConfig {

    private static final TomlFile FILE = new TomlFile("claims.toml");

    public static int starterAllowance = 100;
    /** Hard cap on separate claims per player. Neither the config nor a permission node can go above this. */
    public static final int PROPERTIES_HARD_MAX = 5;
    public static int maxProperties = 1;
    public static Item deedItem = Items.PAPER;
    public static int deedCooldownMinutes = 30;
    public static int subAreaLeaveDays = 7;
    public static int backupsKeepHourly = 5, backupsKeepDaily = 7, logKeepDays = 30;
    public static String alertWebhook = "";
    public static String alertRoleId = "";
    public static Item stickItem = Items.STICK;
    public static int maxSubAreas = 20;
    public static int minDistance = 2;
    public static String defaultName = "{player}'s Claim";
    public static Set<String> disabledDimensions = new HashSet<>();
    public static int claimNameMax = 32;
    public static int maxRanks = 10;
    public static int rankNameMax = 16;

    public static int borderRadius = 2;
    public static int borderMaxSegments = 300;
    public static Block blockOwn = Blocks.LIME_CONCRETE, blockTrusted = Blocks.LIGHT_BLUE_CONCRETE, blockOther = Blocks.RED_CONCRETE,
            blockAdmin = Blocks.ORANGE_CONCRETE, blockSubArea = Blocks.YELLOW_CONCRETE, blockPreview = Blocks.WHITE_CONCRETE;
    public static int colorOther, colorAdmin;

    private ClaimsConfig() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAboutToStart(ServerAboutToStartEvent e) {
        load();
    }

    public static void load() {
        FILE.load();
        starterAllowance = Math.max(0, FILE.num("allowance.starter", 100));
        maxProperties = clampProperties(FILE.num("properties.max", 1));
        deedItem = item(FILE.str("deed.item", "minecraft:paper"), Items.PAPER);
        deedCooldownMinutes = Math.max(0, FILE.num("deed.cooldown_minutes", 30));
        stickItem = item(FILE.str("sub_areas.item", "minecraft:stick"), Items.STICK);
        maxSubAreas = Math.max(1, FILE.num("sub_areas.max_per_claim", 20));
        subAreaLeaveDays = Math.max(1, Math.min(60, FILE.num("sub_areas.leave_days", 7)));
        backupsKeepHourly = Math.max(1, Math.min(48, FILE.num("backups.keep_hourly", 5)));
        backupsKeepDaily = Math.max(1, Math.min(60, FILE.num("backups.keep_daily", 7)));
        logKeepDays = Math.max(1, Math.min(365, FILE.num("log.keep_days", 30)));
        alertWebhook = FILE.str("alerts.discord_webhook", "");
        alertRoleId = FILE.str("alerts.discord_role_id", "");
        minDistance = Math.max(0, FILE.num("distance.chunks", 2));
        defaultName = FILE.str("claims.default_name", "{player}'s Claim");
        disabledDimensions = new HashSet<>(FILE.strings("claims.disabled_dimensions"));
        claimNameMax = Math.max(8, Math.min(64, FILE.num("claims.name_max_length", 32)));
        maxRanks = Math.max(3, Math.min(20, FILE.num("ranks.max", 10)));
        rankNameMax = Math.max(4, Math.min(32, FILE.num("ranks.name_max_length", 16)));

        borderRadius = Math.max(1, Math.min(2, FILE.num("borders.radius", 2)));
        borderMaxSegments = Math.max(16, Math.min(300, FILE.num("borders.max_segments", 300)));
        blockOwn = block(FILE.str("borders.blocks.own", "minecraft:lime_concrete"), Blocks.LIME_CONCRETE);
        blockTrusted = block(FILE.str("borders.blocks.trusted", "minecraft:light_blue_concrete"), Blocks.LIGHT_BLUE_CONCRETE);
        blockOther = block(FILE.str("borders.blocks.other", "minecraft:red_concrete"), Blocks.RED_CONCRETE);
        blockAdmin = block(FILE.str("borders.blocks.admin", "minecraft:orange_concrete"), Blocks.ORANGE_CONCRETE);
        blockSubArea = block(FILE.str("borders.blocks.sub_area", "minecraft:yellow_concrete"), Blocks.YELLOW_CONCRETE);
        blockPreview = block(FILE.str("borders.blocks.preview", "minecraft:white_concrete"), Blocks.WHITE_CONCRETE);
        colorOther = color(FILE.str("maps.colors.other", "#FF5555"), 0xFF5555);
        colorAdmin = color(FILE.str("maps.colors.admin", "#FFAA00"), 0xFFAA00);
    }

    /** Keeps a property count between 1 and the hard cap of 5. */
    public static int clampProperties(int n) {
        return Math.max(1, Math.min(PROPERTIES_HARD_MAX, n));
    }

    public static boolean dimensionAllowed(String dimension) {
        return !disabledDimensions.contains(dimension);
    }

    private static Item item(String id, Item def) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            ElementsClaims.LOGGER.warn("[Claims] claims.toml: unknown item '{}', using {}.", id, BuiltInRegistries.ITEM.getKey(def));
            return def;
        }
        return BuiltInRegistries.ITEM.get(rl);
    }

    private static Block block(String id, Block def) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
            ElementsClaims.LOGGER.warn("[Claims] claims.toml: unknown block '{}', using {}.", id, BuiltInRegistries.BLOCK.getKey(def));
            return def;
        }
        return BuiltInRegistries.BLOCK.get(rl);
    }

    private static int color(String hex, int def) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            ElementsClaims.LOGGER.warn("[Claims] claims.toml: '{}' is not a colour like #55FF55.", hex);
            return def;
        }
    }
}
