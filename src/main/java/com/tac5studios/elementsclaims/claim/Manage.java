package com.tac5studios.elementsclaims.claim;

import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.protect.Access;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who may change what in a claim, and the changes themselves (every change saves).
 *
 * Rules (SPEC sections 1-3):
 *  - Owner and staff (claims.bypass / claims.admin for admin claims) may do everything.
 *  - Manage Land: rename, land flags, private, and the flags of ranks BELOW your own.
 *  - Manage Players: promote / demote / remove / ban players ranked BELOW you, up to the rank just below yours.
 *  - Rank names, order, adding and deleting ranks: owner and staff only.
 */
public final class Manage {

    private Manage() {}

    // ---------- rights ----------

    public static boolean full(ServerPlayer p, Claim c) {
        return c.isOwner(p.getUUID()) || Access.staff(p, c);
    }

    private static boolean has(ServerPlayer p, Claim c, RankFlag f) {
        if (full(p, c)) return true;
        UUID id = p.getUUID();
        return !c.isBanned(id) && c.isMember(id) && c.rankOf(id).has(f);
    }

    public static boolean canManageLand(ServerPlayer p, Claim c) {
        return has(p, c, RankFlag.MANAGE_LAND);
    }

    public static boolean canManagePlayers(ServerPlayer p, Claim c) {
        return has(p, c, RankFlag.MANAGE_PLAYERS);
    }

    public static boolean canOpenMenu(ServerPlayer p, Claim c) {
        return canManageLand(p, c) || canManagePlayers(p, c) || has(p, c, RankFlag.SUB_AREAS);
    }

    public static boolean canEditRankList(ServerPlayer p, Claim c) {
        return full(p, c);
    }

    /** Position of the player's rank (0 = Owner). Staff count as above everyone but the owner. */
    private static int level(ServerPlayer p, Claim c) {
        if (c.isOwner(p.getUUID())) return 0;
        if (Access.staff(p, c)) return 0;
        return c.ranks().indexOf(c.rankOf(p.getUUID()));
    }

    public static boolean canEditRankFlags(ServerPlayer p, Claim c, Rank r) {
        if (r.isOwner()) return false;
        if (full(p, c)) return true;
        return canManageLand(p, c) && c.ranks().indexOf(r) > level(p, c);
    }

    /** May the player change this member's rank, remove or ban them? */
    public static boolean canActOn(ServerPlayer p, Claim c, UUID target) {
        if (c.isOwner(target) || target.equals(p.getUUID())) return false;
        if (!canManagePlayers(p, c)) return false;
        if (full(p, c)) return true;
        return c.ranks().indexOf(c.rankOf(target)) > level(p, c);
    }

    /** Ranks this player may give to someone: trusted ranks below their own (owner/staff: all trusted ranks). */
    public static List<Rank> assignable(ServerPlayer p, Claim c) {
        List<Rank> out = new ArrayList<>();
        int lvl = level(p, c);
        for (int i = 1; i < c.ranks().size() - 1; i++) {
            if (full(p, c) || i > lvl) out.add(c.ranks().get(i));
        }
        return out;
    }

    // ---------- members ----------

    /** One step up (towards Owner). Returns the new rank or null when not possible. */
    public static Rank promote(ServerPlayer p, Claim c, UUID target) {
        if (!canActOn(p, c, target) || !c.isMember(target)) return null;
        List<Rank> allowed = assignable(p, c);
        int idx = c.ranks().indexOf(c.rankOf(target));
        for (int i = idx - 1; i >= 1; i--) {
            Rank r = c.ranks().get(i);
            if (allowed.contains(r)) {
                c.members().put(target, r.id());
                ClaimStore.markDirty();
                return r;
            }
        }
        return null;
    }

    /** One step down (towards Visitor, but never to Visitor — use remove). */
    public static Rank demote(ServerPlayer p, Claim c, UUID target) {
        if (!canActOn(p, c, target) || !c.isMember(target)) return null;
        int idx = c.ranks().indexOf(c.rankOf(target));
        if (idx + 1 >= c.ranks().size() - 1) return null;
        Rank r = c.ranks().get(idx + 1);
        c.members().put(target, r.id());
        ClaimStore.markDirty();
        return r;
    }

