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
    /** The property new chunks are added to (null = the player's first property). */
    public java.util.UUID activeProperty;
    /** Items waiting to be handed back on next login (kept items from an admin-claim death). */
    public final List<ItemStack> pendingItems = new ArrayList<>();
}
