package com.tac5studios.elementsclaims.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.detect.AutoSort;
import com.tac5studios.elementsclaims.detect.Machines;
import com.tac5studios.elementsclaims.importer.Imported;
import com.tac5studios.elementsclaims.importer.Importers;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.messages.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /claims detected and /claims import. */
final class StaffCommands {

    private StaffCommands() {}

    static int detected(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Map<String, Machines.Mode> known = Machines.known();
        Msg.send(src, "detect.header", "count", known.size());
        if (known.isEmpty()) {
            Msg.send(src, "detect.none");
            return 1;
        }
        for (Map.Entry<String, Machines.Mode> e : known.entrySet()) {
            String key = e.getKey();
            MutableComponent line = Msg.text("detect.line", "machine", key, "mode", Msg.raw("detect.mode_" + e.getValue().key()));
            for (Machines.Mode m : Machines.Mode.values()) {
                if (m == e.getValue()) continue;
                line.append(" ").append(Msg.button("detect.button_" + m.key(), "detect.button_hover",
                        "/claims detected set \"" + key + "\" " + m.key(), "machine", key));
            }
            src.sendSuccess(() -> line, false);
        }
        return 1;
    }

    static int detectedSet(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "machine");
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(Locale.ROOT);
        if (!List.of("player", "allow", "deny").contains(mode)) {
            Msg.fail(ctx.getSource(), "detect.bad_mode", "mode", mode);
            return 0;
        }
        Machines.known().put(key, Machines.Mode.parse(mode));
        AutoSort.save();
        Msg.sendLogged(ctx.getSource(), "detect.set", "machine", key, "mode", Msg.raw("detect.mode_" + mode));
        return 1;
    }

    static int importMod(CommandContext<CommandSourceStack> ctx, boolean confirm) {
        CommandSourceStack src = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "mod").toLowerCase(Locale.ROOT);
        if (!Importers.IDS.contains(id)) {
            Msg.fail(src, "import.unknown", "mod", id, "mods", String.join(", ", Importers.IDS));
            return 0;
        }
        if (!Importers.enabled(id)) {
            Msg.fail(src, "general.disabled");
            return 0;
        }
        List<String> notes = new ArrayList<>();
        List<Imported> list;
        try {
            list = Importers.read(id, src.getServer(), notes);
        } catch (Exception ex) {
            ElementsClaims.LOGGER.error("[Claims] Import from {} failed.", id, ex);
            Msg.fail(src, "import.failed", "mod", id, "reason", String.valueOf(ex.getMessage()));
            return 0;
        }
        Importers.Preview pv = Importers.preview(list, notes);
        Msg.send(src, confirm ? "import.applying" : "import.preview", "mod", id, "claims", pv.claims(), "chunks", pv.chunks(),
                "skipped", pv.skippedChunks(), "admin", pv.admin());
        for (String n : notes) src.sendSuccess(() -> Component.literal(" - " + n), false);
        if (!confirm) {
            src.sendSuccess(() -> Msg.button("import.confirm_button", "import.confirm_hover", "/claims import " + id + " confirm"), false);
            return 1;
        }
        String backup = ClaimStore.backup("import-" + id);
        int made = Importers.apply(list);
        Msg.sendLogged(src, "import.done", "mod", id, "claims", made, "backup", backup == null ? "-" : backup);
        return made;
    }
}
