package com.tac5studios.elementsclaims.menu;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.protect.Access;
import com.tac5studios.elementsclaims.ui.Button;
import com.tac5studios.elementsclaims.ui.Icons;
import com.tac5studios.elementsclaims.ui.PagedMenu;
import com.tac5studios.elementsclaims.ui.TextInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Admin claim extras (SPEC 8c): keep inventory, no hunger, block entry / exit, enter / leave commands. */
public final class AdminSettingsMenu extends ClaimMenu {

    public AdminSettingsMenu(Claim claim) {
        super(3, Msg.menu("admin.settings_title", "claim", claim.name()), claim);
    }

    @Override
    protected boolean allowed(ServerPlayer p) {
        return claim.isAdmin() && Features.on(Features.ADMIN_CLAIMS) && Access.staff(p, claim);
    }

    @Override
    protected void draw() {
        if (Features.on(Features.ADMIN_KEEP_INVENTORY)) toggle(10, Items.TOTEM_OF_UNDYING, "keep_inventory", () -> claim.keepInventory, v -> claim.keepInventory = v);
        if (Features.on(Features.ADMIN_NO_HUNGER)) toggle(11, Items.COOKED_BEEF, "no_hunger", () -> claim.noHunger, v -> claim.noHunger = v);
        if (Features.on(Features.ADMIN_BLOCK_ENTRY_EXIT)) {
            toggle(12, Items.IRON_DOOR, "block_entry", () -> claim.blockEntry, v -> claim.blockEntry = v);
            toggle(13, Items.IRON_BARS, "block_exit", () -> claim.blockExit, v -> claim.blockExit = v);
        }
        if (Features.on(Features.ADMIN_COMMANDS)) {
            set(15, Button.of(Icons.named(Items.COMMAND_BLOCK, Msg.menu("admin.enter_commands"),
                    List.of(Msg.menu("admin.commands_info", "count", claim.enterCommands.size()))), (p, c) -> new CommandsMenu(claim, true).open(p)));
            set(16, Button.of(Icons.named(Items.CHAIN_COMMAND_BLOCK, Msg.menu("admin.leave_commands"),
                    List.of(Msg.menu("admin.commands_info", "count", claim.leaveCommands.size()))), (p, c) -> new CommandsMenu(claim, false).open(p)));
        }
        set(18, Button.of(MenuItems.back(), (p, c) -> new LandConfigMenu(claim).open(p)));
        for (int r = 0; r < rows(); r++) fillRow(r);
    }

    private void toggle(int slot, Item icon, String key, Supplier<Boolean> get, Consumer<Boolean> setter) {
        set(slot, Button.of(MenuItems.toggle(icon, Msg.raw("admin." + key), Msg.raw("admin." + key + "_info"), get.get(), true), (p, c) -> {
            setter.accept(!get.get());
            ClaimStore.markDirty();
            com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.STAFF, p, claim, "admin claim setting", key + " " + (get.get() ? "on" : "off"));
            refresh();
        }));
    }

    /** Enter or leave commands: click one to remove it, or add a new one. {player} = the player's name. */
    static final class CommandsMenu extends PagedMenu<String> {
        private final Claim claim;
        private final boolean enter;

        CommandsMenu(Claim claim, boolean enter) {
            super(Msg.menu(enter ? "admin.enter_commands" : "admin.leave_commands"), Layout.SLOTS);
            this.claim = claim;
            this.enter = enter;
        }

        private List<String> list() {
            return enter ? claim.enterCommands : claim.leaveCommands;
        }

        @Override
        protected boolean stillValid(Player player) {
            return player instanceof ServerPlayer sp && ClaimStore.byId(claim.id()) == claim && Access.staff(sp, claim);
        }

        @Override
        protected List<String> entries() {
            return new ArrayList<>(list());
        }

        @Override
        protected void drawEntry(String cmd, int slot) {
            set(slot, Button.of(Icons.named(Items.PAPER, Component.literal("/" + (cmd.startsWith("/") ? cmd.substring(1) : cmd)),
                    List.of(Msg.menu("admin.command_remove"))), (p, c) -> {
                list().remove(cmd);
                ClaimStore.markDirty();
                com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.STAFF, p, claim, enter ? "enter command removed" : "leave command removed", "/" + cmd);
                refresh();
            }));
        }

        @Override
        protected void drawNav() {
            navButton(0, Button.of(MenuItems.back(), (p, c) -> new AdminSettingsMenu(claim).open(p)));
            navButton(3, Button.of(Icons.named(Items.WRITABLE_BOOK, Msg.menu("admin.command_add"), List.of(Msg.menu("admin.command_add_info"))), (p, c) ->
                    TextInput.ask(p, Msg.menu("admin.command_add"), "", (who, text) -> {
                        if (!text.isBlank() && ClaimStore.byId(claim.id()) == claim) {
                            list().add(text.trim());
                            ClaimStore.markDirty();
                            com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.STAFF, who, claim, enter ? "enter command added" : "leave command added", "/" + text.trim());
                        }
                        new CommandsMenu(claim, enter).open(who);
                    }, who -> new CommandsMenu(claim, enter).open(who))));
        }
    }
}
