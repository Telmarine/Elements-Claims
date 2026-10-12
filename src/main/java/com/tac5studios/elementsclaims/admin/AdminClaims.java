package com.tac5studios.elementsclaims.admin;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.config.Features;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin claim "keep inventory": players who die inside get their items and levels back when they respawn. */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class AdminClaims {

    private record Kept(List<ItemStack> items, int level, float progress, int total) {}

    private static final Map<UUID, Kept> KEPT = new HashMap<>();

    private AdminClaims() {}

    private static boolean keeps(ServerPlayer p) {
        if (!Features.on(Features.ADMIN_CLAIMS, Features.ADMIN_KEEP_INVENTORY)) return false;
        Claim c = ClaimStore.at(p.level(), p.blockPosition());
        return c != null && c.isAdmin() && c.keepInventory;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !keeps(p)) return;
        List<ItemStack> items = new ArrayList<>();
        for (ItemEntity ie : e.getDrops()) items.add(ie.getItem().copy());
        e.setCanceled(true);
        KEPT.put(p.getUUID(), new Kept(items, p.experienceLevel, p.experienceProgress, p.totalExperience));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onXp(LivingExperienceDropEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && keeps(p)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        if (!e.isWasDeath() || !(e.getEntity() instanceof ServerPlayer p)) return;
        Kept k = KEPT.remove(p.getUUID());
        if (k == null) return;
        for (ItemStack s : k.items()) if (!p.getInventory().add(s)) p.drop(s, false);
        p.experienceLevel = k.level();
        p.experienceProgress = k.progress();
        p.totalExperience = k.total();
    }

    /** Logged out on the death screen: the items are handed back on the next login instead. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Kept k = KEPT.remove(e.getEntity().getUUID());
        if (k == null) return;
        ClaimStore.player(e.getEntity().getUUID()).pendingItems.addAll(k.items());
        ClaimStore.markDirty();
    }
}
