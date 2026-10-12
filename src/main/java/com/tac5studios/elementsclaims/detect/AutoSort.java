package com.tac5studios.elementsclaims.detect;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.protect.Classify;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Layer 2 of mod detection (SPEC 5): at startup every registered block, item and mob is sorted into a flag by
 * what it is. The results and the server owner's overrides live in config/elements_claims/detected.toml:
 *   [overrides.blocks]    "modid:block" = "containers" | "doors" | "redstone" | "interact" | "farming" | "build" | "none"
 *   [overrides.entities]  "modid:mob"   = "trading" | "vehicles" | "farming" | "interact" | "containers" | "build" | "none"
 *   [machines]            "module:Name" = "player" | "allow" | "deny"
 *   [results]             per-mod counts, rewritten every start (for information only)
 * "none" means "never protected" (everyone may use it).
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class AutoSort {

    /** Sentinel for "none": always allowed. */
    public static final String NONE = "none";

    private static final Map<Block, String> BLOCK_OVERRIDES = new IdentityHashMap<>();
    private static final Map<EntityType<?>, String> ENTITY_OVERRIDES = new IdentityHashMap<>();
    private static final Map<String, Map<String, Integer>> RESULTS = new TreeMap<>();
    private static boolean saveLater;
    private static int timer;

    private AutoSort() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(ElementsClaims.MOD_ID).resolve("detected.toml");
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        if (!Features.on(Features.DETECT)) return;
        readFile();
        if (Features.on(Features.DETECT_AUTO_SORT)) sort();
        if (Features.on(Features.DETECT_REPORT)) save();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (!saveLater || ++timer < 200) return;
        timer = 0;
        saveLater = false;
        save();
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        if (saveLater) save();
        saveLater = false;
    }

    static void saveLater() {
        saveLater = true;
    }

    /** Re-read detected.toml (used by /claims reload). */
    public static void reload() {
        readFile();
    }

    // ---------- lookups used by Classify ----------

    /** Owner override for a block: a flag, NONE, or null when not overridden. */
    public static String blockOverride(Block b) {
        return BLOCK_OVERRIDES.get(b);
    }

    public static String entityOverride(EntityType<?> t) {
        return ENTITY_OVERRIDES.get(t);
    }

    // ---------- sorting ----------

    private static void sort() {
        RESULTS.clear();
        int blocks = 0, items = 0, mobs = 0;
        for (Block b : BuiltInRegistries.BLOCK) {
            String mod = BuiltInRegistries.BLOCK.getKey(b).getNamespace();
            String flag = Classify.staticBlockFlag(b);
            if (flag == null) continue;
            bump(mod, "blocks_" + flag);
            blocks++;
        }
        for (Item i : BuiltInRegistries.ITEM) {
            if (!Classify.usedOnBlocks(i)) continue;
            bump(BuiltInRegistries.ITEM.getKey(i).getNamespace(), "items_used_on_blocks");
            items++;
        }
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            MobCategory c = t.getCategory();
            String cat = c == MobCategory.MONSTER ? "mobs_monsters" : c == MobCategory.MISC ? "entities_other" : "mobs_animals";
            bump(BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace(), cat);
            mobs++;
        }
        ElementsClaims.LOGGER.info("[Claims] Auto-sort: {} usable blocks, {} items used on blocks, {} entity types from {} mods.",
                blocks, items, mobs, RESULTS.size());
    }

    private static void bump(String mod, String key) {
        RESULTS.computeIfAbsent(mod, k -> new TreeMap<>()).merge(key, 1, Integer::sum);
    }

    // ---------- the file ----------

    private static void readFile() {
        BLOCK_OVERRIDES.clear();
        ENTITY_OVERRIDES.clear();
        Path p = file();
        if (!Files.exists(p)) return;
        try (CommentedFileConfig cfg = CommentedFileConfig.builder(p).build()) {
            cfg.load();
            if (cfg.get("overrides.blocks") instanceof Config c) {
                for (Config.Entry en : c.entrySet()) {
                    ResourceLocation rl = ResourceLocation.tryParse(en.getKey());
                    if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl) || !(en.getValue() instanceof String v)) continue;
                    if (valid(v)) BLOCK_OVERRIDES.put(BuiltInRegistries.BLOCK.get(rl), v.toLowerCase(Locale.ROOT));
                }
            }
            if (cfg.get("overrides.entities") instanceof Config c) {
                for (Config.Entry en : c.entrySet()) {
                    ResourceLocation rl = ResourceLocation.tryParse(en.getKey());
                    if (rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl) || !(en.getValue() instanceof String v)) continue;
                    if (valid(v)) ENTITY_OVERRIDES.put(BuiltInRegistries.ENTITY_TYPE.get(rl), v.toLowerCase(Locale.ROOT));
                }
            }
            if (cfg.get("machines") instanceof Config c) {
                for (Config.Entry en : c.entrySet()) {
                    if (en.getValue() instanceof String v) Machines.KNOWN.put(en.getKey(), Machines.Mode.parse(v));
                }
            }
        } catch (Exception ex) {
            ElementsClaims.LOGGER.error("[Claims] Could not read {}. Overrides are not used until it's fixed.", p, ex);
        }
    }

    private static boolean valid(String v) {
        return NONE.equalsIgnoreCase(v) || RankFlag.byKey(v) != null;
    }

    /** Rewrite detected.toml: owner overrides and machines kept, results refreshed. */
    public static synchronized void save() {
        StringBuilder b = new StringBuilder();
        b.append("# ============================================================\n");
        b.append("#  ELEMENTS: CLAIMS  —  detected.toml (auto-detect)\n");
        b.append("#  Rewritten at every start. Your [overrides] and [machines] choices are kept.\n");
        b.append("#  Block flags: containers, doors, redstone, interact, farming, build, none\n");
        b.append("#  Mob flags:   trading, vehicles, farming, interact, containers, build, none\n");
        b.append("#  none = never protected (everyone may use it). Reload with /claims reload.\n");
        b.append("# ============================================================\n\n");
        b.append("[overrides.blocks]\n# \"modid:block\" = \"containers\"\n");
        BLOCK_OVERRIDES.forEach((blk, v) -> b.append(q(BuiltInRegistries.BLOCK.getKey(blk).toString())).append(" = ").append(q(v)).append('\n'));
        b.append("\n[overrides.entities]\n# \"modid:mob\" = \"interact\"\n");
        ENTITY_OVERRIDES.forEach((t, v) -> b.append(q(BuiltInRegistries.ENTITY_TYPE.getKey(t).toString())).append(" = ").append(q(v)).append('\n'));
        b.append("\n# Machines seen acting in claims. player = acts as the player who placed it (default),\n");
        b.append("# allow = always, deny = never in other players' claims. Also set with /claims detected.\n");
        b.append("[machines]\n");
        Map<String, Machines.Mode> machines = new LinkedHashMap<>(Machines.KNOWN);
        machines.forEach((k, v) -> b.append(q(k)).append(" = ").append(q(v.key())).append('\n'));
        b.append("\n# Auto-sort results per mod (information only).\n");
        RESULTS.forEach((mod, counts) -> {
            b.append("[results.").append(q(mod)).append("]\n");
            counts.forEach((k, v) -> b.append(k).append(" = ").append(v).append('\n'));
        });
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), b.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ElementsClaims.LOGGER.error("[Claims] Could not write {}.", file(), ex);
        }
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
