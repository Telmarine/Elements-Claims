package com.tac5studios.elementsclaims.claim;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Per-player data kept with the claims: personal settings, last seen, items waiting to be handed back. */
public final class PlayerData {
    /** Personal flag: show enter/leave messages. */
    public boolean titles = true;
    /** Personal flag: receive trust / rank messages. */
    public boolean trustMessages = true;
    /** Last login or logout, for abandoned claims. 0 = unknown. */
    public long lastSeen;
    /** Name the player had the last time they logged in. Used when the server's name cache doesn't have them. */
    public String lastName;
    /** Items returned from a rented sub-area, given on next login. */
    public final List<ItemStack> pendingItems = new ArrayList<>();
}
