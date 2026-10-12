package com.tac5studios.elementsclaims.protect;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.LandFlag;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.claim.Claiming;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Checked a few times a second for every player:
 *   - Enter: bans, private claims, the Enter rank flag, admin "block entry" → put back where they last stood.
 *   - Admin "block exit" → can't walk out (staff can).
 *   - Flight: stop flying / gliding where the rank can't fly.
 *   - Enter/leave messages (Titles land flag + personal setting) and admin enter/leave commands.
 *   - Admin "no hunger".
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Movement {

    private record Spot(ResourceKey<Level> dim, Vec3 pos, float yRot, float xRot) {}

    private static final Map<UUID, Spot> LAST_ALLOWED = new HashMap<>();
    /** The claim each player was in at the last check (null = wilderness). */
    private static final Map<UUID, UUID> LAST_CLAIM = new HashMap<>();

    private Movement() {}

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if ((p.tickCount + p.getId()) % 5 != 0 || p.isSpectator()) return;

        BlockPos pos = p.blockPosition();
        Claim c = ClaimStore.at(p.level(), pos);
        UUID lastId = LAST_CLAIM.get(p.getUUID());
        Claim last = lastId == null ? null : ClaimStore.byId(lastId);

        if (Features.on(Features.PROTECTION)) {
            if (c != null && !Access.can(p, c, pos, RankFlag.ENTER)) {
                Access.deny(p, c, RankFlag.ENTER);
                pushOut(p);
                return;
            }
            // Admin claim with "block exit": walking out puts them back inside.
            if (last != null && last != c && last.isAdmin() && last.blockExit && Features.on(Features.ADMIN_BLOCK_ENTRY_EXIT)
                    && !Access.staff(p, last) && last.dimension().equals(ClaimStore.dim(p.level()))) {
                Msg.send(p, "admin.no_exit", "claim", last.name());
                pushOut(p);
                return;
            }
        }
        LAST_ALLOWED.put(p.getUUID(), new Spot(p.level().dimension(), p.position(), p.getYRot(), p.getXRot()));

        if (c != last) {
            LAST_CLAIM.put(p.getUUID(), c == null ? null : c.id());
            crossed(p, last, c);
        }

        if (c != null && (p.isFallFlying() || (p.getAbilities().flying && !p.isCreative()))
                && !Access.can(p, c, pos, RankFlag.FLIGHT)) {
            Access.deny(p, c, RankFlag.FLIGHT);
            if (p.isFallFlying()) p.stopFallFlying();
            if (p.getAbilities().flying && !p.isCreative()) {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }
        }

        if (c != null && c.isAdmin() && c.noHunger && Features.on(Features.ADMIN_NO_HUNGER)) {
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(Math.max(p.getFoodData().getSaturationLevel(), 5f));
        }
    }

    /** The player walked from one claim (or the wilderness) into another. */
    private static void crossed(ServerPlayer p, Claim from, Claim to) {
        if (from != null && from.isAdmin() && Features.on(Features.ADMIN_COMMANDS)) run(p, from.leaveCommands);
        if (to != null && to.isAdmin() && Features.on(Features.ADMIN_COMMANDS)) run(p, to.enterCommands);
        if (!Features.landFlagOn(LandFlag.TITLES) || !ClaimStore.player(p.getUUID()).titles) return;
        if (to != null && to.flag(LandFlag.TITLES)) {
            show(p, Msg.text("titles.enter", "claim", to.name(), "owner", Claiming.ownerName(p.server, to)),
                    Msg.text("titles.enter_sub", "claim", to.name(), "owner", Claiming.ownerName(p.server, to)));
        } else if (from != null && from.flag(LandFlag.TITLES)) {
            show(p, Msg.text("titles.leave", "claim", from.name(), "owner", Claiming.ownerName(p.server, from)),
                    Msg.text("titles.leave_sub", "claim", from.name(), "owner", Claiming.ownerName(p.server, from)));
        }
    }

    private static void show(ServerPlayer p, Component title, Component sub) {
        if (Features.on(Features.TITLES_ACTIONBAR)) {
            p.displayClientMessage(title, true);
            return;
        }
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private static void run(ServerPlayer p, java.util.List<String> commands) {
        for (String cmd : commands) {
            String c = cmd.replace("{player}", p.getGameProfile().getName()).replace("{uuid}", p.getStringUUID());
            if (c.startsWith("/")) c = c.substring(1);
            try {
                p.server.getCommands().performPrefixedCommand(p.server.createCommandSourceStack().withSuppressedOutput(), c);
            } catch (RuntimeException ex) {
                ElementsClaims.LOGGER.warn("[Claims] Admin claim command failed: {} ({})", c, ex.toString());
            }
        }
    }

    private static void pushOut(ServerPlayer p) {
        if (p.isPassenger()) p.stopRiding();
        Spot s = LAST_ALLOWED.get(p.getUUID());
        if (s != null && s.dim().equals(p.level().dimension())) {
            p.teleportTo((ServerLevel) p.level(), s.pos().x, s.pos().y, s.pos().z, s.yRot(), s.xRot());
            return;
        }
        // Joined (or teleported) straight into a claim they can't enter: send them to world spawn.
        ServerLevel level = p.server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        p.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, p.getYRot(), p.getXRot());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST_ALLOWED.remove(e.getEntity().getUUID());
        LAST_CLAIM.remove(e.getEntity().getUUID());
        Access.forget(e.getEntity().getUUID());
    }
}
