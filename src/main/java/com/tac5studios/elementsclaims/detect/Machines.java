package com.tac5studios.elementsclaims.detect;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.protect.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Layer 3 of mod detection: machines from other mods act through stand-in ("fake") players.
 * The first time one acts inside a claim it is recorded; the server owner picks per machine (/claims detected):
 *   player  (default) — machines inside the claim always run (Create rule); from outside they act as the
 *                       player who placed them (when the mod stores one), otherwise they're blocked.
 *   allow   — always allowed.
 *   deny    — never allowed inside other players' claims (still runs inside the claim it stands in).
 */
public final class Machines {

    public enum Mode { PLAYER, ALLOW, DENY;
        public static Mode parse(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (RuntimeException e) {
                return PLAYER;
            }
        }
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** machine key -> mode. Saved in detected.toml [machines]. */
    static final Map<String, Mode> KNOWN = new LinkedHashMap<>();
    private static final Map<Class<?>, Field> OWNER_FIELDS = new LinkedHashMap<>();

    private Machines() {}

    public static Map<String, Mode> known() {
        return KNOWN;
    }

    /** "modname:ProfileName", e.g. "create:Deployer". */
    public static String key(FakePlayer fake) {
        String module = fake.getClass().getModule() != null && fake.getClass().getModule().isNamed()
                ? fake.getClass().getModule().getName() : null;
        if (module == null) {
            String pkg = fake.getClass().getPackageName();
            String[] parts = pkg.split("\\.");
            module = parts.length > 2 ? parts[2] : pkg;
        }
        String name = fake.getGameProfile() != null && fake.getGameProfile().getName() != null
                ? fake.getGameProfile().getName() : fake.getClass().getSimpleName();
        return module + ":" + name.replace(' ', '_');
    }

    public static boolean allowed(FakePlayer fake, Claim c, BlockPos pos, RankFlag flag) {
        // A machine standing in the claim always runs there (Create rule).
        Claim home = ClaimStore.at(fake.level(), fake.blockPosition());
        if (home == c) return true;

        Mode mode = Mode.PLAYER;
        if (Features.on(Features.DETECT, Features.DETECT_MACHINES)) {
            String key = key(fake);
            Mode m = KNOWN.get(key);
            if (m == null) {
                KNOWN.put(key, Mode.PLAYER);
                AutoSort.saveLater();
                ElementsClaims.LOGGER.info("[Claims] New machine seen in a claim: {} (see /claims detected)", key);
            } else {
                mode = m;
            }
        }
        if (mode == Mode.ALLOW) return true;
        if (mode == Mode.DENY) return false;
        UUID owner = owner(fake);
        if (owner == null) return false;
        if (home != null && c.owner() != null && c.owner().equals(home.owner())) return true; // same owner's next claim
        return Access.allowedFor(owner, c, pos, flag);
    }

    /** The player who placed the machine, when the mod stores one (field "owner"/"placer"/..., or a real player's UUID). */
    public static UUID owner(FakePlayer fake) {
        Field f = OWNER_FIELDS.computeIfAbsent(fake.getClass(), Machines::findOwnerField);
        if (f != null) {
            try {
                Object v = f.get(fake);
                if (v instanceof UUID u) return u;
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
        }
        UUID id = fake.getUUID();
        MinecraftServer server = fake.getServer();
        if (id != null && server != null && server.getProfileCache() != null
                && server.getProfileCache().get(id).isPresent()) return id; // e.g. Create's deployer reports its owner's UUID
        return null;
    }

    private static Field findOwnerField(Class<?> cls) {
        for (Class<?> c = cls; c != null && c != FakePlayer.class && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (f.getType() == UUID.class && (n.contains("owner") || n.contains("placer") || n.contains("player"))) {
                    try {
                        f.setAccessible(true);
                        return f;
                    } catch (RuntimeException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}
