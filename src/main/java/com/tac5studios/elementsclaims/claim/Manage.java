package com.tac5studios.elementsclaims.claim;

import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.log.ActionLog;
import com.tac5studios.elementsclaims.log.ActionLog.Category;
import com.tac5studios.elementsclaims.messages.Msg;
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
 *  - Every flag (rank flags, land flags, private, sub-area visitor rules) and the rank list: owner and staff only.
 *  - Manage Land: rename the claim and see its ranks.
 *  - Manage Players: promote / demote / remove players ranked BELOW you, up to the rank just below yours.
 *  - Admin Mode (set by the owner on the rank they pick): Manage Players, plus /trust, /untrust, ban and unban.
 */
public final class Manage {

    private Manage() {}

    /** Writes one action log line. Staff acting in someone else's claim get "(staff)" after the details. */
    static void log(Category cat, ServerPlayer p, Claim c, String action, String details) {
        if (p != null && !c.isOwner(p.getUUID()) && Access.staff(p, c)) details = details + " (staff)";
        ActionLog.log(cat, p, c, action, details);
    }

    private static String who(UUID id) {
        return ActionLog.name(id);
    }

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

    /** Promote, demote and remove: Manage Players or Admin Mode. */
    public static boolean canManagePlayers(ServerPlayer p, Claim c) {
        return has(p, c, RankFlag.MANAGE_PLAYERS) || adminMode(p, c);
    }

    /** Admin Mode: the owner's chosen admin rank. Trust, untrust, ban and unban on top of Manage Players. */
    public static boolean adminMode(ServerPlayer p, Claim c) {
        if (full(p, c)) return true;
        return com.tac5studios.elementsclaims.config.Features.on(com.tac5studios.elementsclaims.config.Features.MENU_DELEGATE)
                && has(p, c, RankFlag.ADMIN_MODE);
    }

