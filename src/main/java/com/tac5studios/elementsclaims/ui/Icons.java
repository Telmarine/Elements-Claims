package com.tac5studios.elementsclaims.ui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** Builds the vanilla items menus are made of. Names and lore are never italic. */
public final class Icons {

    private Icons() {}

    public static ItemStack named(Item item, Component name, List<Component> lore) {
        ItemStack s = new ItemStack(item);
        return name(s, name, lore);
    }

    public static ItemStack named(Item item, Component name) {
        return named(item, name, List.of());
    }

    /** Sets name and lore on a copy-safe stack (the caller's stack is changed). */
    public static ItemStack name(ItemStack s, Component name, List<Component> lore) {
        s.set(DataComponents.CUSTOM_NAME, plain(name));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (Component c : lore) lines.add(plain(c));
            s.set(DataComponents.LORE, new ItemLore(lines));
        }
        s.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        return s;
    }

    /** Adds lore lines under what the item already shows (keeps its own name, enchantments, etc.). */
    public static ItemStack addLore(ItemStack s, List<Component> lore) {
        ItemLore old = s.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<Component> lines = new ArrayList<>(old.lines());
        for (Component c : lore) lines.add(plain(c));
        s.set(DataComponents.LORE, new ItemLore(lines));
        return s;
    }

    /** A blank glass pane with no tooltip. */
    public static ItemStack filler() {
        ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        s.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
        return s;
    }

    public static ItemStack blank() {
        return filler();
    }

    public static MutableComponent plain(Component c) {
        return c.copy().withStyle(st -> st.withItalic(false));
    }
}
