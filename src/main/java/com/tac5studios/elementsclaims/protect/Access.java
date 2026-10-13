package com.tac5studios.elementsclaims.protect;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.Rank;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.detect.Machines;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.perms.Perm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** "May this player do X here?" — the one check every protection uses. */
public final class Access {

    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

    private Access() {}

    /** True when allowed. Unclaimed land, switched-off flags and staff with bypass are always allowed. */
    public static boolean can(Player player, Level level, BlockPos pos, RankFlag flag) {
        if (level.isClientSide() || !Features.flagOn(flag)) return true;
        Claim c = ClaimStore.at(level, pos);
        return c == null || can(player, c, pos, flag);
    }

    /** Claim-wide check (no sub-area). */
    public static boolean can(Player player, Claim c, RankFlag flag) {
        return can(player, c, null, flag);
    }

    /** Check at a position inside claim {@code c}; {@code pos} picks the sub-area (may be null). */
    public static boolean can(Player player, Claim c, BlockPos pos, RankFlag flag) {
        if (c == null || !Features.flagOn(flag)) return true;
        if (player instanceof FakePlayer fake) return Machines.allowed(fake, c, pos, flag);
        if (!(player instanceof ServerPlayer sp)) return true;
        if (allowedFor(sp.getUUID(), c, ClaimStore.dim(sp.level()), pos, flag)) return true;
        if (!staff(sp, c)) return false;
        logBypass(sp, c, flag);
        return true;
    }

    private static final Map<String, Long> LAST_BYPASS_LOG = new HashMap<>();

    /**
     * Staff doing something in a player's claim that only their bypass allows. Logged once per player, claim and
     * flag every 10 minutes so the log doesn't fill up with every block. Entering and flying aren't logged.
     */
    private static void logBypass(ServerPlayer sp, Claim c, RankFlag flag) {
        if (c.isAdmin() || flag == RankFlag.ENTER || flag == RankFlag.FLIGHT) return;
        if (!com.tac5studios.elementsclaims.log.ActionLog.enabled(com.tac5studios.elementsclaims.log.ActionLog.Category.STAFF)) return;
        String key = sp.getUUID() + "|" + c.id() + "|" + flag.key();
        long now = System.currentTimeMillis();
        Long last = LAST_BYPASS_LOG.get(key);
        if (last != null && now - last < 10 * 60_000L) return;
        LAST_BYPASS_LOG.put(key, now);
        if (LAST_BYPASS_LOG.size() > 2000) LAST_BYPASS_LOG.values().removeIf(t -> now - t > 10 * 60_000L);
        com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.STAFF, sp, c,
                "used staff bypass", flag.key() + " (logged once every 10 minutes per flag)");
    }

    /** The rank check alone, for any player id (also used for machines acting for their owner). */
    public static boolean allowedFor(UUID id, Claim c, String dim, BlockPos pos, RankFlag flag) {
        if (c.isOwner(id)) return true;
        if (c.isBanned(id)) return false;
        if (c.isAdmin() && c.blockEntry && flag == RankFlag.ENTER && !c.isTrusted(id)) return false;
        SubArea area = pos == null || !Features.on(Features.SUB_AREAS) ? null : c.subAreaAt(dim, pos);
        boolean trusted = c.isTrusted(id) || (area != null && area.members().containsKey(id));
        if (c.isPrivate() && !trusted) return false;
        Rank r = c.rankOf(id, area);
        if (r.isVisitor() && area != null && area.visitorFlags() != null && Features.on(Features.SUB_AREA_VISITOR_FLAGS)) {
            return area.visitorFlags().contains(flag);
        }
        return r.has(flag);
    }

    /** Staff who edit this claim automatically: claims.admin in admin claims, claims.bypass in player claims. */
    public static boolean staff(ServerPlayer p, Claim c) {
        return c.isAdmin() ? Perm.has(p, Perm.ADMIN) || Perm.has(p, Perm.BYPASS) : Perm.has(p, Perm.BYPASS);
    }

    /** Check and, when denied, tell the player above the hotbar (at most every 1.5 s). */
    public static boolean check(Player player, Level level, BlockPos pos, RankFlag flag) {
        if (can(player, level, pos, flag)) return true;
        deny(player, ClaimStore.at(level, pos), flag);
        return false;
    }

    public static boolean check(Player player, Claim c, RankFlag flag) {
        if (can(player, c, flag)) return true;
        deny(player, c, flag);
        return false;
    }

    public static void deny(Player player, Claim c, RankFlag flag) {
        if (!(player instanceof ServerPlayer sp) || player instanceof FakePlayer || c == null) return;
        if (!Features.on(Features.PROTECT_DENY_MESSAGES)) return;
        long now = System.currentTimeMillis();
        Long last = LAST_MESSAGE.get(sp.getUUID());
        if (last != null && now - last < 1500) return;
        LAST_MESSAGE.put(sp.getUUID(), now);
        String key = c.isBanned(sp.getUUID()) ? "protect.banned" : "protect.denied";
        sp.displayClientMessage(Msg.text(key, "action", Msg.raw("actions." + flag.key()),
                "claim", c.name(), "owner", Claiming.ownerName(sp.server, c)), true);
    }

    public static void forget(UUID id) {
        LAST_MESSAGE.remove(id);
    }
}
