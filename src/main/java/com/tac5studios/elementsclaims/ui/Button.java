package com.tac5studios.elementsclaims.ui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * One slot in a menu: a vanilla item with a name and lore, and what happens when it's clicked.
 * {@code action} may be null for display-only slots (fillers, stock counts).
 */
public record Button(ItemStack icon, Action action) {

    @FunctionalInterface
    public interface Action {
        void run(ServerPlayer player, Click click);
    }

    public static Button display(ItemStack icon) {
        return new Button(icon, null);
    }

    public static Button of(ItemStack icon, Action action) {
        return new Button(icon, action);
    }
}
