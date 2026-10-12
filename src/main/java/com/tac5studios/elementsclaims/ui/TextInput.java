package com.tac5studios.elementsclaims.ui;

import com.tac5studios.elementsclaims.ElementsClaims;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Text entry with a vanilla anvil screen (claim and rank names, player names). The player types in the
 * rename box and clicks the result to submit. Costs no XP, and the paper is never given out.
 */
public final class TextInput extends AnvilMenu {

    private final BiConsumer<ServerPlayer, String> onSubmit;
    private final Consumer<ServerPlayer> onCancel;
    private String text;
    private boolean done;

    private TextInput(int id, Inventory inv, String start, BiConsumer<ServerPlayer, String> onSubmit, Consumer<ServerPlayer> onCancel) {
        super(id, inv, ContainerLevelAccess.NULL);
        this.onSubmit = onSubmit;
        this.onCancel = onCancel;
        this.text = start;
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_NAME, Component.literal(start));
        inputSlots.setItem(0, paper);
    }

    /**
     * Ask a player to type something.
     * @param title     screen title, e.g. "Enter a price"
     * @param start     text already in the box
     * @param onSubmit  gets the typed text
     * @param onCancel  runs if the screen closes without submitting (may be null)
     */
    public static void ask(ServerPlayer player, Component title, String start,
                           BiConsumer<ServerPlayer, String> onSubmit, Consumer<ServerPlayer> onCancel) {
        player.server.tell(new TickTask(player.server.getTickCount(), () ->
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TextInput(id, inv, start, onSubmit, onCancel), title))));
    }

    @Override
    public boolean setItemName(String name) {
        text = name == null ? "" : name;
        createResult();
        return true;
    }

    @Override
    public void createResult() {
        ItemStack out = new ItemStack(Items.PAPER);
        out.set(DataComponents.CUSTOM_NAME, Component.literal(text == null ? "" : text));
        resultSlots.setItem(0, out);
        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId <= 2) {
            if (slotId == 2 && player instanceof ServerPlayer sp && !done) {
                done = true;
                String result = text == null ? "" : text.trim();
                sp.closeContainer();
                try {
                    onSubmit.accept(sp, result);
                } catch (RuntimeException e) {
                    ElementsClaims.LOGGER.error("[Claims] Text input handler failed.", e);
                }
            } else {
                sendAllDataToRemote();
            }
            return;
        }
        if (type == ClickType.QUICK_MOVE || type == ClickType.PICKUP_ALL) {
            sendAllDataToRemote();
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public void removed(Player player) {
        inputSlots.clearContent(); // the paper is only a prop
        resultSlots.clearContent();
        super.removed(player);
        if (!done && player instanceof ServerPlayer sp) {
            done = true;
            if (onCancel != null) onCancel.accept(sp);
        }
    }
}