    public static boolean remove(ServerPlayer p, Claim c, UUID target) {
        if (!canActOn(p, c, target)) return false;
        boolean removed = c.members().remove(target) != null;
        if (removed) ClaimStore.markDirty();
        return removed;
    }

    public static boolean ban(ServerPlayer p, Claim c, UUID target) {
        if (c.isOwner(target) || target.equals(p.getUUID()) || !canManagePlayers(p, c)) return false;
        if (c.isMember(target) && !canActOn(p, c, target)) return false;
        c.members().remove(target);
        boolean added = c.bans().add(target);
        ClaimStore.markDirty();
        return added;
    }

    public static boolean unban(ServerPlayer p, Claim c, UUID target) {
        if (!canManagePlayers(p, c)) return false;
        boolean removed = c.bans().remove(target);
        if (removed) ClaimStore.markDirty();
        return removed;
    }

    // ---------- ranks ----------

    public static boolean setRankFlag(ServerPlayer p, Claim c, Rank r, RankFlag f, boolean on) {
        if (!canEditRankFlags(p, c, r)) return false;
        r.set(f, on);
        ClaimStore.markDirty();
        return true;
    }

    public static boolean renameRank(ServerPlayer p, Claim c, Rank r, String name) {
        if (!canEditRankList(p, c) || name.isBlank()) return false;
        r.rename(clip(name, ClaimsConfig.rankNameMax));
        ClaimStore.markDirty();
        return true;
    }

    /** New rank just above Visitor, with Member's flags. Null when the claim is full of ranks. */
    public static Rank addRank(ServerPlayer p, Claim c, String name) {
        if (!canEditRankList(p, c) || name.isBlank() || c.ranks().size() >= ClaimsConfig.maxRanks) return null;
        String id;
        int n = 1;
        do {
            id = "rank" + n++;
        } while (c.rank(id) != null);
        Rank r = new Rank(id, clip(name, ClaimsConfig.rankNameMax), RankFlag.memberDefaults());
        c.ranks().add(c.ranks().size() - 1, r);
        ClaimStore.markDirty();
        return r;
    }

    /** Delete a rank. Its members drop to the next rank down (or the lowest trusted rank). At least one trusted rank stays. */
    public static boolean deleteRank(ServerPlayer p, Claim c, Rank r) {
        if (!canEditRankList(p, c) || r.isOwner() || r.isVisitor()) return false;
        if (c.ranks().size() <= 3) return false;
        int idx = c.ranks().indexOf(r);
        c.ranks().remove(r);
        Rank to = c.ranks().get(Math.min(idx, c.ranks().size() - 2));
        if (to.isOwner()) to = c.lowestTrusted();
        for (Map.Entry<UUID, String> e : c.members().entrySet()) {
            if (e.getValue().equals(r.id())) e.setValue(to.id());
        }
        ClaimStore.markDirty();
        return true;
    }

    /** Move a rank to a new position. Owner stays first and Visitor last. */
    public static boolean moveRank(ServerPlayer p, Claim c, Rank r, int newIndex) {
        if (!canEditRankList(p, c) || r.isOwner() || r.isVisitor()) return false;
        int max = c.ranks().size() - 2;
        newIndex = Math.max(1, Math.min(max, newIndex));
        int old = c.ranks().indexOf(r);
        if (old == newIndex || old < 0) return false;
        c.ranks().remove(old);
        c.ranks().add(newIndex, r);
        ClaimStore.markDirty();
        return true;
    }

    // ---------- land ----------

    public static boolean setLandFlag(ServerPlayer p, Claim c, LandFlag f, boolean on) {
        if (!canManageLand(p, c)) return false;
        c.setFlag(f, on);
        ClaimStore.markDirty();
        return true;
    }

    public static boolean setPrivate(ServerPlayer p, Claim c, boolean on) {
        if (!canManageLand(p, c)) return false;
        c.setPrivate(on);
        ClaimStore.markDirty();
        return true;
    }

    public static boolean rename(ServerPlayer p, Claim c, String name) {
        if (!canManageLand(p, c) || name.isBlank()) return false;
        c.rename(clip(name, ClaimsConfig.claimNameMax));
        ClaimStore.markDirty();
        return true;
    }

    private static String clip(String s, int max) {
        s = s.trim().replace('&', ' ').replace('§', ' ');
        return s.length() > max ? s.substring(0, max) : s;
    }
}
