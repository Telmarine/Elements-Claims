package com.tac5studios.elementsclaims.api;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.protect.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Public Elements: Claims API — "may this player do X here?" for other mods and the Elements family.
 * NeoForge has no shared protection API, so mods call this (by reflection if they don't want a hard dependency).
 *
 *   ClaimsAPI.canBuild(player, level, pos)
 *   ClaimsAPI.can(player, level, pos, "containers")      // any rank flag key: build, containers, doors, ...
 *   ClaimsAPI.claimName(level, pos)                      // Optional name of the claim there
 *
 * Every answer is "yes" when the API is switched off in features.toml ([integrations] api).
 */
public final class ClaimsAPI {

    private ClaimsAPI() {}

    private static boolean on() {
        return Features.on(Features.API);
    }

    /** True when the player may do what the flag covers at that position. Unknown flag keys count as Build. */
    public static boolean can(Player player, Level level, BlockPos pos, String flag) {
        if (!on()) return true;
        RankFlag f = RankFlag.byKey(flag.toLowerCase(Locale.ROOT));
        return Access.can(player, level, pos, f == null ? RankFlag.BUILD : f);
    }

    public static boolean canBuild(Player player, Level level, BlockPos pos) {
        return can(player, level, pos, "build");
    }

    public static boolean canOpenContainers(Player player, Level level, BlockPos pos) {
        return can(player, level, pos, "containers");
    }

    public static boolean canInteract(Player player, Level level, BlockPos pos) {
        return can(player, level, pos, "interact");
    }

    public static boolean canUseShops(Player player, Level level, BlockPos pos) {
        return can(player, level, pos, "shops_use");
    }

    public static boolean canCreateShops(Player player, Level level, BlockPos pos) {
        return can(player, level, pos, "shops_create");
    }

    public static boolean isClaimed(Level level, BlockPos pos) {
        return on() && ClaimStore.at(level, pos) != null;
    }

    public static boolean isAdminClaim(Level level, BlockPos pos) {
        Claim c = on() ? ClaimStore.at(level, pos) : null;
        return c != null && c.isAdmin();
    }

    public static Optional<String> claimName(Level level, BlockPos pos) {
        Claim c = on() ? ClaimStore.at(level, pos) : null;
        return c == null ? Optional.empty() : Optional.of(c.name());
    }

    /** Owner of the claim there; empty for unclaimed land and admin claims. */
    public static Optional<UUID> owner(Level level, BlockPos pos) {
        Claim c = on() ? ClaimStore.at(level, pos) : null;
        return c == null || c.owner() == null ? Optional.empty() : Optional.of(c.owner());
    }

    /** Owner's name ("the Server" for admin claims); empty for unclaimed land. */
    public static Optional<String> ownerName(Level level, BlockPos pos) {
        Claim c = on() ? ClaimStore.at(level, pos) : null;
        if (c == null || level.getServer() == null) return Optional.empty();
        return Optional.of(Claiming.ownerName(level.getServer(), c));
    }

    /** Owner or trusted (any rank above Visitor) in the claim there. False on unclaimed land. */
    public static boolean isTrusted(UUID player, Level level, BlockPos pos) {
        Claim c = on() ? ClaimStore.at(level, pos) : null;
        return c != null && c.isTrusted(player);
    }

    // ---------- action log ----------

    /**
     * Get every action Claims logs (claims made, players trusted, flags changed, bans, transfers...) as it happens,
     * on the server thread. Only actions whose category is switched on in features.toml [log] are passed on.
     */
    public static void onAction(java.util.function.Consumer<com.tac5studios.elementsclaims.log.ActionLog.Entry> listener) {
        com.tac5studios.elementsclaims.log.ActionLog.listen(listener);
    }

    /**
     * Write your own line to the Claims action log, e.g. when your mod changes something in a claim.
     * Goes under the "staff" category when {@code staffAction} is true, otherwise "settings".
     */
    public static void logAction(String actor, Level level, BlockPos pos, String action, String details, boolean staffAction) {
        Claim c = on() ? ClaimStore.at(level, pos) : null;
        com.tac5studios.elementsclaims.log.ActionLog.log(staffAction ? com.tac5studios.elementsclaims.log.ActionLog.Category.STAFF
                : com.tac5studios.elementsclaims.log.ActionLog.Category.SETTINGS, actor, null, c, action, details);
    }
}
