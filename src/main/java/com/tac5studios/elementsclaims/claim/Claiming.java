package com.tac5studios.elementsclaims.claim;

import com.tac5studios.elementsclaims.log.ActionLog;
import com.mojang.authlib.GameProfile;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.perms.Perm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
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

    /** Properties this player owns right now. Admin claims don't count. */
    public static int propertiesUsed(UUID player) {
        return ClaimStore.ownedBy(player).size();
    }

    /** True when the property limit is in use. */
    public static boolean propertyLimitOn() {
        return Features.on(Features.CLAIM_PROPERTY_LIMIT);
    }

    /** Property limit for a player who may be offline. */
    public static int maxProperties(UUID player) {
        int v = Perm.properties(player);
        return ClaimsConfig.clampProperties(v >= 0 ? v : ClaimsConfig.maxProperties);
    }

    /**
     * The property the player is actively managing: new chunks next to it join it. If the saved one is gone,
     * their oldest property takes over. Null when they own nothing.
     */
    public static Claim activeProperty(UUID player) {
        PlayerData d = ClaimStore.player(player);
        Claim c = d.activeProperty == null ? null : ClaimStore.byId(d.activeProperty);
        if (c != null && !c.isAdmin() && c.isOwner(player)) return c;
        Claim oldest = null;
        for (Claim o : ClaimStore.ownedBy(player)) if (oldest == null || o.created() < oldest.created()) oldest = o;
        d.activeProperty = oldest == null ? null : oldest.id();
        return oldest;
    }

    public static void setActive(UUID player, Claim c) {
        ClaimStore.player(player).activeProperty = c == null ? null : c.id();
        ClaimStore.markDirty();
    }

    /** One of the player's properties by name (not case sensitive), or null. */
    public static Claim propertyNamed(UUID player, String name) {
        for (Claim c : ClaimStore.ownedBy(player)) if (c.name().equalsIgnoreCase(name)) return c;
        return null;
    }

    /** A name for a new property that the owner isn't using yet: "Steve's Claim", then "Steve's Claim 2"... */
    private static String freeName(UUID owner, String base) {
        if (propertyNamed(owner, base) == null) return base;
        for (int n = 2; ; n++) if (propertyNamed(owner, base + " " + n) == null) return base + " " + n;
    }

    // ---------- claim ----------

    /** Claim the chunk the player stands in for their active property. Returns true when claimed. */
    public static boolean claimHere(ServerPlayer player) {
        return claimHere(player, null);
    }

    /**
     * Claim the chunk the player stands in. Messages the player either way. Returns true when claimed.
     * @param newProperty null = add it to the active property (or start the first property). A name = start a new
     *                    property with that name here (/claim create), which then becomes the active one.
     */
    public static boolean claimHere(ServerPlayer player, String newProperty) {
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

        // Which property does this chunk go to? Always the active one, wherever the chunk is.
        // A new property is only ever started on purpose with /claim create (or when the player has none yet).
        Claim target = null;
        String name;
        if (newProperty == null) {
            // The active property can have chunks in any dimension.
            target = activeProperty(id);
            name = freeName(id, ClaimsConfig.defaultName.replace("{player}", player.getGameProfile().getName()));
        } else {
            name = Manage.clip(newProperty, ClaimsConfig.claimNameMax);
            if (name.isBlank()) {
                Msg.send(player, "claim.bad_name");
                return false;
            }
            if (propertyNamed(id, name) != null) {
                Msg.send(player, "menu.name_taken", "claim", name);
                return false;
            }
        }
        if (target == null && propertyLimitOn()) {
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
        boolean isNew = target == null;
        Claim c = ClaimStore.addChunk(target, id, false, dim, pos, name);
        if (isNew) {
            setActive(id, c);
            if (propertiesUsed(id) > 1) Msg.send(player, "claim.new_property", "claim", c.name());
        }
        Msg.send(player, "claim.claimed", "claim", c.name(), "used", used + 1, "max", max);
        if (isNew) ActionLog.log(ActionLog.Category.CLAIMING, player, c, "property made", c.name());
        ActionLog.log(ActionLog.Category.CLAIMING, player, c, "claimed", ActionLog.chunk(dim, pos.x, pos.z));
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
        // Joins the oldest admin claim it touches. Admin claims are never merged either.
        Claim target = null;
        if (Features.on(Features.CLAIM_MERGE)) {
            for (Claim t : ClaimStore.touching(dim, pos, Claim::isAdmin)) {
                if (target == null || t.created() < target.created()) target = t;
            }
        }
        Claim c = ClaimStore.addChunk(target, null, true, dim, pos, Msg.raw("admin.default_name"));
        Msg.send(player, "admin.claimed", "claim", c.name(), "chunks", c.chunkCount());
        ActionLog.log(ActionLog.Category.STAFF, player, c, "admin claimed", ActionLog.chunk(dim, pos.x, pos.z));
        return true;
    }

    // ---------- transfer ----------

    /** Can the receiver take the claim's chunks? The total chunk allowance always applies. */
    public static boolean hasRoom(UUID receiver, Claim c) {
        return ClaimStore.chunksUsed(receiver) + c.chunkCount() <= allowance(receiver);
    }

    /** The receiver's active property, which the claim can be added to, or null when they own none. */
    public static Claim joinTarget(UUID receiver, Claim c) {
        Claim active = activeProperty(receiver);
        return active != null && active != c ? active : null;
    }

    /** Can the receiver keep the claim as a property of its own? */
    public static boolean canKeepSeparate(UUID receiver) {
        return !propertyLimitOn() || propertiesUsed(receiver) < maxProperties(receiver);
    }

    /** Why this player can't take over the claim, as a message key, or null when they can. */
    public static String cantReceive(UUID receiver, Claim c) {
        if (!hasRoom(receiver, c)) return "transfer.no_chunks";
        if (joinTarget(receiver, c) == null && !canKeepSeparate(receiver)) return "transfer.no_property_slot";
        return null;
    }

    /** Message pairs for a receiver's chunk room: used, max and free. */
    public static Object[] room(UUID receiver) {
        int used = ClaimStore.chunksUsed(receiver), max = allowance(receiver);
        return new Object[]{"used", used, "max", max, "free", Math.max(0, max - used)};
    }

    /** Join message pairs. */
    public static Object[] pairs(Object[] a, Object... b) {
        Object[] out = new Object[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** Give a claim to another player and add its chunks (and sub-areas) to their active property. */
    public static void transferInto(Claim c, Claim into) {
        UUID old = c.owner();
        if (old != null && c.id().equals(ClaimStore.player(old).activeProperty)) ClaimStore.player(old).activeProperty = null;
        ClaimStore.moveInto(c, into);
    }

    /** Give a claim to another player. Their own active property stays active. */
    public static void transfer(Claim c, UUID receiver) {
        UUID old = c.owner();
        c.setOwner(receiver);
        c.members().remove(receiver);
        c.bans().remove(receiver);
        c.abandonedSince = 0;
        if (old != null && c.id().equals(ClaimStore.player(old).activeProperty)) ClaimStore.player(old).activeProperty = null;
        if (activeProperty(receiver) == null) setActive(receiver, c);
        ClaimStore.markDirty();
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
        boolean gone = ClaimStore.byId(c.id()) == null;
        ActionLog.log(c.isAdmin() ? ActionLog.Category.STAFF : ActionLog.Category.CLAIMING, player, c,
                c.isAdmin() ? "admin unclaimed" : "unclaimed",
                ActionLog.chunk(dim, pos.x, pos.z) + (gone ? " (last chunk, property removed)" : "") + (own || c.isAdmin() ? "" : " (staff)"));
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
