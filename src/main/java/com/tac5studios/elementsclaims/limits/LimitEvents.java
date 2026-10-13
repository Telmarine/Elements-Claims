package com.tac5studios.elementsclaims.limits;

import com.tac5studios.elementsclaims.ElementsClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Enforces lag limits and entity limits on new placements, breeding, spawn eggs and spawners. */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class LimitEvents {

    private LimitEvents() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        LagLimits.load();
        EntityLimits.load();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof Level level)) return;
        String msg = LagLimits.checkBlock(p, level, e.getPos(), e.getPlacedBlock());
        if (msg != null) {
            e.setCanceled(true);
            p.displayClientMessage(com.tac5studios.elementsclaims.util.Text.color(msg), true);
            p.inventoryMenu.sendAllDataToRemote();
        }
    }

    /** Entity-placing items used on a block (armor stands, frames, paintings, minecarts, boats, spawn eggs). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        BlockPos at = e.getPos().relative(e.getFace() == null ? net.minecraft.core.Direction.UP : e.getFace());
        if (refuse(p, p.level(), at, e.getItemStack())) {
            e.setUseItem(TriState.FALSE);
            p.inventoryMenu.sendAllDataToRemote();
        }
    }

    /** A spawn egg used on a mob (e.g. a villager) makes a baby. Check the limit before the egg is used up. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onUseOnEntity(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!(e.getItemStack().getItem() instanceof SpawnEggItem)) return;
        if (refuse(p, p.level(), e.getTarget().blockPosition(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            p.inventoryMenu.sendAllDataToRemote();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onUseInAir(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!(e.getItemStack().getItem() instanceof BoatItem) && !(e.getItemStack().getItem() instanceof SpawnEggItem)) return;
        if (refuse(p, p.level(), p.blockPosition(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            p.inventoryMenu.sendAllDataToRemote();
        }
    }

    private static boolean refuse(ServerPlayer p, Level level, BlockPos pos, ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof SpawnEggItem egg) {
            EntityType<?> type = egg.getType(stack);
            if (!EntityLimits.mayAdd(level, pos, EntityLimits.of(type))) {
                p.displayClientMessage(com.tac5studios.elementsclaims.messages.Msg.text("limits.entities_refused"), true);
                return true;
            }
            return false;
        }
        EntityType<?> type = entityFor(item);
        if (type == null) return false;
        String msg = LagLimits.checkEntity(p, level, pos, type);
        if (msg == null) return false;
        p.displayClientMessage(com.tac5studios.elementsclaims.util.Text.color(msg), true);
        return true;
    }

    private static EntityType<?> entityFor(Item item) {
        if (item instanceof ArmorStandItem) return EntityType.ARMOR_STAND;
        if (item == Items.ITEM_FRAME) return EntityType.ITEM_FRAME;
        if (item == Items.GLOW_ITEM_FRAME) return EntityType.GLOW_ITEM_FRAME;
        // Only the vanilla painting. Hanging items from other mods aren't paintings.
        if (item == Items.PAINTING) return EntityType.PAINTING;
        if (item instanceof MinecartItem) return EntityType.MINECART;
        if (item instanceof BoatItem) return EntityType.BOAT;
        return null;
    }

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent e) {
        var parent = e.getParentA();
        if (parent.level().isClientSide()) return;
        if (!EntityLimits.mayAdd(parent.level(), parent.blockPosition(), EntityLimits.of(parent))) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent e) {
        MobSpawnType t = e.getSpawnType();
        if (t != MobSpawnType.SPAWNER && t != MobSpawnType.TRIAL_SPAWNER && t != MobSpawnType.SPAWN_EGG && t != MobSpawnType.DISPENSER) return;
        var mob = e.getEntity();
        if (!(mob.level() instanceof Level level) || level.isClientSide()) return;
        if (!EntityLimits.mayAdd(level, BlockPos.containing(e.getX(), e.getY(), e.getZ()), EntityLimits.of(mob))) e.setSpawnCancelled(true);
    }
}
