package com.tac5studios.elementsclaims.limits;

import com.electronwill.nightconfig.core.Config;
import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.config.TomlFile;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.perms.Perm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Placement limits for laggy blocks and entities (SPEC 8b). Only new placements are refused;
 * nothing already placed is removed or switched off.
 *   - vanilla list: each has its own switch in features.toml [limits.vanilla] and a number in limits.toml
 *   - [custom] entries in limits.toml (any block or entity id, or #tag)
 *   - per-mod files config/elements_claims/mods/<modid>.toml, only for installed mods
 */
public final class LagLimits {

    public enum Kind { BLOCK, ENTITY }

    /** A built-in limit. {@code targets} are ids or #tags. */
    public record Entry(String key, Kind kind, String scope, int def, List<String> targets) {}

    /** A live limit: what it matches, where it counts, and the number. */
    public record Limit(String name, Kind kind, boolean claimScope, int max, List<String> targets) {}

    public static final List<Entry> VANILLA = List.of(
            new Entry("armor_stand", Kind.ENTITY, "chunk", 16, List.of("minecraft:armor_stand")),
            new Entry("item_frame", Kind.ENTITY, "chunk", 64, List.of("minecraft:item_frame", "minecraft:glow_item_frame")),
            new Entry("minecart", Kind.ENTITY, "chunk", 16, List.of("minecraft:minecart", "minecraft:chest_minecart", "minecraft:hopper_minecart",
                    "minecraft:furnace_minecart", "minecraft:tnt_minecart", "minecraft:command_block_minecart", "minecraft:spawner_minecart")),
            new Entry("boat", Kind.ENTITY, "chunk", 16, List.of("minecraft:boat", "minecraft:chest_boat")),
            new Entry("painting", Kind.ENTITY, "chunk", 32, List.of("minecraft:painting")),
            new Entry("hopper", Kind.BLOCK, "chunk", 32, List.of("minecraft:hopper")),
            new Entry("observer", Kind.BLOCK, "chunk", 32, List.of("minecraft:observer")),
            new Entry("piston", Kind.BLOCK, "chunk", 32, List.of("minecraft:piston", "minecraft:sticky_piston")),
            new Entry("comparator", Kind.BLOCK, "chunk", 64, List.of("minecraft:comparator")),
            new Entry("repeater", Kind.BLOCK, "chunk", 64, List.of("minecraft:repeater")),
            new Entry("dropper_dispenser", Kind.BLOCK, "chunk", 32, List.of("minecraft:dropper", "minecraft:dispenser")),
            new Entry("redstone_lamp", Kind.BLOCK, "chunk", 64, List.of("minecraft:redstone_lamp")),
            new Entry("furnace", Kind.BLOCK, "chunk", 32, List.of("minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker")),
            new Entry("beacon", Kind.BLOCK, "claim", 4, List.of("minecraft:beacon")),
            new Entry("spawner", Kind.BLOCK, "claim", 4, List.of("minecraft:spawner"))
    );

    /** Mods with a built-in limits file (written only when the mod is installed). */
    public static List<String> modsWithLimits() {
        return List.of("create", "toms_storage");
    }

    private static final TomlFile FILE = new TomlFile("limits.toml");
    private static final List<Limit> LIVE = new ArrayList<>();

    private LagLimits() {}

    /** Read limits.toml and the installed mods' files. Called at server start and by /claims reload. */
    public static void load() {
        FILE.load();
        LIVE.clear();
        for (Entry e : VANILLA) {
            if (!Features.limitOn(e.key())) continue;
            LIVE.add(new Limit(e.key(), e.kind(), "claim".equals(FILE.str("scopes." + e.key(), e.scope())),
                    FILE.num("vanilla." + e.key(), e.def()), e.targets()));
        }
        for (Config c : FILE.tables("custom.entries")) addCustom(c, "custom");
        for (String modid : modsWithLimits()) {
            if (!ModList.get().isLoaded(modid)) continue;
            TomlFile mod = new TomlFile("mods/" + modid + ".toml");
            mod.load(); // writes the default file the first time, even while switched off
            if (!Features.modLimitsOn(modid)) continue;
            for (Config c : mod.tables("limits")) addCustom(c, modid);
        }
        ElementsClaims.LOGGER.info("[Claims] {} lag limits active.", LIVE.size());
    }

    private static void addCustom(Config c, String source) {
        Object target = c.get("target");
        Object max = c.get("max");
        if (!(target instanceof String t) || !(max instanceof Number n)) {
            ElementsClaims.LOGGER.warn("[Claims] Skipped a {} limit without target/max: {}", source, c);
            return;
        }
        if (c.get("enabled") instanceof Boolean b && !b) return;
        String scope = c.get("scope") instanceof String s ? s : "chunk";
        String name = c.get("name") instanceof String s ? s : t;
        Kind kind = isEntity(t) ? Kind.ENTITY : Kind.BLOCK;
        LIVE.add(new Limit(name, kind, "claim".equals(scope), n.intValue(), List.of(t)));
    }

    private static boolean isEntity(String target) {
        if (target.startsWith("#")) return false;
        ResourceLocation rl = ResourceLocation.tryParse(target);
        return rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl) && !BuiltInRegistries.BLOCK.containsKey(rl);
    }

    // ---------- matching ----------

    private static boolean matches(BlockState state, List<String> targets) {
        for (String t : targets) {
            if (t.startsWith("#")) {
                ResourceLocation rl = ResourceLocation.tryParse(t.substring(1));
                if (rl != null && state.is(TagKey.create(Registries.BLOCK, rl))) return true;
            } else if (t.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(EntityType<?> type, List<String> targets) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        for (String t : targets) {
            if (t.startsWith("#")) {
                ResourceLocation rl = ResourceLocation.tryParse(t.substring(1));
                if (rl != null && type.is(TagKey.create(Registries.ENTITY_TYPE, rl))) return true;
            } else if (t.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean exempt(ServerPlayer p, Level level, BlockPos pos) {
        if (Perm.has(p, Perm.BYPASS)) return true;
        Claim c = ClaimStore.at(level, pos);
        return c != null && c.isAdmin() && Features.on(Features.LIMITS_BYPASS_ADMIN_CLAIMS);
    }

    /** Chunks counted for a limit: the chunk, or every loaded chunk of the claim. */
    private static List<LevelChunk> area(Level level, BlockPos pos, boolean claimScope) {
        List<LevelChunk> out = new ArrayList<>();
        Claim c = claimScope ? ClaimStore.at(level, pos) : null;
        if (c == null) {
            out.add(level.getChunkAt(pos));
            return out;
        }
        for (long l : c.chunks()) {
            LevelChunk ch = level.getChunkSource().getChunkNow(ChunkPos.getX(l), ChunkPos.getZ(l));
            if (ch != null) out.add(ch);
        }
        return out;
    }

    // ---------- blocks ----------

    /** Called after a player placed a block. Returns the refusal message, or null when allowed. */
    public static String checkBlock(ServerPlayer p, Level level, BlockPos pos, BlockState placed) {
        if (LIVE.isEmpty() || !Features.on(Features.LIMITS) || exempt(p, level, pos)) return null;
        for (Limit lim : LIVE) {
            if (lim.kind() != Kind.BLOCK || !matches(placed, lim.targets())) continue;
            int count = 0;
            Predicate<BlockState> test = s -> matches(s, lim.targets());
            for (LevelChunk ch : area(level, pos, lim.claimScope())) count += count(ch, test);
            if (count > lim.max()) {
                return Msg.raw(lim.claimScope() ? "limits.reached_claim" : "limits.reached_chunk",
                        "limit", nice(lim.name()), "count", count - 1, "max", lim.max());
            }
        }
        return null;
    }

    /** Blocks matching the test in a chunk. Uses block entities when the block has one, else the section palettes. */
    private static int count(LevelChunk chunk, Predicate<BlockState> test) {
        int n = 0;
        Map<BlockPos, BlockEntity> bes = chunk.getBlockEntities();
        boolean anyBe = false;
        for (BlockEntity be : bes.values()) {
            if (test.test(be.getBlockState())) {
                n++;
                anyBe = true;
            }
        }
        if (anyBe) return n;
        for (LevelChunkSection sec : chunk.getSections()) {
            if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(test)) continue;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if (test.test(sec.getBlockState(x, y, z))) n++;
        }
        return n;
    }

    // ---------- entities ----------

    /** Before a player places an entity of this type at pos. Returns the refusal message, or null when allowed. */
    public static String checkEntity(ServerPlayer p, Level level, BlockPos pos, EntityType<?> type) {
        if (LIVE.isEmpty() || !Features.on(Features.LIMITS) || exempt(p, level, pos)) return null;
        for (Limit lim : LIVE) {
            if (lim.kind() != Kind.ENTITY || !matches(type, lim.targets())) continue;
            int count = 0;
            for (LevelChunk ch : area(level, pos, lim.claimScope())) {
                ChunkPos cp = ch.getPos();
                AABB box = new AABB(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(),
                        cp.getMaxBlockX() + 1, level.getMaxBuildHeight(), cp.getMaxBlockZ() + 1);
                for (Entity e : level.getEntities((Entity) null, box, e -> matches(e.getType(), lim.targets()))) count++;
            }
            if (count >= lim.max()) {
                return Msg.raw(lim.claimScope() ? "limits.reached_claim" : "limits.reached_chunk",
                        "limit", nice(lim.name()), "count", count, "max", lim.max());
            }
        }
        return null;
    }

    private static String nice(String key) {
        int c = key.indexOf(':');
        String s = (c >= 0 ? key.substring(c + 1) : key).replace('#', ' ').replace('_', ' ').trim();
        return s.isEmpty() ? key : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
