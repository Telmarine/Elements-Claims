package com.tac5studios.elementsclaims.messages;

import net.neoforged.api.distmarker.Dist;
import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.config.TomlFile;
import com.tac5studios.elementsclaims.util.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Every player-facing line, read from config/elements_claims/messages.toml.
 *
 * Usage:  Msg.send(player, "claim.claimed", "used", 5, "max", 100);
 * Placeholders are given as name/value pairs and fill {name} in the text.
 * An empty message sends nothing. A missing key uses the built-in text from the jar.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Msg {

    private static final TomlFile FILE = new TomlFile("messages.toml");

    private Msg() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAboutToStart(ServerAboutToStartEvent e) {
        load();
    }

    /** Read messages.toml. Called at server start and by /claims reload. */
    public static void load() {
        FILE.load();
    }

    /** The raw text with placeholders filled, colors not applied. "" when the message is off. */
    public static String raw(String key, Object... pairs) {
        String s = FILE.str(key, key); // an unknown key shows itself, so typos are easy to spot
        if (s.isEmpty()) return s;
        s = s.replace("{prefix}", FILE.str("general.prefix", ""));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = s.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return s;
    }

    /** The message as colored text. Empty component when the message is off. */
    public static MutableComponent text(String key, Object... pairs) {
        return Text.color(raw(key, pairs));
    }

    /** True when the message has text (owners can switch a line off with ""). */
    public static boolean on(String key) {
        return !FILE.str(key, key).isEmpty();
    }

    public static void send(ServerPlayer player, String key, Object... pairs) {
        if (player != null && on(key)) player.sendSystemMessage(text(key, pairs));
    }

    /** Success line to a command source (player or console). */
    public static void send(CommandSourceStack source, String key, Object... pairs) {
        if (on(key)) source.sendSuccess(() -> text(key, pairs), false);
    }

    /** Success line that admins with command feedback also see. */
    public static void sendLogged(CommandSourceStack source, String key, Object... pairs) {
        if (on(key)) source.sendSuccess(() -> text(key, pairs), true);
    }

    /** Red failure line to a command source. */
    public static void fail(CommandSourceStack source, String key, Object... pairs) {
        if (on(key)) source.sendFailure(text(key, pairs));
    }

    /**
     * A clickable chat button.
     * @param labelKey message key for the label, e.g. "currency.confirm_button"
     * @param hoverKey message key for the hover text
     * @param command  command run on click
     */
    public static MutableComponent button(String labelKey, String hoverKey, String command, Object... pairs) {
        return text(labelKey, pairs).withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        text(hoverKey, pairs).withStyle(ChatFormatting.GRAY))));
    }

    /** Text for item names and lore in menus: no italics (vanilla adds them to custom names). */
    public static MutableComponent menu(String key, Object... pairs) {
        return text(key, pairs).withStyle(s -> s.withItalic(false));
    }

    /** Join parts into one line with spaces. */
    public static MutableComponent join(Component... parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append(" ");
            out.append(parts[i]);
        }
        return out;
    }
}
