package com.tac5studios.elementsclaims.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.perms.Perm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Staff alerts for problems someone needs to look at (a broken claims.json or detected.toml).
 * Each alert goes to the log, to online staff with claims.admin, to staff when they log in,
 * and to a Discord channel when alerts.discord_webhook is set in claims.toml.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Alerts {

    /** Alerts raised since the server started. Shown to staff when they log in. */
    private static final List<String> ACTIVE = new ArrayList<>();
    private static HttpClient http;

    private Alerts() {}

    public static synchronized void staff(String text) {
        ElementsClaims.LOGGER.error("[Claims] >>> STAFF ALERT <<< {}", text);
        if (!Features.on(Features.ALERTS)) return;
        ACTIVE.add(text);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && Features.on(Features.ALERTS_CHAT)) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (Perm.has(p, Perm.ADMIN)) Msg.send(p, "alerts.staff", "text", text);
            }
        }
        if (Features.on(Features.ALERTS_DISCORD)) discord(text);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || ACTIVE.isEmpty() || !Features.on(Features.ALERTS, Features.ALERTS_CHAT)) return;
        if (!Perm.has(p, Perm.ADMIN)) return;
        for (String text : List.copyOf(ACTIVE)) Msg.send(p, "alerts.staff", "text", text);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        ACTIVE.clear();
    }

    private static void discord(String text) {
        String url = ClaimsConfig.alertWebhook;
        if (url == null || url.isBlank()) return;
        String role = ClaimsConfig.alertRoleId == null ? "" : ClaimsConfig.alertRoleId.trim();
        JsonObject body = new JsonObject();
        body.addProperty("username", "Elements: Claims");
        body.addProperty("content", (role.isEmpty() ? "" : "<@&" + role + "> ") + "**Claims alert:** " + text);
        JsonObject mentions = new JsonObject();
        JsonArray roles = new JsonArray();
        if (!role.isEmpty()) roles.add(role);
        mentions.add("roles", roles);
        mentions.add("parse", new JsonArray());
        body.add("allowed_mentions", mentions);
        try {
            if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((res, ex) -> {
                if (ex != null) ElementsClaims.LOGGER.warn("[Claims] Could not send the Discord alert: {}", ex.toString());
                else if (res.statusCode() >= 300) ElementsClaims.LOGGER.warn("[Claims] Discord answered {} to the alert.", res.statusCode());
            });
        } catch (IllegalArgumentException ex) {
            ElementsClaims.LOGGER.warn("[Claims] alerts.discord_webhook in claims.toml is not a valid URL.");
        }
    }
}
