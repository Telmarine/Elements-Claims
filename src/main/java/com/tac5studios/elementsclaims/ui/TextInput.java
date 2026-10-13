package com.tac5studios.elementsclaims.ui;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.messages.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Text entry through chat (claim and rank names, player names). The menu closes, the player gets a
 * prompt, and the next thing they type in chat is the answer. That chat line is never sent to anyone else.
 * Typing "cancel", logging out or waiting too long stops it.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class TextInput {

    private static final long TIMEOUT_MS = 2 * 60 * 1000;

    private record Pending(BiConsumer<ServerPlayer, String> onSubmit, Consumer<ServerPlayer> onCancel, long until) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static int timer;

    private TextInput() {}

    /**
     * Ask a player to type something.
     * @param title     what to type, e.g. "New claim name"
     * @param start     the current value, shown so they know what they're changing (may be empty)
     * @param onSubmit  gets the typed text
     * @param onCancel  runs if they type "cancel" (may be null). Timing out or logging out just drops it.
     */
    public static void ask(ServerPlayer player, Component title, String start,
                           BiConsumer<ServerPlayer, String> onSubmit, Consumer<ServerPlayer> onCancel) {
        // Next tick, so it doesn't close a menu in the middle of its own click.
        player.server.tell(new TickTask(player.server.getTickCount(), () -> {
            player.closeContainer();
            PENDING.put(player.getUUID(), new Pending(onSubmit, onCancel, System.currentTimeMillis() + TIMEOUT_MS));
            Msg.send(player, "input.prompt", "prompt", title.getString());
            if (start != null && !start.isBlank()) Msg.send(player, "input.current", "text", start);
        }));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        Pending pending = PENDING.remove(p.getUUID());
        if (pending == null) return;
        e.setCanceled(true); // never goes out to the server
        String text = e.getRawText() == null ? "" : e.getRawText().trim();
        p.server.execute(() -> {
            if (text.equalsIgnoreCase("cancel")) {
                Msg.send(p, "input.cancelled");
                if (pending.onCancel() != null) pending.onCancel().accept(p);
                return;
            }
            try {
                pending.onSubmit().accept(p, text);
            } catch (RuntimeException ex) {
                ElementsClaims.LOGGER.error("[Claims] Text input handler failed.", ex);
            }
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        PENDING.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (PENDING.isEmpty() || ++timer < 20) return;
        timer = 0;
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(en -> {
            if (now < en.getValue().until()) return false;
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(en.getKey());
            if (p != null) Msg.send(p, "input.timeout");
            return true;
        });
    }
}
