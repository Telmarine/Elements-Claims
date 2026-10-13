package com.tac5studios.elementsclaims.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.LandFlag;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.menu.LandConfigMenu;
import com.tac5studios.elementsclaims.menu.MyClaimsMenu;
import com.tac5studios.elementsclaims.claim.Rank;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.deed.Deed;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.perms.Perm;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.StringJoiner;

/**
 * /claim            Land Config for the claim you're in (or its info), or your claim list
 * /claim deed       a new Claim Deed if you lost yours
 * /claims info      staff: owner, ranks and flags of the claim you're in
 * /claims reload    staff: reload every config file
 * /claim deed admin staff: the Admin Deed
 * /claims detected  staff: machines found by auto-detect (set each to player / allow / deny)
 * /claims import <mod> [confirm]  staff: import claims from OPAC, FTB Chunks, Flan or Cadmus (preview first)
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ClaimCommands {

    private ClaimCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();

        var claim = Commands.literal("claim")
                .requires(s -> Perm.has(s, Perm.USE))
                .executes(ClaimCommands::claim);
        if (Features.on(Features.CLAIM_DEED)) {
            var deed = Commands.literal("deed").executes(ClaimCommands::deed);
            if (Features.on(Features.ADMIN_CLAIMS)) {
                deed.then(Commands.literal("admin").requires(s -> Perm.has(s, Perm.ADMIN)).executes(ClaimCommands::adminDeed));
            }
            claim.then(deed);
        }
        d.register(claim);

        if (Features.on(Features.ADMIN)) {
            var claims = Commands.literal("claims")
                    .requires(s -> Perm.has(s, Perm.INFO) || Perm.has(s, Perm.RELOAD) || Perm.has(s, Perm.DETECTED) || Perm.has(s, Perm.IMPORT));
            if (Features.on(Features.DETECT)) {
                claims.then(Commands.literal("detected").requires(s -> Perm.has(s, Perm.DETECTED))
                        .executes(StaffCommands::detected)
                        .then(Commands.literal("set")
                                .then(Commands.argument("machine", StringArgumentType.string())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                com.tac5studios.elementsclaims.detect.Machines.known().keySet().stream().map(k -> "\"" + k + "\""), b))
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"player", "allow", "deny"}, b))
                                                .executes(StaffCommands::detectedSet)))));
            }
            if (Features.on(Features.IMPORT)) {
                claims.then(Commands.literal("import").requires(s -> Perm.has(s, Perm.IMPORT))
                        .then(Commands.argument("mod", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(com.tac5studios.elementsclaims.importer.Importers.IDS, b))
                                .executes(ctx -> StaffCommands.importMod(ctx, false))
                                .then(Commands.literal("confirm").executes(ctx -> StaffCommands.importMod(ctx, true)))));
            }
            if (Features.on(Features.ADMIN_INFO)) {
                claims.then(Commands.literal("info").requires(s -> Perm.has(s, Perm.INFO)).executes(ClaimCommands::info));
            }
            if (Features.on(Features.ADMIN_RELOAD)) {
                claims.then(Commands.literal("reload").requires(s -> Perm.has(s, Perm.RELOAD)).executes(ClaimCommands::reload));
            }
            d.register(claims);
        }
    }

    private static int claim(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            Msg.fail(ctx.getSource(), "general.players_only");
            return 0;
        }
        Claim here = ClaimStore.at(p.level(), p.blockPosition());
        if (here != null) {
            if (Manage.canOpenMenu(p, here) && LandConfigMenu.open(p, here)) return 1;
            Rank r = here.rankOf(p.getUUID());
            Msg.send(p, "claim.here", "claim", here.name(), "owner", Claiming.ownerName(p.server, here),
                    "chunks", here.chunks().size(), "rank", r.name());
            return 1;
        }
        if (Features.on(Features.MENU)) {
            new MyClaimsMenu().open(p);
            return 1;
        }
        List<Claim> mine = ClaimStore.ownedBy(p.getUUID());
        int used = ClaimStore.chunksUsed(p.getUUID());
        Msg.send(p, "claim.list_header", "count", mine.size(), "used", used, "max", Claiming.allowance(p));
        if (mine.isEmpty()) Msg.send(p, "claim.list_empty");
        for (Claim c : mine) {
            ChunkPos mid = c.center();
            Msg.send(p, "claim.list_line", "claim", c.name(), "chunks", c.chunks().size(),
                    "dimension", c.dimension(), "x", mid.getMiddleBlockX(), "z", mid.getMiddleBlockZ(),
                    "members", c.members().size());
        }
        return 1;
    }

    private static int deed(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            Msg.fail(ctx.getSource(), "general.players_only");
            return 0;
        }
        if (Deed.hasDeed(p)) {
            Msg.send(p, "deed.already_have");
            return 0;
        }
        long now = System.currentTimeMillis();
        long wait = ClaimsConfig.deedCooldownMinutes * 60_000L;
        Long last = DEED_USED.get(p.getUUID());
        if (wait > 0 && last != null && now - last < wait && !Perm.has(p, Perm.ADMIN)) {
            long left = (wait - (now - last) + 59_999) / 60_000;
            Msg.send(p, "deed.cooldown", "minutes", left);
            return 0;
        }
        Deed.give(p);
        DEED_USED.put(p.getUUID(), now);
        Msg.send(p, "deed.given");
        return 1;
    }

    /** Last time each player got a Deed from /claim deed. Resets on restart. */
    private static final Map<UUID, Long> DEED_USED = new HashMap<>();

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            Msg.fail(ctx.getSource(), "general.players_only");
            return 0;
        }
        Claim c = ClaimStore.at(p.level(), p.blockPosition());
        if (c == null) {
            Msg.send(p, "claim.not_claimed");
            return 0;
        }
        Msg.send(p, "info.header", "claim", c.name(), "owner", Claiming.ownerName(p.server, c),
                "type", Msg.raw(c.isAdmin() ? "info.type_admin" : "info.type_player"),
                "chunks", c.chunks().size(), "private", c.isPrivate() ? "yes" : "no");
        for (Rank r : c.ranks()) {
            StringJoiner members = new StringJoiner(", ");
            c.members().forEach((u, id) -> {
                if (id.equals(r.id())) members.add(Claiming.name(p.server, u));
            });
            StringJoiner flags = new StringJoiner(", ");
            for (RankFlag f : RankFlag.values()) if (r.has(f)) flags.add(f.key());
            Msg.send(p, "info.rank", "rank", r.name(), "members", members.length() == 0 ? "-" : members.toString(),
                    "flags", r.isOwner() ? "all" : (flags.length() == 0 ? "-" : flags.toString()));
        }
        StringJoiner on = new StringJoiner(", ");
        for (LandFlag f : LandFlag.values()) if (c.flag(f)) on.add(f.key());
        Msg.send(p, "info.land_flags", "flags", on.length() == 0 ? "-" : on.toString());
        if (!c.bans().isEmpty()) {
            StringJoiner bans = new StringJoiner(", ");
            for (var u : c.bans()) bans.add(Claiming.name(p.server, u));
            Msg.send(p, "info.bans", "players", bans.toString());
        }
        return 1;
    }

    private static int adminDeed(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            Msg.fail(ctx.getSource(), "general.players_only");
            return 0;
        }
        var stack = Deed.createAdmin();
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        Msg.send(p, "deed.admin_given");
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ClaimsConfig.load();
        Msg.load();
        com.tac5studios.elementsclaims.limits.LagLimits.load();
        com.tac5studios.elementsclaims.limits.EntityLimits.load();
        com.tac5studios.elementsclaims.market.Market.reload();
        com.tac5studios.elementsclaims.market.Expiry.reload();
        com.tac5studios.elementsclaims.detect.AutoSort.reload();
        com.tac5studios.elementsclaims.integration.Integrations.reload();
        Msg.sendLogged(ctx.getSource(), "admin.reloaded");
        return 1;
    }
}
