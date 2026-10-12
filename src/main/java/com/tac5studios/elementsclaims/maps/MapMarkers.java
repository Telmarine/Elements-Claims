package com.tac5studios.elementsclaims.maps;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Claims on web maps: BlueMap and squaremap (both reached by reflection; nothing happens without them).
 * Each claim is drawn as rectangles (one per row of touching chunks) in its outline colour, with the claim name
 * and owner on hover. Redrawn 30 seconds after claims change.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class MapMarkers {

    private static final String SET_ID = "elements_claims";
    private static int lastVersion = -1;
    private static int timer;
    private static MinecraftServer server;
    private static Object blueMapApi;
    private static boolean blueMapHooked;

    private record Rect(int x1, int z1, int x2, int z2) {}

    private MapMarkers() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        server = e.getServer();
        if (!Features.on(Features.MAPS)) return;
        if (Features.on(Features.MAP_BLUEMAP) && ModList.get().isLoaded("bluemap")) hookBlueMap();
        lastVersion = -1;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (!Features.on(Features.MAPS) || ++timer < 600) return; // check every 30 s
        timer = 0;
        if (ClaimStore.version() == lastVersion) return;
        lastVersion = ClaimStore.version();
        redraw();
    }

    private static void redraw() {
        if (server == null) return;
        if (Features.on(Features.MAP_SQUAREMAP) && ModList.get().isLoaded("squaremap")) squaremap();
        if (blueMapApi != null) blueMap(blueMapApi);
    }

    /** Rows of touching chunks merged into rectangles (block coordinates, inclusive start, exclusive end). */
    private static List<Rect> rects(Claim c) {
        Map<Integer, List<Integer>> rows = new TreeMap<>();
        for (long l : c.chunks()) rows.computeIfAbsent(ChunkPos.getZ(l), k -> new ArrayList<>()).add(ChunkPos.getX(l));
        List<Rect> out = new ArrayList<>();
        rows.forEach((z, xs) -> {
            xs.sort(Integer::compare);
            int start = xs.get(0), prev = start;
            for (int i = 1; i <= xs.size(); i++) {
                if (i < xs.size() && xs.get(i) == prev + 1) {
                    prev = xs.get(i);
                    continue;
                }
                out.add(new Rect(start << 4, z << 4, (prev + 1) << 4, (z + 1) << 4));
                if (i < xs.size()) start = prev = xs.get(i);
            }
        });
        return out;
    }

    private static int color(Claim c) {
        return c.isAdmin() ? ClaimsConfig.colorAdmin : ClaimsConfig.colorOther;
    }

    private static String label(Claim c) {
        return c.name() + " (" + Claiming.ownerName(server, c) + ")";
    }

    // ---------- squaremap ----------

    private static void squaremap() {
        try {
            Class<?> provider = Class.forName("xyz.jpenilla.squaremap.api.SquaremapProvider");
            Object api = provider.getMethod("get").invoke(null);
            Class<?> worldId = Class.forName("xyz.jpenilla.squaremap.api.WorldIdentifier");
            Class<?> keyCls = Class.forName("xyz.jpenilla.squaremap.api.Key");
            Class<?> layerCls = Class.forName("xyz.jpenilla.squaremap.api.SimpleLayerProvider");
            Class<?> markerCls = Class.forName("xyz.jpenilla.squaremap.api.marker.Marker");
            Class<?> pointCls = Class.forName("xyz.jpenilla.squaremap.api.Point");
            Class<?> optsCls = Class.forName("xyz.jpenilla.squaremap.api.marker.MarkerOptions");
            Class<?> squaremapCls = Class.forName("xyz.jpenilla.squaremap.api.Squaremap");
            Class<?> mapWorldCls = Class.forName("xyz.jpenilla.squaremap.api.MapWorld");
            Class<?> registryCls = Class.forName("xyz.jpenilla.squaremap.api.Registry");
            Class<?> optsBuilderCls = Class.forName("xyz.jpenilla.squaremap.api.marker.MarkerOptions$Builder");
            Class<?> layerBuilderCls = Class.forName("xyz.jpenilla.squaremap.api.SimpleLayerProvider$Builder");
            Object setKey = keyCls.getMethod("of", String.class).invoke(null, SET_ID);
            for (ServerLevel level : server.getAllLevels()) {
                var loc = level.dimension().location();
                Object wid = worldId.getMethod("create", String.class, String.class).invoke(null, loc.getNamespace(), loc.getPath());
                Optional<?> world = (Optional<?>) squaremapCls.getMethod("getWorldIfEnabled", worldId).invoke(api, wid);
                if (world.isEmpty()) continue;
                Object registry = mapWorldCls.getMethod("layerRegistry").invoke(world.get());
                Method has = registryCls.getMethod("hasEntry", keyCls);
                if ((boolean) has.invoke(registry, setKey)) registryCls.getMethod("unregister", keyCls).invoke(registry, setKey);
                Object builder = layerCls.getMethod("builder", String.class).invoke(null, "Claims");
                Object layer = layerBuilderCls.getMethod("build").invoke(builder);
                Method addMarker = layerCls.getMethod("addMarker", keyCls, markerCls);
                String dim = ClaimStore.dim(level);
                int n = 0;
                for (Claim c : ClaimStore.all()) {
                    if (!c.dimension().equals(dim)) continue;
                    java.awt.Color col = new java.awt.Color(color(c));
                    Object ob = optsCls.getMethod("builder").invoke(null);
                    optsBuilderCls.getMethod("strokeColor", java.awt.Color.class).invoke(ob, col);
                    optsBuilderCls.getMethod("fillColor", java.awt.Color.class).invoke(ob, col);
                    optsBuilderCls.getMethod("fillOpacity", double.class).invoke(ob, 0.25);
                    optsBuilderCls.getMethod("hoverTooltip", String.class).invoke(ob, escape(label(c)));
                    Object opts = optsBuilderCls.getMethod("build").invoke(ob);
                    for (Rect r : rects(c)) {
                        Object p1 = pointCls.getMethod("of", double.class, double.class).invoke(null, (double) r.x1(), (double) r.z1());
                        Object p2 = pointCls.getMethod("of", double.class, double.class).invoke(null, (double) r.x2(), (double) r.z2());
                        Object marker = markerCls.getMethod("rectangle", pointCls, pointCls).invoke(null, p1, p2);
                        markerCls.getMethod("markerOptions", optsCls).invoke(marker, opts);
                        addMarker.invoke(layer, keyCls.getMethod("of", String.class).invoke(null, "c" + (n++)), marker);
                    }
                }
                registryCls.getMethod("register", keyCls, Object.class).invoke(registry, setKey, layer);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            ElementsClaims.LOGGER.warn("[Claims] squaremap markers failed: {}", ex.toString());
        }
    }

    // ---------- BlueMap ----------

    @SuppressWarnings("unchecked")
    private static void hookBlueMap() {
        if (blueMapHooked) return;
        blueMapHooked = true;
        try {
            Class<?> apiCls = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            Consumer<Object> onEnable = api -> {
                blueMapApi = api;
                if (server != null) server.execute(() -> blueMap(api));
            };
            apiCls.getMethod("onEnable", Consumer.class).invoke(null, onEnable);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            ElementsClaims.LOGGER.warn("[Claims] BlueMap link failed: {}", ex.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static void blueMap(Object api) {
        try {
            Class<?> setCls = Class.forName("de.bluecolored.bluemap.api.markers.MarkerSet");
            Class<?> shapeCls = Class.forName("de.bluecolored.bluemap.api.math.Shape");
            Class<?> colorCls = Class.forName("de.bluecolored.bluemap.api.math.Color");
            Class<?> markerCls = Class.forName("de.bluecolored.bluemap.api.markers.ShapeMarker");
            Class<?> baseMarker = Class.forName("de.bluecolored.bluemap.api.markers.Marker");
            Constructor<?> setNew = setCls.getConstructor(String.class, boolean.class, boolean.class);
            Method rect = shapeCls.getMethod("createRect", double.class, double.class, double.class, double.class);
            Constructor<?> colorNew = colorCls.getConstructor(int.class, int.class, int.class, float.class);
            Constructor<?> markerNew = markerCls.getConstructor(String.class, shapeCls, float.class);
            Method setColors = markerCls.getMethod("setColors", colorCls, colorCls);
            Method setDetail = markerCls.getMethod("setDetail", String.class);
            Method put = setCls.getMethod("put", String.class, baseMarker);
            Class<?> apiCls = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            Class<?> worldCls = Class.forName("de.bluecolored.bluemap.api.BlueMapWorld");
            Class<?> mapCls = Class.forName("de.bluecolored.bluemap.api.BlueMapMap");
            for (ServerLevel level : server.getAllLevels()) {
                Optional<?> world = (Optional<?>) apiCls.getMethod("getWorld", Object.class).invoke(api, level);
                if (world.isEmpty()) continue;
                Object set = setNew.newInstance("Claims", true, false);
                String dim = ClaimStore.dim(level);
                int n = 0;
                for (Claim c : ClaimStore.all()) {
                    if (!c.dimension().equals(dim)) continue;
                    int rgb = color(c);
                    Object line = colorNew.newInstance((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 1f);
                    Object fill = colorNew.newInstance((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 0.25f);
                    for (Rect r : rects(c)) {
                        Object shape = rect.invoke(null, (double) r.x1(), (double) r.z1(), (double) r.x2(), (double) r.z2());
                        Object marker = markerNew.newInstance(label(c), shape, (float) level.getSeaLevel());
                        setColors.invoke(marker, line, fill);
                        setDetail.invoke(marker, "<b>" + escape(c.name()) + "</b><br>" + escape(Claiming.ownerName(server, c)));
                        put.invoke(set, "c" + (n++), marker);
                    }
                }
                Collection<?> maps = (Collection<?>) worldCls.getMethod("getMaps").invoke(world.get());
                for (Object map : maps) {
                    Map<String, Object> sets = (Map<String, Object>) mapCls.getMethod("getMarkerSets").invoke(map);
                    sets.put(SET_ID, set);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            ElementsClaims.LOGGER.warn("[Claims] BlueMap markers failed: {}", ex.toString());
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