    /** Ban and unban: owner, staff and Admin Mode. */
    public static boolean canBan(ServerPlayer p, Claim c) {
        return adminMode(p, c);
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

    /** Every flag is set by the owner (and staff) only. */
    public static boolean canEditRankFlags(ServerPlayer p, Claim c, Rank r) {
        return !r.isOwner() && full(p, c);
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
                log(Category.MEMBERS, p, c, "promoted", who(target) + " to " + r.name());
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
        log(Category.MEMBERS, p, c, "demoted", who(target) + " to " + r.name());
        return r;
    }

    public static boolean remove(ServerPlayer p, Claim c, UUID target) {
        if (!canActOn(p, c, target)) return false;
        boolean removed = c.members().remove(target) != null;
        if (removed) {
            ClaimStore.markDirty();
            log(Category.MEMBERS, p, c, "untrusted", who(target));
        }
        return removed;
    }

    public static boolean ban(ServerPlayer p, Claim c, UUID target) {
        if (c.isOwner(target) || target.equals(p.getUUID()) || !canBan(p, c)) return false;
        if (c.isMember(target) && !canActOn(p, c, target)) return false;
        boolean added = c.bans().add(target);
        removeEverywhere(c, target);
        ClaimStore.markDirty();
        if (added) log(Category.BANS, p, c, "banned", who(target) + " (removed from every list in the claim)");
        return added;
    }

    /**
     * Takes a player off every list in the claim: members, every sub-area, sub-area manager, removal
     * countdowns, and the record of blocks they placed (those blocks now just belong to the claim).
     * Returns true when anything changed.
     */
    public static boolean removeEverywhere(Claim c, UUID target) {
        boolean changed = c.members().remove(target) != null;
        for (SubArea a : c.subAreas()) {
            if (a.members().remove(target) != null) changed = true;
            if (a.leaving.remove(target) != null) changed = true;
            if (target.equals(a.manager)) {
                a.manager = null;
                changed = true;
            }
            if (a.placed.values().removeIf(target::equals)) changed = true;
        }
        return changed;
    }

    public static boolean unban(ServerPlayer p, Claim c, UUID target) {
        if (!canBan(p, c)) return false;
        boolean removed = c.bans().remove(target);
        if (removed) {
            ClaimStore.markDirty();
            log(Category.BANS, p, c, "unbanned", who(target));
        }
        return removed;
    }

    // ---------- ranks ----------

    /** The flags that hand out control over the claim. */
    public static boolean isManageFlag(RankFlag f) {
        return f.isManagement();
    }

    /** Flags are the owner's call only (kept as its own check for the menus). */
    public static boolean canGrant(ServerPlayer p, Claim c, RankFlag f) {
        return full(p, c);
    }

    public static boolean setRankFlag(ServerPlayer p, Claim c, Rank r, RankFlag f, boolean on) {
        if (!canEditRankFlags(p, c, r) || !canGrant(p, c, f)) return false;
        r.set(f, on);
        ClaimStore.markDirty();
        log(Category.SETTINGS, p, c, "rank flag", r.name() + ": " + f.key() + " " + (on ? "on" : "off"));
        return true;
    }

    public static boolean renameRank(ServerPlayer p, Claim c, Rank r, String name) {
        if (!canEditRankList(p, c) || name.isBlank()) return false;
        String old = r.name();
        r.rename(clip(name, ClaimsConfig.rankNameMax));
        ClaimStore.markDirty();
        log(Category.SETTINGS, p, c, "rank renamed", old + " -> " + r.name());
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
        log(Category.SETTINGS, p, c, "rank added", r.name());
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
        for (SubArea a : c.subAreas()) {
            for (Map.Entry<UUID, String> e : a.members().entrySet()) {
                if (e.getValue().equals(r.id())) e.setValue(to.id());
            }
        }
        ClaimStore.markDirty();
        log(Category.SETTINGS, p, c, "rank deleted", r.name() + " (its players moved to " + to.name() + ")");
        return true;
    }

    /**
     * Points any member or sub-area that uses a rank that no longer exists at the lowest trusted rank.
     * Run once when the claims load, to clean up data saved before deleteRank handled sub-areas.
     * Returns true when something was changed.
     */
    public static boolean fixMissingRanks(Claim c) {
        boolean changed = false;
        String fallback = c.lowestTrusted().id();
        for (Map.Entry<UUID, String> e : c.members().entrySet()) {
            if (c.rank(e.getValue()) == null) {
                e.setValue(fallback);
                changed = true;
            }
        }
        for (SubArea a : c.subAreas()) {
            for (Map.Entry<UUID, String> e : a.members().entrySet()) {
                if (c.rank(e.getValue()) == null) {
                    e.setValue(fallback);
                    changed = true;
                }
            }
        }
        // Banned players are never on any list (older saves could still have them in sub-areas).
        for (UUID banned : new java.util.ArrayList<>(c.bans())) {
            if (removeEverywhere(c, banned)) changed = true;
        }
        return changed;
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
        log(Category.SETTINGS, p, c, "rank moved", r.name() + " to position " + newIndex);
        return true;
    }

    // ---------- land ----------

    public static boolean setLandFlag(ServerPlayer p, Claim c, LandFlag f, boolean on) {
        if (!full(p, c)) return false;
        c.setFlag(f, on);
        ClaimStore.markDirty();
        log(Category.SETTINGS, p, c, "land flag", f.key() + " " + (on ? "on" : "off"));
        return true;
    }

    public static boolean setPrivate(ServerPlayer p, Claim c, boolean on) {
        if (!full(p, c)) return false;
        c.setPrivate(on);
        ClaimStore.markDirty();
        log(Category.SETTINGS, p, c, "private", on ? "on" : "off");
        return true;
    }

    public static boolean rename(ServerPlayer p, Claim c, String name) {
        String clean = clip(name, ClaimsConfig.claimNameMax);
        if (!canManageLand(p, c) || clean.isBlank()) return false;
        // Property names are used in commands, so one owner can't have two with the same name.
        if (c.owner() != null) {
            Claim same = Claiming.propertyNamed(c.owner(), clean);
            if (same != null && same != c) {
                Msg.send(p, "menu.name_taken", "claim", clean);
                return false;
            }
        }
        String old = c.name();
        c.rename(clean);
        ClaimStore.markDirty();
        log(Category.CLAIMING, p, c, "renamed", old + " -> " + clean);
        return true;
    }

    /** Trims a name, strips & and § colour codes and cuts it to the max length. */
    public static String clip(String s, int max) {
        s = s.trim().replace('&', ' ').replace('§', ' ');
        return s.length() > max ? s.substring(0, max) : s;
    }
}
