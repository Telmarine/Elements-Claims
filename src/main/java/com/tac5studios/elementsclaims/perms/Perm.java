package com.tac5studios.elementsclaims.perms;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.config.Features;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Every Elements: Claims permission node, and the one place that checks them.
 * Nodes go through the NeoForge PermissionAPI, so they work with Elements: Nexus ranks
 * (permissionHandler = "elements_nexus:permissions"), any other permission mod, or plain OP levels.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Perm {

    /** Who gets a node when no permission mod says otherwise. */
    public enum Who {
        PLAYER(0),
        STAFF(2);

        final int opLevel;

        Who(int opLevel) {
            this.opLevel = opLevel;
        }
    }

    private record Entry(PermissionNode<Boolean> node, Who who, BooleanSupplier gate) {}

    private static final Map<String, Entry> NODES = new LinkedHashMap<>();

    /** Deed, menu, /claim, /trust. */
    public static final String USE = node("use", Who.PLAYER, () -> true);
    /** Admin Deed; build and edit in admin claims automatically. */
    public static final String ADMIN = node("admin", Who.STAFF, () -> true);
    /** Build and edit in any player claim automatically (no toggle). Senior staff only. */
    public static final String BYPASS = node("bypass", Who.STAFF, () -> Features.on(Features.PROTECTION));
    public static final String INFO = node("info", Who.STAFF, () -> Features.on(Features.ADMIN, Features.ADMIN_INFO));
    public static final String RELOAD = node("reload", Who.STAFF, () -> Features.on(Features.ADMIN, Features.ADMIN_RELOAD));
    /** /claims detected: see and set machines found by auto-detect. */
    public static final String DETECTED = node("detected", Who.STAFF, () -> Features.on(Features.DETECT));
    /** /claims import: import claims from other claim mods. */
    public static final String IMPORT = node("import", Who.STAFF, () -> Features.on(Features.IMPORT));
    /** Claims of players with this never become abandoned. */
    public static final String EXPIRY_EXEMPT = node("expiry.exempt", Who.STAFF, () -> Features.on(Features.EXPIRY));

    /** Chunk allowance. -1 (unset) = the starter value from claims.toml. Nexus: "claims.limit=300". */
    public static final PermissionNode<Integer> LIMIT = new PermissionNode<>("claims", "limit", PermissionTypes.INTEGER,
            (player, uuid, ctx) -> -1);

    /** Separate claims a player can have. -1 (unset) = properties.max from claims.toml. Capped at 5. Nexus: "claims.properties=3". */
    public static final PermissionNode<Integer> PROPERTIES = new PermissionNode<>("claims", "properties", PermissionTypes.INTEGER,
            (player, uuid, ctx) -> -1);

    private Perm() {}

    private static String node(String path, Who who, BooleanSupplier gate) {
        PermissionNode<Boolean> n = new PermissionNode<>("claims", path, PermissionTypes.BOOLEAN,
                (player, uuid, ctx) -> player != null && player.hasPermissions(who.opLevel));
        n.setInformation(Component.literal("claims." + path),
                Component.literal("Default: " + (who == Who.PLAYER ? "everyone" : "OP level 2+")));
        NODES.put(n.getNodeName(), new Entry(n, who, gate));
        return n.getNodeName();
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        List<PermissionNode<?>> on = new ArrayList<>();
        for (Entry e : NODES.values()) if (e.gate().getAsBoolean()) on.add(e.node());
        on.add(LIMIT);
        on.add(PROPERTIES);
        event.addNodes(on.toArray(new PermissionNode<?>[0]));
        ElementsClaims.LOGGER.info("[Claims] {} permission nodes registered.", on.size());
    }

    public static boolean enabled(String node) {
        Entry e = NODES.get(node);
        return e != null && e.gate().getAsBoolean();
    }

    /** Console and command blocks use OP level 2. */
    public static boolean has(CommandSourceStack source, String node) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return source.hasPermission(2);
        return has(player, node);
    }

    public static boolean has(ServerPlayer player, String node) {
        Entry e = NODES.get(node);
        if (e == null || !e.gate().getAsBoolean()) return false;
        try {
            return PermissionAPI.getPermission(player, e.node());
        } catch (RuntimeException ex) {
            return player.hasPermissions(e.who().opLevel);
        }
    }

    /** A yes/no node for a player who may be offline (Nexus ranks answer for offline players too). */
    public static boolean offline(UUID player, String node) {
        Entry e = NODES.get(node);
        if (e == null || !e.gate().getAsBoolean()) return false;
        try {
            return Boolean.TRUE.equals(PermissionAPI.getOfflinePermission(player, e.node()));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** The claims.limit value for an online player, or -1 when nothing sets it. */
    public static int limit(ServerPlayer player) {
        try {
            Integer v = PermissionAPI.getPermission(player, LIMIT);
            return v == null ? -1 : v;
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    /** The claims.properties value for an online player, or -1 when nothing sets it. */
    public static int properties(ServerPlayer player) {
        try {
            Integer v = PermissionAPI.getPermission(player, PROPERTIES);
            return v == null ? -1 : v;
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    /** The claims.limit value for an offline player, or -1 when nothing sets it. */
    public static int limit(UUID player) {
        try {
            Integer v = PermissionAPI.getOfflinePermission(player, LIMIT);
            return v == null ? -1 : v;
        } catch (RuntimeException ex) {
            return -1;
        }
    }
}
