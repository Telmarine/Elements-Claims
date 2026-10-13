package com.tac5studios.elementsclaims.limits;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.config.TomlFile;
import com.tac5studios.elementsclaims.messages.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Optional entity limits (SPEC 8): pets, passive mobs, monsters and villagers, per claim and/or per chunk.
 * Over a limit, breeding, spawn eggs and spawners stop adding to it and the land owner is told.
 * Nothing already there is killed. Easy NPCs never count.
 */
public final class EntityLimits {

    public enum Category { PETS, PASSIVE, MONSTERS, VILLAGERS }

    private static final TomlFile FILE = new TomlFile("limits.toml");
    private static final Map<UUID, Long> LAST_WARN = new HashMap<>();

    private EntityLimits() {}

    public static void load() {
        FILE.load();
    }

    public static Category of(Entity e) {
        if (e.getType().getCategory() == MobCategory.MISC && !(e instanceof AbstractVillager)) return null;
        if (excluded(e.getType())) return null;
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return Category.PETS;
        if (e instanceof AbstractVillager) return Category.VILLAGERS;
        if (e instanceof Enemy) return Category.MONSTERS;
        return Category.PASSIVE;
    }

    /** The category a new mob of this type would count in (pets can't be known before taming). */
    public static Category of(EntityType<?> type) {
        if (excluded(type)) return null;
        MobCategory c = type.getCategory();
        if (c == MobCategory.MONSTER) return Category.MONSTERS;
        if (c == MobCategory.MISC) return null;
        return Category.PASSIVE;
    }

    private static boolean excluded(EntityType<?> type) {
        return "easy_npc".equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace());
    }

    /** True when one more mob of this category may be added at this position. */
    public static boolean mayAdd(Level level, BlockPos pos, Category cat) {
        if (cat == null || !Features.on(Features.ENTITY_LIMITS) || !(level instanceof ServerLevel sl)) return true;
        String key = cat.name().toLowerCase();
        Claim claim = ClaimStore.at(level, pos);
        if (claim != null && Features.on(Features.ENTITY_LIMITS_PER_CLAIM)) {
            int max = FILE.num("entities.per_claim." + key, -1);
            if (max >= 0 && count(sl, claim, cat) >= max) {
                warn(sl, claim, key, max);
                return false;
            }
        }
        if (Features.on(Features.ENTITY_LIMITS_SERVER_WIDE)) {
            int max = FILE.num("entities.per_chunk." + key, -1);
            if (max >= 0 && count(sl, new ChunkPos(pos), cat) >= max) {
                if (claim != null) warn(sl, claim, key, max);
                return false;
            }
        }
        return true;
    }

    private static int count(ServerLevel level, Claim claim, Category cat) {
        int n = 0;
        for (long l : claim.chunks(ClaimStore.dim(level))) {
            if (level.getChunkSource().getChunkNow(ChunkPos.getX(l), ChunkPos.getZ(l)) == null) continue;
            n += count(level, new ChunkPos(l), cat);
        }
        return n;
    }

    private static int count(ServerLevel level, ChunkPos cp, Category cat) {
        AABB box = new AABB(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(),
                cp.getMaxBlockX() + 1, level.getMaxBuildHeight(), cp.getMaxBlockZ() + 1);
        return level.getEntities((Entity) null, box, e -> of(e) == cat).size();
    }

    private static void warn(ServerLevel level, Claim claim, String key, int max) {
        if (claim.owner() == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(claim.owner());
        if (owner == null) return;
        long now = System.currentTimeMillis();
        Long last = LAST_WARN.get(owner.getUUID());
        if (last != null && now - last < 60_000) return;
        LAST_WARN.put(owner.getUUID(), now);
        Msg.send(owner, "limits.entities_full", "claim", claim.name(), "category", Msg.raw("limits.category_" + key), "max", max);
    }
}
