package com.tac5studios.elementsclaims.ui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The server side of a menu. The client sees a plain vanilla chest (GENERIC_9xN), so players need
 * no mod. Nothing in the top rows can ever be taken or placed: every click there goes to the
 * {@link Menu}, and the screen is resent so the client's guess is undone.
 */
final class ChestGui extends AbstractContainerMenu {

    private final Menu menu;
    private final SimpleContainer top;
    private final int topSize;

    ChestGui(int id, Inventory inv, Menu menu) {
        super(type(menu.rows()), id);
        this.menu = menu;
        this.topSize = menu.rows() * 9;
        this.top = new SimpleContainer(topSize);

        for (int i = 0; i < topSize; i++) {
            addSlot(new LockedSlot(top, i));
        }
        // Player inventory, same order as a vanilla chest menu.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 103 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 161));
        }
    }

    static MenuType<?> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    Container top() {
        return top;
    }

    void show(int slot, ItemStack stack) {
        top.setItem(slot, stack);
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < topSize) {
            if (player instanceof ServerPlayer sp && type != ClickType.QUICK_CRAFT && type != ClickType.PICKUP_ALL) {
                menu.handleClick(sp, slotId, Click.of(type, button));
            }
            resync();
            return;
        }
        if (type == ClickType.QUICK_MOVE && slotId >= topSize && slotId < slots.size() && player instanceof ServerPlayer sp) {
            ItemStack stack = slots.get(slotId).getItem();
            if (!stack.isEmpty()) menu.inventoryShiftClick(sp, stack.copy()); // e.g. add the item to a server shop
            resync();
            return;
        }
        if (type == ClickType.QUICK_MOVE || type == ClickType.PICKUP_ALL || type == ClickType.QUICK_CRAFT) {
            // Shift-click, double-click gathering and dragging could reach the top rows: not allowed.
            resync();
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    private void resync() {
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return menu.stillValid(player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != top && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.container != top;
    }

    @Override
    public void removed(Player player) {
        top.clearContent(); // display items only, never given to anyone
        super.removed(player);
        if (player instanceof ServerPlayer sp) menu.closed(sp, this);
    }

    /** Top slot: shows an item, can't be taken or filled. */
    private static final class LockedSlot extends Slot {
        LockedSlot(Container c, int i) {
            super(c, i, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
