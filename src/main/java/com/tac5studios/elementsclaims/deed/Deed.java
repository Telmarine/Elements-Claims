package com.tac5studios.elementsclaims.deed;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.menu.LandConfigMenu;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.perms.Perm;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Claim Deed: a paper (configurable) with a hidden tag. Never used up.
 *   Right-click            claim the chunk you stand in, or open Land Config when you're in a claim you manage
 *   Sneak + right-click    unclaim the chunk you stand in
 * The lore shows the player's counter live, e.g. "Chunks claimed: 37 / 300".
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Deed {

    private static final String TAG = "elements_claims";
    private static final String TOOL = "tool";
    private static final String DEED = "deed";
    private static final String ADMIN_DEED = "admin_deed";
    public static final String STICK = "stick";

    /** One click can fire both the block and the air event; only the first counts. */
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    private Deed() {}

    // ---------- the item ----------

    public static ItemStack create(ServerPlayer owner) {
        ItemStack stack = tool(ClaimsConfig.deedItem, DEED, "deed.name");
        refreshLore(stack, owner);
        return stack;
    }

    /** The Admin Deed: claims admin claims (staff with claims.admin). */
    public static ItemStack createAdmin() {
        ItemStack stack = tool(ClaimsConfig.deedItem, ADMIN_DEED, "deed.admin_name");
        List<Component> lines = new ArrayList<>();
        for (String line : Msg.raw("deed.admin_lore").split("\n")) {
            lines.add(com.tac5studios.elementsclaims.util.Text.color(line).withStyle(st -> st.withItalic(false)));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    /** A tagged tool item (Deed, Admin Deed, Sub-area Stick). */
    public static ItemStack tool(net.minecraft.world.item.Item item, String kind, String nameKey) {
        ItemStack stack = new ItemStack(item);
        CompoundTag inner = new CompoundTag();
        inner.putString(TOOL, kind);
        CompoundTag tag = new CompoundTag();
        tag.put(TAG, inner);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(DataComponents.CUSTOM_NAME, Msg.menu(nameKey));
        return stack;
    }

    /** Which Claims tool this item is ("deed", "admin_deed", "stick"), or null. */
    public static String kind(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TAG)) return null;
        String k = data.copyTag().getCompound(TAG).getString(TOOL);
        return k.isEmpty() ? null : k;
    }

    public static boolean isDeed(ItemStack stack) {
        return DEED.equals(kind(stack));
    }

    public static boolean isAdminDeed(ItemStack stack) {
        return ADMIN_DEED.equals(kind(stack));
    }

    public static boolean hasDeed(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isDeed(inv.getItem(i))) return true;
        return false;
    }

    /** Holding the Deed or the Admin Deed (shows claim outlines). */
    public static boolean holding(ServerPlayer player) {
        return isDeed(player.getMainHandItem()) || isDeed(player.getOffhandItem())
                || isAdminDeed(player.getMainHandItem()) || isAdminDeed(player.getOffhandItem());
    }

    /** Give a Deed; drops it at the player's feet if the inventory is full. */
    public static void give(ServerPlayer player) {
        ItemStack deed = create(player);
        if (!player.getInventory().add(deed)) player.drop(deed, false);
    }

    /** Rewrite the lore with the current counter. Returns true when it changed. */
    public static boolean refreshLore(ItemStack stack, ServerPlayer owner) {
        int used = ClaimStore.chunksUsed(owner.getUUID());
        int max = Claiming.allowance(owner);
        List<Component> lines = new ArrayList<>();
        for (String line : Msg.raw("deed.lore", "used", used, "max", max).split("\n")) {
            lines.add(com.tac5studios.elementsclaims.util.Text.color(line).withStyle(s -> s.withItalic(false)));
        }
        ItemLore lore = new ItemLore(lines);
        if (lore.equals(stack.get(DataComponents.LORE))) return false;
        stack.set(DataComponents.LORE, lore);
        return true;
    }

    // ---------- using it ----------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        boolean admin = isAdminDeed(e.getItemStack());
        if (!admin && !isDeed(e.getItemStack())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (admin) useAdmin(p);
        else use(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseInAir(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        boolean admin = isAdminDeed(e.getItemStack());
        if (!admin && !isDeed(e.getItemStack())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (admin) useAdmin(p);
        else use(p);
    }

    private static boolean debounce(ServerPlayer p) {
        long now = p.server.getTickCount();
        Long last = LAST_USE.get(p.getUUID());
        if (last != null && now - last < 5) return true;
        LAST_USE.put(p.getUUID(), now);
        return false;
    }

    /** Admin Deed: right-click claims (or opens) an admin claim, sneak + right-click unclaims. */
    private static void useAdmin(ServerPlayer p) {
        if (p instanceof FakePlayer || debounce(p)) return;
        if (!Features.on(Features.ADMIN_CLAIMS) || !Perm.has(p, Perm.ADMIN)) {
            Msg.send(p, "general.no_permission");
            return;
        }
        if (p.isShiftKeyDown()) {
            Claiming.unclaimHere(p);
            return;
        }
        Claim here = ClaimStore.at(p.level(), p.blockPosition());
        if (here != null && here.isAdmin()) {
            LandConfigMenu.open(p, here);
        } else {
            Claiming.claimAdminHere(p);
        }
    }

    private static void use(ServerPlayer p) {
        if (p instanceof FakePlayer || debounce(p)) return;

        if (!Features.on(Features.CLAIM_DEED) || !Perm.has(p, Perm.USE)) {
            Msg.send(p, "general.no_permission");
            return;
        }
        boolean changed;
        if (p.isShiftKeyDown()) {
            changed = Claiming.unclaimHere(p);
        } else {
            Claim here = ClaimStore.at(p.level(), p.blockPosition());
            if (here != null && Manage.canOpenMenu(p, here)) {
                if (!LandConfigMenu.open(p, here)) {
                    Msg.send(p, "claim.info_here", "claim", here.name(), "chunks", here.chunks().size(),
                            "members", here.members().size());
                }
                changed = false;
            } else if (here != null && com.tac5studios.elementsclaims.market.Market.offer(p, here)) {
                changed = false; // the land (or a sub-area) here is for sale or rent: the offer opened
            } else {
                changed = Claiming.claimHere(p);
            }
        }
        if (changed) refreshAll(p);
    }

    /** Update every Deed this player carries. */
    public static void refreshAll(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isDeed(s)) refreshLore(s, p);
        }
    }

    // ---------- keeping the counter live ----------

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        // Every 2 seconds: rank changes (allowance) and claims lost to staff show up without a click.
        if ((p.tickCount + p.getId()) % 40 != 0) return;
        refreshAll(p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (Features.on(Features.CLAIMS, Features.CLAIM_DEED_FIRST_JOIN) && Features.on(Features.CLAIM_DEED)
                && !ClaimStore.deedGiven(p.getUUID())) {
            ClaimStore.setDeedGiven(p.getUUID());
            if (!hasDeed(p)) {
                give(p);
                Msg.send(p, "deed.first_join");
            }
        }
        refreshAll(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST_USE.remove(e.getEntity().getUUID());
    }
}
