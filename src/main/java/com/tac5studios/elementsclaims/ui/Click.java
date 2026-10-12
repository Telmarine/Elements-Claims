package com.tac5studios.elementsclaims.ui;

import net.minecraft.world.inventory.ClickType;

/** How a button was clicked. */
public enum Click {
    LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, DROP, OTHER;

    static Click of(ClickType type, int button) {
        return switch (type) {
            case PICKUP -> button == 1 ? RIGHT : LEFT;
            case QUICK_MOVE -> button == 1 ? SHIFT_RIGHT : SHIFT_LEFT;
            case CLONE -> MIDDLE;
            case THROW -> DROP;
            default -> OTHER;
        };
    }

    public boolean isLeft() {
        return this == LEFT || this == SHIFT_LEFT;
    }

    public boolean isRight() {
        return this == RIGHT || this == SHIFT_RIGHT;
    }

    public boolean isShift() {
        return this == SHIFT_LEFT || this == SHIFT_RIGHT;
    }
}
