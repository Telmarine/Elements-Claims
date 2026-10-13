package com.tac5studios.elementsclaims.config;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.util.Alerts;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Overlap guard: two claim mods protecting the same land fight each other (and can crash the server).
 *
 * When another claim mod is installed, Claims starts in import-only mode: no claiming, no protection, no menus,
 * no outlines. Only /claims import, /claims reload, /claims log, backups and staff alerts stay on, so the
 * server owner can import the other mod's claims. After removing that mod and restarting, Claims sees it's
 * gone and starts normally. features.toml [overlap_guard] force_on = true skips the guard.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class OverlapGuard {

    /** Claim mods Claims can import from: mod id -> name and the /claims import id. */
    private static final Map<String, String[]> CLAIM_MODS = new LinkedHashMap<>();
    static {
        CLAIM_MODS.put("openpartiesandclaims", new String[]{"Open Parties and Claims", "opac"});
        CLAIM_MODS.put("ftbchunks", new String[]{"FTB Chunks", "ftb"});
        CLAIM_MODS.put("flan", new String[]{"Flan", "flan"});
        CLAIM_MODS.put("cadmus", new String[]{"Cadmus", "cadmus"});
    }

    private static List<String> found;

    private OverlapGuard() {}

    /** Installed claim mods (mod ids). */
    public static List<String> found() {
        if (found == null) {
            List<String> out = new ArrayList<>();
            try {
                for (String id : CLAIM_MODS.keySet()) if (ModList.get().isLoaded(id)) out.add(id);
            } catch (RuntimeException e) {
                return List.of(); // mod list not ready yet: ask again later
            }
            found = out;
        }
        return found;
    }

    /** True when Claims runs in import-only mode. */
    public static boolean active() {
        return !forceOn() && !found().isEmpty();
    }

    private static boolean forceOn() {
        if (Features.OVERLAP_FORCE == null) return false;
        try {
            return Features.OVERLAP_FORCE.getAsBoolean();
        } catch (IllegalStateException notLoaded) {
            return Features.OVERLAP_FORCE.getDefault();
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        if (!active()) return;
        List<String> names = new ArrayList<>(), imports = new ArrayList<>();
        for (String id : found()) {
            names.add(CLAIM_MODS.get(id)[0]);
            imports.add("/claims import " + CLAIM_MODS.get(id)[1]);
        }
        Alerts.staff("Claims is in import-only mode because " + String.join(", ", names) + " is installed."
                + " Claiming and protection are off. Run " + String.join(" / ", imports) + " (add confirm),"
                + " then remove " + String.join(", ", names) + " and restart. Claims turns on by itself once it's gone."
                + " To run both anyway, set force_on = true under [overlap_guard] in features.toml.");
    }
}
