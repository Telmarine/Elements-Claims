package com.tac5studios.elementsclaims.border;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.deed.Deed;
import com.tac5studios.elementsclaims.subarea.SubAreas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Glowing claim outlines, sent only to the player holding the Deed.
 * Each edge is a thin block display entity with a coloured glow. The entities exist only on that
 * player's client (packets only), so they never touch the world, saves or other players.
 *
 * Colours: own green · trusted aqua · other players red · admin gold · preview (unclaimed chunk you stand in) white.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Borders {

    private static final float T = 0.0625f;   // bar thickness
    private static final float POST = 2.5f;   // corner post height

    private static final class Shown {
        final List<Integer> ids = new ArrayList<>();
        String signature = "";
    }

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    private Borders() {}

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if ((p.tickCount + p.getId()) % 10 != 0) return;

        boolean stick = Features.on(Features.SUB_AREAS) && SubAreas.holding(p);
        boolean show = Features.on(Features.BORDERS) && ((Features.on(Features.BORDERS_DEED) && Deed.holding(p)) || stick);
        Shown s = SHOWN.get(p.getUUID());
        if (!show) {
            if (s != null) clear(p, s);
            return;
        }
        int y = (int) Math.floor(p.getY());
        ChunkPos here = p.chunkPosition();
        // Redraw only when something that changes the picture changed.
        SubAreas.Selection sel = SubAreas.selection(p.getUUID());
        String sig = ClaimStore.dim(p.level()) + "|" + here.x + "," + here.z + "|" + (y >> 2) + "|" + ClaimStore.version()
                + "|" + stick + "|" + (sel == null ? "" : sel.first().asLong());
        if (s != null && sig.equals(s.signature)) return;
        if (s == null) {
            s = new Shown();
            SHOWN.put(p.getUUID(), s);
        }
        draw(p, s, here, y, stick, sel);
        s.signature = sig;
    }

    private static void draw(ServerPlayer p, Shown s, ChunkPos here, int y, boolean stick, SubAreas.Selection sel) {
        removeEntities(p, s);
        String dim = ClaimStore.dim(p.level());
        int r = ClaimsConfig.borderRadius;
        double by = y + 0.02;
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();

        for (int cx = here.x - r; cx <= here.x + r && s.ids.size() < ClaimsConfig.borderMaxSegments; cx++) {
            for (int cz = here.z - r; cz <= here.z + r && s.ids.size() < ClaimsConfig.borderMaxSegments; cz++) {
                Claim c = ClaimStore.at(dim, ChunkPos.asLong(cx, cz));
                if (c == null) continue;
                int color = colorFor(p, c);
                double x0 = cx << 4, z0 = cz << 4;
                if (ClaimStore.at(dim, ChunkPos.asLong(cx, cz - 1)) != c) bar(p, s, packets, x0, by, z0, 16, T, T, color);           // north
                if (ClaimStore.at(dim, ChunkPos.asLong(cx, cz + 1)) != c) bar(p, s, packets, x0, by, z0 + 16 - T, 16, T, T, color);  // south
                if (ClaimStore.at(dim, ChunkPos.asLong(cx - 1, cz)) != c) bar(p, s, packets, x0, by, z0, T, T, 16, color);           // west
                if (ClaimStore.at(dim, ChunkPos.asLong(cx + 1, cz)) != c) bar(p, s, packets, x0 + 16 - T, by, z0, T, T, 16, color);  // east
            }
        }

        // Sub-areas (yellow boxes) while holding the stick, and the first corner picked (white).
        if (stick) {
            java.util.Set<Claim> seen = new java.util.HashSet<>();
            for (int cx = here.x - r; cx <= here.x + r; cx++) {
                for (int cz = here.z - r; cz <= here.z + r; cz++) {
                    Claim c = ClaimStore.at(dim, ChunkPos.asLong(cx, cz));
                    if (c == null || !seen.add(c)) continue;
                    for (SubArea a : c.subAreas()) box(p, s, packets, a.min(), a.max(), ClaimsConfig.colorSubArea);
                }
            }
            if (sel != null) box(p, s, packets, sel.first(), sel.first(), ClaimsConfig.colorPreview);
        }

        // White preview around the unclaimed chunk the player stands in.
        if (!stick && Features.on(Features.BORDERS_PREVIEW) && ClaimStore.at(dim, here) == null) {
            double x0 = here.getMinBlockX(), z0 = here.getMinBlockZ();
            int color = ClaimsConfig.colorPreview;
            bar(p, s, packets, x0, by, z0, 16, T, T, color);
            bar(p, s, packets, x0, by, z0 + 16 - T, 16, T, T, color);
            bar(p, s, packets, x0, by, z0, T, T, 16, color);
            bar(p, s, packets, x0 + 16 - T, by, z0, T, T, 16, color);
            bar(p, s, packets, x0, by, z0, T, POST, T, color);
            bar(p, s, packets, x0 + 16 - T, by, z0, T, POST, T, color);
            bar(p, s, packets, x0, by, z0 + 16 - T, T, POST, T, color);
            bar(p, s, packets, x0 + 16 - T, by, z0 + 16 - T, T, POST, T, color);
        }
        if (!packets.isEmpty()) p.connection.send(new ClientboundBundlePacket(packets));
    }

    /** The 12 edges of a block box (inclusive corners). */
    private static void box(ServerPlayer p, Shown s, List<Packet<? super ClientGamePacketListener>> packets,
                            net.minecraft.core.BlockPos a, net.minecraft.core.BlockPos b, int color) {
        double x0 = a.getX(), y0 = a.getY(), z0 = a.getZ();
        float sx = b.getX() - a.getX() + 1, sy = b.getY() - a.getY() + 1, sz = b.getZ() - a.getZ() + 1;
        double x1 = x0 + sx - T, y1 = y0 + sy - T, z1 = z0 + sz - T;
        for (double yy : new double[]{y0, y1}) {
            bar(p, s, packets, x0, yy, z0, sx, T, T, color);
            bar(p, s, packets, x0, yy, z1, sx, T, T, color);
            bar(p, s, packets, x0, yy, z0, T, T, sz, color);
            bar(p, s, packets, x1, yy, z0, T, T, sz, color);
        }
        bar(p, s, packets, x0, y0, z0, T, sy, T, color);
        bar(p, s, packets, x1, y0, z0, T, sy, T, color);
        bar(p, s, packets, x0, y0, z1, T, sy, T, color);
        bar(p, s, packets, x1, y0, z1, T, sy, T, color);
    }

    private static int colorFor(ServerPlayer p, Claim c) {
        if (c.isAdmin()) return ClaimsConfig.colorAdmin;
        if (c.isOwner(p.getUUID())) return ClaimsConfig.colorOwn;
        if (c.isMember(p.getUUID())) return ClaimsConfig.colorTrusted;
        return ClaimsConfig.colorOther;
    }

    /** One glowing bar: a block display at (x, y, z) scaled to (sx, sy, sz). */
    private static void bar(ServerPlayer p, Shown s, List<Packet<? super ClientGamePacketListener>> packets,
                            double x, double y, double z, float sx, float sy, float sz, int color) {
        if (s.ids.size() >= ClaimsConfig.borderMaxSegments) return;
        Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(p.level());
        if (d == null) return;
        CompoundTag tag = new CompoundTag();
        CompoundTag state = new CompoundTag();
        state.putString("Name", BuiltInRegistries.BLOCK.getKey(ClaimsConfig.borderBlock).toString());
        tag.put("block_state", state);
        CompoundTag tf = new CompoundTag();
        tf.put("translation", floats(0, 0, 0));
        tf.put("left_rotation", floats(0, 0, 0, 1));
        tf.put("scale", floats(sx, sy, sz));
        tf.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("transformation", tf);
        CompoundTag light = new CompoundTag();
        light.putInt("block", 15);
        light.putInt("sky", 15);
        tag.put("brightness", light);
        tag.putInt("glow_color_override", color);
        tag.putFloat("view_range", 2.0f);
        d.load(tag);
        d.setGlowingTag(true);
        d.setPos(x, y, z);

        packets.add(new ClientboundAddEntityPacket(d.getId(), d.getUUID(), x, y, z, 0f, 0f,
                EntityType.BLOCK_DISPLAY, 0, Vec3.ZERO, 0d));
        List<SynchedEntityData.DataValue<?>> data = d.getEntityData().getNonDefaultValues();
        if (data != null) packets.add(new ClientboundSetEntityDataPacket(d.getId(), data));
        s.ids.add(d.getId());
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(FloatTag.valueOf(f));
        return l;
    }

    private static void removeEntities(ServerPlayer p, Shown s) {
        if (s.ids.isEmpty()) return;
        int[] ids = s.ids.stream().mapToInt(Integer::intValue).toArray();
        p.connection.send(new ClientboundRemoveEntitiesPacket(ids));
        s.ids.clear();
    }

    private static void clear(ServerPlayer p, Shown s) {
        removeEntities(p, s);
        SHOWN.remove(p.getUUID());
    }

    /** The client forgets every entity on dimension change and respawn, so just forget ours. */
    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        SHOWN.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        SHOWN.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SHOWN.remove(e.getEntity().getUUID());
    }
}
