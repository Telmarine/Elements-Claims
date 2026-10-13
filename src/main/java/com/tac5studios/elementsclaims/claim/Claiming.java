package com.tac5studios.elementsclaims.claim;

import com.mojang.authlib.GameProfile;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.perms.Perm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/** Claiming and unclaiming chunks, with every rule checked in one place. */
public final class Claiming {

    private Claiming() {}

    // ---------- allowance ----------

    /** How many chunks this player may claim: claims.limit node, else the starter value. */
    public static int allowance(ServerPlayer player) {
        int v = Perm.limit(player);
        return v >= 0 ? v : ClaimsConfig.starterAllowance;
    }

    public static int allowance(UUID player) {
        int v = Perm.limit(player);
        return v >= 0 ? v : ClaimsConfig.starterAllowance;
    }

    // ---------- properties ----------

    /** How many separate claims this player may have: claims.properties node, else properties.max. Never above 5. */
    public static int maxProperties(ServerPlayer player) {
        int v = Perm.properties(player);
        return ClaimsConfig.clampProperties(v >= 0 ? v : ClaimsConfig.maxProperties);
    }

    /** Separate claims this player owns right now. Admin claims and forfeited claims don't count. */
    public static int propertiesUsed(UUID player) {
        int n = 0;
        for (Claim c : ClaimStore.ownedBy(player)) if (!c.forfeited) n++;
        return n;
    }

    /** True when the property limit is in use (it needs merging on, or every chunk would be its own claim). */
    public static boolean propertyLimitOn() {
        return Features.on(Features.CLAIM_PROPERTY_LIMIT) && Features.on(Features.CLAIM_MERGE);
    }

    /** True when claiming this chunk would join one of the player's existing claims instead of starting a new one. */
    private static boolean joinsOwnClaim(String dim, ChunkPos pos, UUID player) {
        for (ChunkPos n : new ChunkPos[]{
                new ChunkPos(pos.x + 1, pos.z), new ChunkPos(pos.x - 1, pos.z),
                new ChunkPos(pos.x, pos.z + 1), new ChunkPos(pos.x, pos.z - 1)}) {
            Claim c = ClaimStore.at(dim, n);
            if (c != null && !c.isAdmin() && c.isOwner(player)) return true;
        }
        return false;
    }

    // ---------- claim ----------

    /** Claim the chunk the player stands in. Messages the player either way. Returns true when claimed. */
    public static boolean claimHere(ServerPlayer player) {
        if (!Features.on(Features.CLAIMS)) {
            Msg.send(player, "general.disabled");
            return false;
        }
        String dim = ClaimStore.dim(player.level());
        ChunkPos pos = player.chunkPosition();
        if (!ClaimsConfig.dimensionAllowed(dim)) {
            Msg.send(player, "claim.dimension_blocked");
            return false;
        }
        Claim existing = ClaimStore.at(dim, pos);
        if (existing != null) {
            if (existing.isOwner(player.getUUID())) Msg.send(player, "claim.already_yours", "claim", existing.name());
            else Msg.send(player, "claim.taken", "claim", existing.name(), "owner", ownerName(player.server, existing));
            return false;
        }
        UUID id = player.getUUID();
        int used = ClaimStore.chunksUsed(id);
        int max = allowance(player);
        if (used >= max) {
            Msg.send(player, "claim.full", "used", used, "max", max);
            return false;
        }
        if (propertyLimitOn() && !joinsOwnClaim(dim, pos, id)) {
            int owned = propertiesUsed(id);
            int maxProperties = maxProperties(player);
            if (owned >= maxProperties) {
                Msg.send(player, "claim.properties_full", "used", owned, "max", maxProperties);
                return false;
            }
        }
        if (com.tac5studios.elementsclaims.integration.Integrations.inColony(player.level(), player.blockPosition())) {
            Msg.send(player, "claim.colony");
            return false;
        }
        if (Features.on(Features.CLAIM_MIN_DISTANCE) && ClaimsConfig.minDistance > 0) {
            Claim near = nearbyOther(dim, pos, id, ClaimsConfig.minDistance);
            if (near != null) {
                Msg.send(player, "claim.too_close", "claim", near.name(), "distance", ClaimsConfig.minDistance);
                return false;
            }
        }
        String name = ClaimsConfig.defaultName.replace("{player}", player.getGameProfile().getName());
        Claim c = ClaimStore.addChunk(id, false, dim, pos, name, Features.on(Features.CLAIM_MERGE));
        Msg.send(player, "claim.claimed", "claim", c.name(), "used", used + 1, "max", max);
        return true;
    }

    /** Admin Deed: claim the chunk as an admin claim. Admin claims don't count against anything and never expire. */
    public static boolean claimAdminHere(ServerPlayer player) {
        String dim = ClaimStore.dim(player.level());
        ChunkPos pos = player.chunkPosition();
        Claim existing = ClaimStore.at(dim, pos);
        if (existing != null) {
            Msg.send(player, "claim.taken", "claim", existing.name(), "owner", ownerName(player.server, existing));
            return false;
        }
        Claim c = ClaimStore.addChunk(null, true, dim, pos, Msg.raw("admin.default_name"), Features.on(Features.CLAIM_MERGE));
        Msg.send(player, "admin.claimed", "claim", c.name(), "chunks", c.chunks().size());
        return true;
    }

    /** Another owner's claim (or an admin claim) within this many chunks, or null. */
    private static Claim nearbyOther(String dim, ChunkPos pos, UUID player, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Claim c = ClaimStore.at(dim, ChunkPos.asLong(pos.x + dx, pos.z + dz));
                if (c != null && !c.isOwner(player)) return c;
            }
        }
        return null;
    }

    // ---------- unclaim ----------

    /** Unclaim the chunk the player stands in. Owners, and staff with claims.bypass. */
    public static boolean unclaimHere(ServerPlayer player) {
        if (!Features.on(Features.CLAIMS, Features.CLAIM_UNCLAIM)) {
            Msg.send(player, "general.disabled");
            return false;
        }
        String dim = ClaimStore.dim(player.level());
        ChunkPos pos = player.chunkPosition();
        Claim c = ClaimStore.at(dim, pos);
        if (c == null) {
            Msg.send(player, "claim.not_claimed");
            return false;
        }
        boolean own = c.isOwner(player.getUUID());
        if (!own) {
            boolean staff = c.isAdmin() ? Perm.has(player, Perm.ADMIN) : Perm.has(player, Perm.BYPASS);
            if (!staff) {
                Msg.send(player, "claim.not_yours", "claim", c.name());
                return false;
            }
        }
        ClaimStore.removeChunk(dim, pos);
        if (own) {
            int used = ClaimStore.chunksUsed(player.getUUID());
            Msg.send(player, "claim.unclaimed", "claim", c.name(), "used", used, "max", allowance(player));
        } else {
            Msg.send(player, "claim.unclaimed_other", "claim", c.name(), "owner", ownerName(player.server, c));
        }
        return true;
    }

    // ---------- names ----------

    public static String ownerName(MinecraftServer server, Claim c) {
        if (c.isAdmin() || c.owner() == null) return Msg.raw("general.server_name");
        return name(server, c.owner());
    }

    public static String name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        if (server.getProfileCache() != null) {
            GameProfile p = server.getProfileCache().get(id).orElse(null);
            if (p != null && p.getName() != null) return p.getName();
        }
        PlayerData d = ClaimStore.players().get(id);
        if (d != null && d.lastName != null) return d.lastName;
        return id.toString().substring(0, 8);
    }
}
