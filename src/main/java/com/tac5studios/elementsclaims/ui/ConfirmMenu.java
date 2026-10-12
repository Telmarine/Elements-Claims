package com.tac5studios.elementsclaims.ui;

import com.tac5studios.elementsclaims.messages.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;

/**
 * "Are you sure?" screen: the item in the middle, Yes on the left, No on the right.
 */
public final class ConfirmMenu extends Menu {

    private final ItemStack preview;
    private final List<Component> details;
    private final Consumer<ServerPlayer> onYes;
    private final Consumer<ServerPlayer> onNo;

    private ConfirmMenu(ItemStack preview, List<Component> details, Consumer<ServerPlayer> onYes, Consumer<ServerPlayer> onNo) {
        super(3, Msg.menu("menu.confirm_title"));
        this.preview = preview;
        this.details = details;
        this.onYes = onYes;
        this.onNo = onNo;
    }

    /**
     * Ask, then run onYes or onNo (onNo may be null = just close).
     * @param preview what the question is about
     * @param details lines shown on Yes
     */
    public static void ask(ServerPlayer player, ItemStack preview, List<Component> details,
                           Consumer<ServerPlayer> onYes, Consumer<ServerPlayer> onNo) {
        new ConfirmMenu(preview, details, onYes, onNo).open(player);
    }

    @Override
    protected void draw() {
        for (int i = 0; i < size(); i++) set(i, Button.display(Icons.filler()));
        set(13, Button.display(preview.copy()));
        ItemStack yes = Icons.named(Items.LIME_STAINED_GLASS_PANE, Msg.menu("menu.yes"), details);
        ItemStack no = Icons.named(Items.RED_STAINED_GLASS_PANE, Msg.menu("menu.no"));
        for (int slot : new int[]{10, 11, 12}) {
            set(slot, Button.of(yes, (p, c) -> {
                close();
                onYes.accept(p);
            }));
        }
        for (int slot : new int[]{14, 15, 16}) {
            set(slot, Button.of(no, (p, c) -> {
                close();
                if (onNo != null) onNo.accept(p);
            }));
        }
    }
}
