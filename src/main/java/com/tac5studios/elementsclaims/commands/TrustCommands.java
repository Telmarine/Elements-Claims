package com.tac5studios.elementsclaims.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.claim.Rank;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.perms.Perm;
import com.tac5studios.elementsclaims.subarea.SubAreas;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The only claim commands for players besides /claim (SPEC section 1):
 *   /trust <player>     add to the lowest trusted rank
 *   /untrust <player>   remove
 *   /trustlist          who is trusted, by rank
 * Standing in one of your claims = that claim. Anywhere else = all your claims.
 *
 * Sub-areas (stand in the claim the sub-area is in):
 *   /trust <player> <subarea> <rank>   give the player that rank inside the sub-area only
 *   /untrust <player> <subarea>        take them out of the sub-area (their claim rank stays)
 * Names with spaces go in quotes, e.g. /trust Steve "Shop 1" "Shop Owner".
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class TrustCommands {

    private TrustCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        if (!Features.on(Features.TRUST)) return;
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        boolean sub = Features.on(Features.SUB_AREAS) && Features.on(Features.SUB_AREA_TRUST);
        var trustPlayer = Commands.argument("player", GameProfileArgument.gameProfile())
                .executes(TrustCommands::trust);
        if (sub) {
            trustPlayer.then(Commands.argument("subarea", StringArgumentType.string())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(subAreaNames(ctx.getSource()), b))
                    .then(Commands.argument("rank", StringArgumentType.string())
                            .suggests((ctx, b) -> SharedSuggestionProvider.suggest(rankNames(ctx.getSource(), StringArgumentType.getString(ctx, "subarea")), b))
                            .executes(TrustCommands::trustSub)));
        }
        d.register(Commands.literal("trust")
                .requires(s -> Perm.has(s, Perm.USE))
                .then(trustPlayer));
        var untrustPlayer = Commands.argument("player", GameProfileArgument.gameProfile())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(memberNames(ctx.getSource()), b))
                .executes(TrustCommands::untrust);
        if (sub) {
            untrustPlayer.then(Commands.argument("subarea", StringArgumentType.string())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(subAreaNames(ctx.getSource()), b))
                    .executes(TrustCommands::untrustSub));
        }
        d.register(Commands.literal("untrust")
                .requires(s -> Perm.has(s, Perm.USE))
                .then(untrustPlayer));
        d.register(Commands.literal("trustlist")
                .requires(s -> Perm.has(s, Perm.USE))
                .executes(TrustCommands::list));
    }

    /**
     * The claims a trust command applies to: the one you stand in (if it's yours, or your rank there has
     * Admin Mode), else all of yours.
     */
    private static List<Claim> targets(ServerPlayer p) {
        Claim here = ClaimStore.at(p.level(), p.blockPosition());
        if (here != null && (here.isOwner(p.getUUID()) || Manage.adminMode(p, here))) return List.of(here);
        return ClaimStore.ownedBy(p.getUUID());
    }

    private static int trust(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            Msg.fail(ctx.getSource(), "general.players_only");
            return 0;
        }
        List<Claim> claims = targets(p);
        if (claims.isEmpty()) {
            Msg.send(p, "trust.no_claims");
            return 0;
        }
        int done = 0;
        for (GameProfile gp : GameProfileArgument.getGameProfiles(ctx, "player")) {
            UUID id = gp.getId();
            if (id.equals(p.getUUID())) {
                Msg.send(p, "trust.self");
                continue;
            }
            if (!Features.on(Features.TRUST_OFFLINE) && p.server.getPlayerList().getPlayer(id) == null) {
                Msg.send(p, "trust.must_be_online", "player", gp.getName());
                continue;
            }
            int added = 0;
            for (Claim c : claims) {
                if (c.isMember(id)) continue;
                boolean wasBanned = c.bans().remove(id);
                c.members().put(id, c.lowestTrusted().id());
                added++;
                com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.MEMBERS, p, c, "trusted", gp.getName() + " as " + c.lowestTrusted().name() + (wasBanned ? " (was banned, ban lifted)" : ""));
            }
            if (added == 0) {
                Msg.send(p, "trust.already", "player", gp.getName());
                continue;
            }
            ClaimStore.markDirty();
            done++;
            String where = claims.size() == 1 ? claims.get(0).name() : Msg.raw("trust.all_claims", "count", claims.size());
            Msg.send(p, "trust.added", "player", gp.getName(), "claim", where, "rank", claims.get(0).lowestTrusted().name());
            ServerPlayer target = p.server.getPlayerList().getPlayer(id);
            if (target != null && ClaimStore.player(id).trustMessages) Msg.send(target, "trust.added_target", "player", p.getGameProfile().getName(), "claim", where);
        }
        return done;
    }

    private static int untrust(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            Msg.fail(ctx.getSource(), "general.players_only");
            return 0;
        }
        List<Claim> claims = targets(p);
        if (claims.isEmpty()) {
            Msg.send(p, "trust.no_claims");
            return 0;
        }
        int done = 0;
        for (GameProfile gp : GameProfileArgument.getGameProfiles(ctx, "player")) {
            int removed = 0;
            for (Claim c : claims) {
                // Admin Mode can only remove players ranked below them.
                if (!c.isOwner(p.getUUID()) && c.isMember(gp.getId()) && !Manage.canActOn(p, c, gp.getId())) {
                    Msg.send(p, "menu.cant_remove", "player", gp.getName());
                    continue;
                }
                if (c.members().remove(gp.getId()) == null) continue;
                removed++;
                com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.MEMBERS, p, c, "untrusted", gp.getName());
            }
            if (removed == 0) {
                Msg.send(p, "trust.not_trusted", "player", gp.getName());
                continue;
            }
            ClaimStore.markDirty();
            done++;
            String where = claims.size() == 1 ? claims.get(0).name() : Msg.raw("trust.all_claims", "count", claims.size());
            Msg.send(p, "trust.removed", "player", gp.getName(), "claim", where);
        }
        return done;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            Msg.fail(ctx.getSource(), "general.players_only");
            return 0;
        }
        List<Claim> claims = targets(p);
        if (claims.isEmpty()) {
            Msg.send(p, "trust.no_claims");
            return 0;
        }
        for (Claim c : claims) {
            Msg.send(p, "trust.list_header", "claim", c.name());
            boolean any = false;
            for (Rank r : c.ranks()) {
                if (r.isOwner() || r.isVisitor()) continue;
                List<String> names = new ArrayList<>();
                c.members().forEach((u, id) -> {
                    if (id.equals(r.id())) names.add(Claiming.name(p.server, u));
                });
                if (names.isEmpty()) continue;
                any = true;
                Msg.send(p, "trust.list_line", "rank", r.name(), "players", String.join(", ", names));
            }
            if (!any) Msg.send(p, "trust.list_empty");
        }
        return 1;
    }

    // ---------- sub-areas ----------

    /** The claim the player stands in, if they can manage its sub-areas. Sends the reason when not. */
    private static Claim subAreaClaim(ServerPlayer p) {
        Claim c = ClaimStore.at(p.level(), p.blockPosition());
        if (c == null) {
            Msg.send(p, "trust.sub_stand_in_claim");
            return null;
        }
        // The owner (or staff), or a player the owner made manager of one of the sub-areas here.
        boolean manager = false;
        for (SubArea a : c.subAreas()) if (SubAreas.canManageMembers(p, c, a)) manager = true;
        if (!manager) {
            Msg.send(p, "trust.sub_no_permission", "claim", c.name());
            return null;
        }
        return c;
    }

    /** Managers (not the owner) can't touch themselves or anyone at or above their own rank. */
    private static boolean managerMay(ServerPlayer p, Claim c, SubArea a, UUID target) {
        if (Manage.full(p, c)) return true;
        if (target.equals(p.getUUID())) return false;
        String rid = a.members().get(target);
        Rank r = rid == null ? null : c.rank(rid);
        return r == null || SubAreas.assignable(p, c, a).contains(r);
    }

    private static SubArea findSubArea(Claim c, String name) {
        for (SubArea a : c.subAreas()) if (a.name().equalsIgnoreCase(name)) return a;
        return null;
    }

    private static int trustSub(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            Msg.fail(ctx.getSource(), "general.players_only");
            return 0;
        }
        Claim c = subAreaClaim(p);
        if (c == null) return 0;
        String areaName = StringArgumentType.getString(ctx, "subarea");
        SubArea area = findSubArea(c, areaName);
        if (area == null) {
            Msg.send(p, "trust.sub_unknown", "area", areaName, "claim", c.name());
            return 0;
        }
        if (!SubAreas.canManageMembers(p, c, area)) {
            Msg.send(p, "trust.sub_no_permission", "claim", c.name());
            return 0;
        }
        String rankName = StringArgumentType.getString(ctx, "rank");
        Rank rank = null;
        for (Rank r : SubAreas.assignable(p, c, area)) if (r.name().equalsIgnoreCase(rankName)) rank = r;
        if (rank == null) {
            Msg.send(p, "trust.sub_unknown_rank", "rank", rankName);
            return 0;
        }
        int done = 0;
        for (GameProfile gp : GameProfileArgument.getGameProfiles(ctx, "player")) {
            UUID id = gp.getId();
            if (id.equals(p.getUUID()) || c.isOwner(id)) {
                Msg.send(p, "trust.sub_self");
                continue;
            }
            if (!Features.on(Features.TRUST_OFFLINE) && p.server.getPlayerList().getPlayer(id) == null) {
                Msg.send(p, "trust.must_be_online", "player", gp.getName());
                continue;
            }
            if (c.isBanned(id)) {
                Msg.send(p, "trust.sub_banned", "player", gp.getName(), "claim", c.name());
                continue;
            }
            if (!managerMay(p, c, area, id)) {
                Msg.send(p, "subarea.manager_cant", "player", gp.getName());
                continue;
            }
            String before = area.members().get(id);
            area.members().put(id, rank.id());
            done++;
            com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.SUB_AREAS, p, c, before == null ? "sub-area trusted" : "sub-area rank changed",
                    gp.getName() + " in " + area.name() + " as " + rank.name());
            Msg.send(p, "trust.sub_added", "player", gp.getName(), "area", area.name(), "rank", rank.name());
            ServerPlayer target = p.server.getPlayerList().getPlayer(id);
            if (target != null && ClaimStore.player(id).trustMessages) {
                Msg.send(target, "trust.sub_added_target", "player", p.getGameProfile().getName(), "area", area.name(), "claim", c.name(), "rank", rank.name());
            }
        }
        if (done > 0) ClaimStore.markDirty();
        return done;
    }

    private static int untrustSub(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            Msg.fail(ctx.getSource(), "general.players_only");
            return 0;
        }
        Claim c = subAreaClaim(p);
        if (c == null) return 0;
        String areaName = StringArgumentType.getString(ctx, "subarea");
        SubArea area = findSubArea(c, areaName);
        if (area == null) {
            Msg.send(p, "trust.sub_unknown", "area", areaName, "claim", c.name());
            return 0;
        }
        if (!SubAreas.canManageMembers(p, c, area)) {
            Msg.send(p, "trust.sub_no_permission", "claim", c.name());
            return 0;
        }
        int done = 0;
        for (GameProfile gp : GameProfileArgument.getGameProfiles(ctx, "player")) {
            if (!area.members().containsKey(gp.getId())) {
                Msg.send(p, "trust.sub_not_member", "player", gp.getName(), "area", area.name());
                continue;
            }
            if (!managerMay(p, c, area, gp.getId())) {
                Msg.send(p, "subarea.manager_cant", "player", gp.getName());
                continue;
            }
            // Stops (and starts the countdown) when blocks they placed are still in the sub-area.
            if (!com.tac5studios.elementsclaims.subarea.SubAreaLeave.remove(p, c, area, gp.getId())) continue;
            done++;
            Msg.send(p, "trust.sub_removed", "player", gp.getName(), "area", area.name());
        }
        if (done > 0) ClaimStore.markDirty();
        return done;
    }

    /** Names for tab-complete, quoted when they have a space. */
    private static String quote(String s) {
        return s.matches("[A-Za-z0-9_.+-]+") ? s : "\"" + s.replace("\"", "") + "\"";
    }

    private static Collection<String> subAreaNames(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        List<String> out = new ArrayList<>();
        if (p == null) return out;
        Claim c = ClaimStore.at(p.level(), p.blockPosition());
        if (c == null) return out;
        for (SubArea a : c.subAreas()) if (SubAreas.canManageMembers(p, c, a)) out.add(quote(a.name()));
        return out;
    }

    private static Collection<String> rankNames(CommandSourceStack source, String areaName) {
        ServerPlayer p = source.getPlayer();
        List<String> out = new ArrayList<>();
        if (p == null) return out;
        Claim c = ClaimStore.at(p.level(), p.blockPosition());
        if (c == null) return out;
        SubArea a = findSubArea(c, areaName);
        if (a == null) return out;
        for (Rank r : SubAreas.assignable(p, c, a)) out.add(quote(r.name()));
        return out;
    }

    private static Collection<String> memberNames(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        Set<String> out = new LinkedHashSet<>();
        if (p == null) return out;
        for (Claim c : targets(p)) for (UUID u : c.members().keySet()) out.add(Claiming.name(p.server, u));
        return out;
    }
}
