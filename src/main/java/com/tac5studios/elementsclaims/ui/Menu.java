package com.tac5studios.elementsclaims.ui;

import com.tac5studios.elementsclaims.config.Features;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * A server-side menu in a vanilla chest screen (1 to 6 rows).
 * Subclasses fill slots in {@link #draw()} with {@link #set}; call {@link #refresh()} to redraw.
 */
public abstract class Menu {

    private final int rows;
    private Component title;
    private final Map<Integer, Button> buttons = new HashMap<>();
    protected ServerPlayer viewer;
    private ChestGui gui;

    protected Menu(int rows, Component title) {
        this.rows = Math.max(1, Math.min(6, rows));
        this.title = title;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return rows * 9;
    }

    public Component title() {
        return title;
    }

    /** Changes the title; takes effect the next time the menu opens. */
    protected void setTitle(Component t) {
        title = t;
    }

    /** Fill the slots. Called on open and on every refresh. */
    protected abstract void draw();

    /** True while the menu may stay open (e.g. the claim still exists). */
    protected boolean stillValid(Player player) {
        return true;
    }

    /** Called when the screen closes (also when another menu replaces it). */
    protected void onClose(ServerPlayer player) {}

    // ---------- slots ----------

    protected void set(int slot, Button b) {
        if (slot < 0 || slot >= size()) return;
        buttons.put(slot, b);
        if (gui != null) gui.show(slot, b == null ? ItemStack.EMPTY : b.icon().copy());
    }

    protected void set(int row, int col, Button b) {
        set(row * 9 + col, b);
    }

    protected void clear() {
        buttons.clear();
        if (gui != null) {
            for (int i = 0; i < size(); i++) gui.show(i, ItemStack.EMPTY);
        }
    }

    /** Fill every empty slot in a row with blank panes. */
    protected void fillRow(int row) {
        for (int c = 0; c < 9; c++) {
            if (!buttons.containsKey(row * 9 + c)) set(row, c, Button.display(Icons.filler()));
        }
    }

    // ---------- open / close ----------

    /** Opens this menu for a player (on the next tick, so it's safe from inside a click). */
    public void open(ServerPlayer player) {
        player.server.tell(new TickTask(player.server.getTickCount(), () -> openNow(player)));
    }

    private void openNow(ServerPlayer player) {
        this.viewer = player;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
            gui = new ChestGui(id, inv, this);
            redraw();
            return gui;
        }, title));
    }

    /** Redraw everything now. */
    public void refresh() {
        if (gui == null) return;
        redraw();
        gui.broadcastChanges();
    }

    private void redraw() {
        clear();
        draw();
    }

    public void close() {
        if (viewer != null && gui != null && viewer.containerMenu == gui) viewer.closeContainer();
    }

    final void closed(ServerPlayer player, ChestGui which) {
        if (which != gui) return;
        gui = null;
        onClose(player);
    }

    /** The player shift-clicked an item in their own inventory while this menu is open. Nothing happens. */
    final boolean inventoryShiftClick(ServerPlayer player, ItemStack stack) {
        return false;
    }

    final void handleClick(ServerPlayer player, int slot, Click click) {
        Button b = buttons.get(slot);
        if (b == null || b.action() == null) return;
        if (Features.on(Features.MENU, Features.MENU_SOUNDS)) {
            player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.4f, 1f);
        }
        b.action().run(player, click);
    }
}
