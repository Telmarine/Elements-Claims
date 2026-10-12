package com.tac5studios.elementsclaims.ui;

import com.tac5studios.elementsclaims.messages.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * A 6-row menu with a page table: rows 1-5 hold entries, row 6 is navigation.
 * Two layouts:
 *  - ROWS: one entry per row (5 per page)
 *  - SLOTS: one entry per slot (45 per page)
 * Bottom row: [prev] [extra 1-3] [page] [extra 5-7] [next]. Subclasses add their own buttons with {@link #navButton}.
 */
public abstract class PagedMenu<T> extends Menu {

    public enum Layout { ROWS, SLOTS }

    private final Layout layout;
    protected int page;

    protected PagedMenu(Component title, Layout layout) {
        super(6, title);
        this.layout = layout;
    }

    /** Everything to show, already filtered and sorted. */
    protected abstract List<T> entries();

    /** Draw one entry. ROWS: fill columns 0-8 of {@code row}. SLOTS: {@code row} is the slot index 0-44. */
    protected abstract void drawEntry(T entry, int row);

    /** Add extra bottom-row buttons here (slots 46-48 and 50-52). */
    protected void drawNav() {}

    /** Shown when there are no entries. */
    protected Component emptyText() {
        return Msg.menu("menu.empty");
    }

    public int perPage() {
        return layout == Layout.ROWS ? 5 : 45;
    }

    public int pages(int count) {
        return Math.max(1, (count + perPage() - 1) / perPage());
    }

    @Override
    protected void draw() {
        List<T> all = entries();
        int pages = pages(all.size());
        page = Math.max(0, Math.min(page, pages - 1));

        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < all.size(); i++) {
            drawEntry(all.get(start + i), i);
        }
        if (all.isEmpty()) {
            set(22, Button.display(Icons.named(Items.BARRIER, emptyText())));
        }

        // Navigation row
        if (page > 0) {
            set(45, Button.of(Icons.named(Items.ARROW, Msg.menu("menu.prev")), (p, c) -> {
                page--;
                refresh();
            }));
        }
        if (page < pages - 1) {
            set(53, Button.of(Icons.named(Items.ARROW, Msg.menu("menu.next")), (p, c) -> {
                page++;
                refresh();
            }));
        }
        set(49, Button.display(Icons.named(Items.PAPER, Msg.menu("menu.page", "page", page + 1, "pages", pages))));
        drawNav();
        fillRow(5);
    }

    /** Put a button in the bottom row. {@code index} 0-2 = left side (slots 46-48), 3-5 = right side (50-52). */
    protected void navButton(int index, Button b) {
        int slot = index < 3 ? 46 + index : 50 + (index - 3);
        set(slot, b);
    }
}
