package com.tac5studios.elementsclaims.subarea;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.log.ActionLog;
import com.tac5studios.elementsclaims.messages.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps track of who placed which blocks inside sub-areas, so a player can't be removed from a sub-area
 * while their things are still in it.
 *
 * When the owner removes someone who still has blocks there, the removal is put on hold and a countdown
 * starts (sub_areas.leave_days in claims.toml, default 7). The player keeps their sub-area rank so they can
 * collect their things. When they've taken everything, or the countdown runs out, they're removed and
 * anything they left becomes normal claim blocks the owner can do what they want with.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class SubAreaLeave {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static int timer;

    private SubAreaLeave() {}

    // ---------- tracking ----------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        if (!Features.on(Features.SUB_AREAS, Features.SUB_AREA_LEAVE_WAIT)) return;
        if (!(e.getLevel() instanceof Level level)) return;
        if (e instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snap : multi.getReplacedBlockSnapshots()) record(level, snap.getPos(), p.getUUID());
        } else {
            record(level, e.getPos(), p.getUUID());
        }
    }

    private static void record(Level level, BlockPos pos, UUID placer) {
        Claim c = ClaimStore.at(level, pos);
        if (c == null || c.isOwner(placer)) return;
        SubArea a = c.subAreaAt(ClaimStore.dim(level), pos);
        if (a == null) return;
        a.placed.put(pos.asLong(), placer);
        ClaimStore.markDirty();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.isCanceled() || !(e.getLevel() instanceof Level level)) return;
        Claim c = ClaimStore.at(level, e.getPos());
        if (c == null) return;
        SubArea a = c.subAreaAt(ClaimStore.dim(level), e.getPos());
        if (a != null && a.placed.remove(e.getPos().asLong()) != null) ClaimStore.markDirty();
    }

    /**
     * How many blocks this player placed in the sub-area are still there. Records whose block is gone
     * (broken without an event, moved by a piston, blown up) are cleaned out on the way. Unloaded chunks count as still there.
     */
    public static int blocksLeft(MinecraftServer server, Claim c, SubArea a, UUID player) {
        ServerLevel level = levelOf(server, a);
        int n = 0;
        boolean changed = false;
        for (Iterator<Map.Entry<Long, UUID>> it = a.placed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, UUID> en = it.next();
            if (!en.getValue().equals(player)) continue;
            BlockPos pos = BlockPos.of(en.getKey());
            if (level != null && level.isLoaded(pos)) {
                BlockState st = level.getBlockState(pos);
                if (st.isAir() || !a.contains(pos)) {
                    it.remove();
                    changed = true;
                    continue;
                }
            }
            n++;
        }
        if (changed) ClaimStore.markDirty();
        return n;
    }

    private static ServerLevel levelOf(MinecraftServer server, SubArea a) {
        for (ServerLevel l : server.getAllLevels()) if (ClaimStore.dim(l).equals(a.dimension())) return l;
        return null;
    }

    // ---------- removing a player ----------

    /**
     * Remove a player from a sub-area, unless blocks they placed are still in it. Then the removal waits
     * and the countdown starts. Messages the owner (and the player, if online). Returns true when they were removed now.
     */
    public static boolean remove(ServerPlayer by, Claim c, SubArea a, UUID player) {
        String name = Claiming.name(by.server, player);
        Long until = a.leaving.get(player);
        if (until != null) {
            Msg.send(by, "subarea.leave_pending", "player", name, "area", a.name(), "time", timeLeft(until));
            return false;
        }
        int left = Features.on(Features.SUB_AREA_LEAVE_WAIT) ? blocksLeft(by.server, c, a, player) : 0;
        if (left == 0) {
            a.members().remove(player);
            if (player.equals(a.manager)) a.manager = null;
            a.placed.values().removeIf(player::equals);
            ClaimStore.markDirty();
            ActionLog.log(ActionLog.Category.SUB_AREAS, by, c, "sub-area untrusted", name + " from " + a.name());
            return true;
        }
        long deadline = System.currentTimeMillis() + ClaimsConfig.subAreaLeaveDays * DAY;
        a.leaving.put(player, deadline);
        ClaimStore.markDirty();
        Msg.send(by, "subarea.leave_started", "player", name, "area", a.name(), "blocks", left, "days", ClaimsConfig.subAreaLeaveDays);
        ActionLog.log(ActionLog.Category.SUB_AREAS, by, c, "sub-area removal countdown",
                name + " from " + a.name() + ", " + left + " blocks left, " + ClaimsConfig.subAreaLeaveDays + " days");
        ServerPlayer target = by.server.getPlayerList().getPlayer(player);
        if (target != null) {
            Msg.send(target, "subarea.leave_notice", "area", a.name(), "claim", c.name(), "blocks", left, "days", ClaimsConfig.subAreaLeaveDays);
        }
        return false;
    }

    /** Owner changed their mind: stop the countdown. The player stays in the sub-area. */
    public static boolean cancel(ServerPlayer by, Claim c, SubArea a, UUID player) {
        if (a.leaving.remove(player) == null) return false;
        ActionLog.log(ActionLog.Category.SUB_AREAS, by, c, "sub-area countdown stopped", ActionLog.name(player) + " stays in " + a.name());
        ClaimStore.markDirty();
        return true;
    }

    public static String timeLeft(long until) {
        long ms = Math.max(0, until - System.currentTimeMillis());
        long days = ms / DAY, hours = (ms % DAY) / (60L * 60 * 1000);
        return days > 0 ? days + "d " + hours + "h" : hours + "h " + (ms % (60L * 60 * 1000)) / 60000 + "m";
    }

    // ---------- countdown ----------

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (++timer < 1200) return; // once a minute
        timer = 0;
        MinecraftServer server = e.getServer();
        long now = System.currentTimeMillis();
        for (Claim c : new ArrayList<>(ClaimStore.all())) {
            for (SubArea a : c.subAreas()) {
                if (a.leaving.isEmpty()) continue;
                for (UUID player : new ArrayList<>(a.leaving.keySet())) {
                    boolean timeUp = now >= a.leaving.get(player);
                    if (!timeUp && blocksLeft(server, c, a, player) > 0) continue;
                    a.leaving.remove(player);
                    a.members().remove(player);
                    if (player.equals(a.manager)) a.manager = null;
                    ActionLog.log(ActionLog.Category.SUB_AREAS, "Server", null, c, "sub-area removal done",
                            Claiming.name(server, player) + " from " + a.name() + (timeUp ? " (time ran out)" : " (took their blocks)"));
                    // What they left behind is no longer theirs.
                    a.placed.values().removeIf(player::equals);
                    ClaimStore.markDirty();
                    notify(server, player, timeUp ? "subarea.leave_done_timeout" : "subarea.leave_done_empty", a, c);
                    if (c.owner() != null) {
                        notify(server, c.owner(), timeUp ? "subarea.leave_owner_timeout" : "subarea.leave_owner_empty", a, c,
                                "player", Claiming.name(server, player));
                    }
                }
            }
        }
    }

    private static void notify(MinecraftServer server, UUID who, String key, SubArea a, Claim c, Object... extra) {
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p == null) return;
        Object[] pairs = new Object[4 + extra.length];
        pairs[0] = "area";
        pairs[1] = a.name();
        pairs[2] = "claim";
        pairs[3] = c.name();
        System.arraycopy(extra, 0, pairs, 4, extra.length);
        Msg.send(p, key, pairs);
    }
}
