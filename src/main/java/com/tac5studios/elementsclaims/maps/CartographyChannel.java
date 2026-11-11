package com.tac5studios.elementsclaims.maps;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimMark;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.claim.Rank;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.log.ActionLog;
import com.tac5studios.elementsclaims.menu.LandConfigMenu;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.perms.Perm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Codex: Cartography support: sends the claims around each player who has the map mod (outlines, names, the
 * player's standing, sub-areas, claim marks and their own chunk numbers) and takes claim requests from the map.
 * The map only asks; every request runs through the same code as the Claim Deed. Players without the map get nothing.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID)
@SuppressWarnings("unused") // handlers are called by the event bus
public final class CartographyChannel {

    /** What each player was last sent: claim id -> tag text. */
    private static final Map<UUID, Map<Integer, String>> SENT = new HashMap<>();
    private static final Map<UUID, Long> BAND = new HashMap<>();
    private static final Map<UUID, String> DIM = new HashMap<>();
    private static final Map<UUID, int[]> RATE = new HashMap<>();
    private static int lastVersion = -1;

    private CartographyChannel() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playBidirectional(MapPayload.TYPE, MapPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) request(sp, p.tag());
        });
    }

    private static boolean on() {
        return Features.on(Features.MAPS, Features.MAP_CARTOGRAPHY);
    }

    private static boolean hasMap(ServerPlayer p) {
        return p.connection.hasChannel(MapPayload.TYPE);
    }

    private static void send(ServerPlayer p, CompoundTag t) {
        p.connection.send(new MapPayload(t));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && on() && hasMap(p)) {
            CompoundTag hello = counts(p);
            hello.putString("op", "hello");
            hello.putInt("claim_range", ClaimsConfig.mapClaimRange);
            send(p, hello);
            DIM.put(p.getUUID(), ClaimStore.dim(p.level()));
            refresh(p, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SENT.remove(id);
        BAND.remove(id);
        DIM.remove(id);
        RATE.remove(id);
    }

    /** Changes go out at most once a second, and only when claims changed or the player moved to a new chunk band. */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!on()) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        boolean changed = ClaimStore.version() != lastVersion;
        lastVersion = ClaimStore.version();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!hasMap(p)) continue;
            UUID id = p.getUUID();
            ChunkPos c = p.chunkPosition();
            long band = ChunkPos.asLong(c.x >> 2, c.z >> 2);
            String dim = ClaimStore.dim(p.level());
            boolean newDim = !dim.equals(DIM.get(id));
            boolean moved = newDim || !Long.valueOf(band).equals(BAND.get(id));
            if (!changed && !moved) continue;
            BAND.put(id, band);
            DIM.put(id, dim);
            refresh(p, newDim);
            if (changed) {
                CompoundTag counts = counts(p);
                counts.putString("op", "counts");
                send(p, counts);
            }
        }
    }

    /** Sends everything again to every player with the map (after /claims reload). */
    public static void resendAll(MinecraftServer server) {
        SENT.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (hasMap(p)) refresh(p, true);
    }

    private static CompoundTag counts(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.putInt("chunks_used", ClaimStore.chunksUsed(p.getUUID()));
        t.putInt("chunks_allowed", Claiming.allowance(p));
        t.putInt("props_used", Claiming.propertiesUsed(p.getUUID()));
        t.putInt("props_max", Claiming.maxProperties(p));
        return t;
    }

    /** Sends the claims around a player that changed since last time, and removes the ones out of range or gone. */
    private static void refresh(ServerPlayer p, boolean full) {
        UUID id = p.getUUID();
        Map<Integer, String> sent;
        if (full) {
            sent = new HashMap<>();
            SENT.put(id, sent);
            CompoundTag clear = new CompoundTag();
            clear.putString("op", "clear");
            send(p, clear);
        } else {
            sent = SENT.computeIfAbsent(id, k -> new HashMap<>());
        }
        String dim = ClaimStore.dim(p.level());
        ChunkPos c = p.chunkPosition();
        int r = ClaimsConfig.mapRange;
        Set<Integer> inRange = new HashSet<>();
        ListTag changed = new ListTag();
        Set<Claim> seen = new HashSet<>();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                Claim cl = ClaimStore.at(dim, ChunkPos.asLong(c.x + dx, c.z + dz));
                if (cl == null || !seen.add(cl)) continue;
                CompoundTag t = claimTag(p, cl, dim);
                int key = cl.id().hashCode();
                inRange.add(key);
                String text = t.toString();
                if (!text.equals(sent.get(key))) {
                    sent.put(key, text);
                    changed.add(t);
                }
            }
        }
        if (!changed.isEmpty()) {
            CompoundTag msg = new CompoundTag();
            msg.putString("op", "claims");
            msg.putString("dim", dim);
            msg.put("claims", changed);
            send(p, msg);
        }
        Set<Integer> gone = new HashSet<>(sent.keySet());
        gone.removeAll(inRange);
        if (!gone.isEmpty()) {
            for (int g : gone) sent.remove(g);
            CompoundTag rm = new CompoundTag();
            rm.putString("op", "remove");
            rm.putIntArray("ids", gone.stream().mapToInt(Integer::intValue).toArray());
            send(p, rm);
        }
    }

    private static CompoundTag claimTag(ServerPlayer p, Claim c, String dim) {
        UUID id = p.getUUID();
        boolean trusted = c.isTrusted(id);
        boolean hidden = c.isPrivate() && !trusted && !ClaimsConfig.mapShowPrivateNames && !Perm.has(p, Perm.BYPASS);
        CompoundTag t = new CompoundTag();
        t.putInt("id", c.id().hashCode());
        t.putString("name", hidden ? "" : c.name());
        t.putString("owner", hidden ? "" : Claiming.ownerName(p.server, c));
        t.putInt("standing", c.isAdmin() ? 3 : c.isOwner(id) ? 0 : trusted ? 1 : 2);
        Rank rank = c.rankOf(id);
        t.putString("rank", trusted ? rank.name() : "");
        t.putBoolean("private", hidden);
        if (!hidden) t.putInt("color", c.isAdmin() ? ClaimsConfig.colorAdmin : ClaimsConfig.colorOther);
        t.putLongArray("chunks", c.chunks(dim).stream().mapToLong(Long::longValue).toArray());
        boolean staff = c.isAdmin() ? Perm.has(p, Perm.ADMIN) : Perm.has(p, Perm.BYPASS);
        t.putBoolean("manage", c.isOwner(id) || staff);
        t.putBoolean("active", !c.isAdmin() && c == Claiming.activeProperty(id));
        if (!hidden && trusted) {
            ListTag subs = new ListTag();
            for (SubArea a : c.subAreas()) {
                if (!a.dimension().equals(dim)) continue;
                CompoundTag s = new CompoundTag();
                s.putString("name", a.name());
                s.putInt("x1", a.min().getX());
                s.putInt("y1", a.min().getY());
                s.putInt("z1", a.min().getZ());
                s.putInt("x2", a.max().getX());
                s.putInt("y2", a.max().getY());
                s.putInt("z2", a.max().getZ());
                subs.add(s);
            }
            t.put("subs", subs);
            if (rank.has(RankFlag.VIEW_CLAIM_MARKS)) {
                ListTag marks = new ListTag();
                for (ClaimMark m : c.marks()) {
                    if (!m.dimension().equals(dim)) continue;
                    CompoundTag mt = new CompoundTag();
                    mt.putString("id", m.id().toString());
                    mt.putString("name", m.name());
                    mt.putString("emblem", m.emblem());
                    mt.putInt("color", m.color());
                    mt.putInt("x", m.x());
                    mt.putInt("y", m.y());
                    mt.putInt("z", m.z());
                    marks.add(mt);
                }
                t.put("marks", marks);
            }
        }
        return t;
    }

    // ---------- requests from the map ----------

    private static boolean rateOk(ServerPlayer p) {
        int sec = (int) (System.currentTimeMillis() / 1000);
        int[] r = RATE.computeIfAbsent(p.getUUID(), k -> new int[2]);
        if (r[0] != sec) {
            r[0] = sec;
            r[1] = 0;
        }
        return ++r[1] <= ClaimsConfig.mapRequestsPerSecond;
    }

    private static void result(ServerPlayer p, String key, Object... pairs) {
        String text = Msg.raw(key, pairs);
        if (text.isEmpty()) return;
        CompoundTag t = new CompoundTag();
        t.putString("op", "result");
        t.putString("message", text.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1"));
        send(p, t);
    }

    private static Claim byMapId(int id) {
        for (Claim c : ClaimStore.all()) if (c.id().hashCode() == id) return c;
        return null;
    }

    private static void request(ServerPlayer p, CompoundTag t) {
        if (!on() || !rateOk(p)) return;
        switch (t.getString("op")) {
            case "claim" -> claim(p, t, true);
            case "unclaim" -> claim(p, t, false);
            case "open_config" -> {
                Claim c = byMapId(t.getInt("id"));
                if (c != null) LandConfigMenu.open(p, c);
            }
            case "set_active" -> {
                Claim c = byMapId(t.getInt("id"));
                if (c != null && c.isOwner(p.getUUID()) && !c.isAdmin()) {
                    Claiming.setActive(p.getUUID(), c);
                    result(p, "claim.now_active", "claim", c.name());
                    refresh(p, false);
                }
            }
            case "mark_add" -> markAdd(p, t);
            case "mark_remove" -> {
                Claim c = byMapId(t.getInt("claim"));
                if (c == null || !(c.isOwner(p.getUUID()) || Manage.canManageLand(p, c))) return;
                String mid = t.getString("id");
                if (c.marks().removeIf(m -> m.id().toString().equals(mid))) {
                    ClaimStore.markDirty();
                    ActionLog.log(ActionLog.Category.CLAIMING, p, c, "claim mark removed", "(map)");
                }
            }
            case "request" -> refresh(p, true);
            default -> { }
        }
    }

    /** Claim or unclaim chunks. Claiming is all or nothing against the chunks the player has left. */
    private static void claim(ServerPlayer p, CompoundTag t, boolean claim) {
        String dim = ClaimStore.dim(p.level());
        if (!dim.equals(t.getString("dim"))) {
            result(p, "map.other_dimension");
            return;
        }
        long[] chunks = t.getLongArray("chunks");
        if (chunks.length == 0 || chunks.length > 1024) return;
        ChunkPos here = p.chunkPosition();
        for (long ch : chunks) {
            if (Math.max(Math.abs(ChunkPos.getX(ch) - here.x), Math.abs(ChunkPos.getZ(ch) - here.z)) > ClaimsConfig.mapClaimRange) {
                result(p, "map.too_far", "range", ClaimsConfig.mapClaimRange);
                return;
            }
        }
        int done = 0;
        if (claim) {
            int left = Claiming.allowance(p) - ClaimStore.chunksUsed(p.getUUID());
            if (chunks.length > left) {
                result(p, "map.not_enough", "left", Math.max(0, left));
                return;
            }
            for (long ch : chunks) if (Claiming.claimAt(p, dim, new ChunkPos(ch), null, true)) done++;
        } else {
            for (long ch : chunks) if (Claiming.unclaimAt(p, dim, new ChunkPos(ch), true)) done++;
        }
        if (done > 0) {
            result(p, claim ? "map.claimed" : "map.unclaimed", "count", done,
                    "used", ClaimStore.chunksUsed(p.getUUID()), "max", Claiming.allowance(p));
        }
    }

    private static void markAdd(ServerPlayer p, CompoundTag t) {
        Claim c = byMapId(t.getInt("claim"));
        if (c == null || !(c.isOwner(p.getUUID()) || Manage.canManageLand(p, c))) return;
        if (c.marks().size() >= ClaimsConfig.mapMarksPerClaim) {
            result(p, "map.marks_full", "max", ClaimsConfig.mapMarksPerClaim);
            return;
        }
        String name = Manage.clip(t.getString("name"), 32);
        if (name.isBlank()) return;
        c.marks().add(new ClaimMark(UUID.randomUUID(), name, t.getString("emblem"), t.getInt("color") & 0xFFFFFF,
                ClaimStore.dim(p.level()), t.getInt("x"), t.getInt("y"), t.getInt("z")));
        ClaimStore.markDirty();
        ActionLog.log(ActionLog.Category.CLAIMING, p, c, "claim mark added", name + " (map)");
    }
}
