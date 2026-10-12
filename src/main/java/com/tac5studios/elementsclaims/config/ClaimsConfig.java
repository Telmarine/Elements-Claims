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
    public static Item deedItem = Items.PAPER;
    public static Item stickItem = Items.STICK;
    public static int maxSubAreas = 20;
    public static int minDistance = 2;
    public static String defaultName = "{player}'s Claim";
    public static Set<String> disabledDimensions = new HashSet<>();
    public static int claimNameMax = 32;
    public static int maxRanks = 10;
    public static int rankNameMax = 16;

    public static int borderRadius = 3;
    public static int borderMaxSegments = 300;
    public static Block borderBlock = Blocks.GLASS;
    public static int colorOwn, colorTrusted, colorOther, colorAdmin, colorSubArea, colorPreview;

    private ClaimsConfig() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAboutToStart(ServerAboutToStartEvent e) {
        load();
    }

    public static void load() {
        FILE.load();
        starterAllowance = Math.max(0, FILE.num("allowance.starter", 100));
        deedItem = item(FILE.str("deed.item", "minecraft:paper"), Items.PAPER);
        stickItem = item(FILE.str("sub_areas.item", "minecraft:stick"), Items.STICK);
        maxSubAreas = Math.max(1, FILE.num("sub_areas.max_per_claim", 20));
        minDistance = Math.max(0, FILE.num("distance.chunks", 2));
        defaultName = FILE.str("claims.default_name", "{player}'s Claim");
        disabledDimensions = new HashSet<>(FILE.strings("claims.disabled_dimensions"));
        claimNameMax = Math.max(8, Math.min(64, FILE.num("claims.name_max_length", 32)));
        maxRanks = Math.max(3, Math.min(20, FILE.num("ranks.max", 10)));
        rankNameMax = Math.max(4, Math.min(32, FILE.num("ranks.name_max_length", 16)));

        borderRadius = Math.max(1, Math.min(8, FILE.num("borders.radius", 3)));
        borderMaxSegments = Math.max(16, FILE.num("borders.max_segments", 300));
        borderBlock = block(FILE.str("borders.block", "minecraft:glass"), Blocks.GLASS);
        colorOwn = color(FILE.str("borders.colors.own", "#55FF55"), 0x55FF55);
        colorTrusted = color(FILE.str("borders.colors.trusted", "#55FFFF"), 0x55FFFF);
        colorOther = color(FILE.str("borders.colors.other", "#FF5555"), 0xFF5555);
        colorAdmin = color(FILE.str("borders.colors.admin", "#FFAA00"), 0xFFAA00);
        colorSubArea = color(FILE.str("borders.colors.sub_area", "#FFFF55"), 0xFFFF55);
        colorPreview = color(FILE.str("borders.colors.preview", "#FFFFFF"), 0xFFFFFF);
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
