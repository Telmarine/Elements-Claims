package com.tac5studios.elementsclaims.protect;

import com.tac5studios.elementsclaims.claim.RankFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import com.tac5studios.elementsclaims.detect.AutoSort;
import com.tac5studios.elementsclaims.integration.Integrations;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Sorts blocks, items and mobs into rank flags by what they are, so modded blocks and mobs are covered
 * without per-mod code. Owner overrides from detected.toml (auto-sort) and mod links come first.
 * A null result means "not protected": everyone may.
 */
public final class Classify {

    private static final Map<Block, Boolean> INTERACTIVE_BLOCKS = new IdentityHashMap<>();
    private static final Map<Item, Boolean> ITEMS_USED_ON_BLOCKS = new IdentityHashMap<>();

    private Classify() {}

    /**
     * Does right-clicking this block do anything? True when its class (or a parent below Block) has its own
     * use code. Blocks like stone or grass return false, so clicking them never shows a "not allowed" message.
     * NeoForge runs with Mojang names, so the method names are stable.
     */
    public static boolean interactive(Block b) {
        return INTERACTIVE_BLOCKS.computeIfAbsent(b, k -> declares(k.getClass(), Block.class, "useWithoutItem", "useItemOn"));
    }

    /** Does this item have its own "use on a block" code (wrenches, spawn eggs, tools...)? Food and most items don't. */
    public static boolean usedOnBlocks(Item i) {
        return ITEMS_USED_ON_BLOCKS.computeIfAbsent(i, k -> declares(k.getClass(), Item.class, "useOn"));
    }

    private static boolean declares(Class<?> c, Class<?> stop, String... names) {
        try {
            for (Class<?> k = c; k != null && k != stop && k != Object.class; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods()) {
                    for (String n : names) if (m.getName().equals(n)) return true;
                }
            }
        } catch (Throwable t) {
            return true; // can't tell: treat it as usable, so it stays protected
        }
        return false;
    }

    /** Right-clicking this block needs which flag? null = everyone may. */
    public static RankFlag blockUse(Level level, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        String o = AutoSort.blockOverride(b);
        if (o != null) return AutoSort.NONE.equals(o) ? null : RankFlag.byKey(o);
        if (Integrations.alwaysUsable(level, pos, state)) return null;
        RankFlag f = staticFlag(b);
        if (f != null && f != RankFlag.INTERACT) return f;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container || be instanceof MenuProvider) return RankFlag.CONTAINERS;
        if (be != null && level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, be, null) != null) return RankFlag.CONTAINERS;
        return RankFlag.INTERACT;
    }

    /** Auto-sort name for a block without a world (startup report): the flag key, or null when it does nothing. */
    public static String staticBlockFlag(Block b) {
        String o = AutoSort.blockOverride(b);
        if (o != null) return o;
        if (!interactive(b)) return null;
        RankFlag f = staticFlag(b);
        if (f == RankFlag.INTERACT && b instanceof net.minecraft.world.level.block.EntityBlock) return RankFlag.CONTAINERS.key();
        return f == null ? null : f.key();
    }

    private static RankFlag staticFlag(Block b) {
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) return RankFlag.DOORS;
        if (b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof BasePressurePlateBlock
                || b instanceof RepeaterBlock || b instanceof ComparatorBlock || b instanceof DaylightDetectorBlock
                || b instanceof NoteBlock) return RankFlag.REDSTONE;
        if (b instanceof SweetBerryBushBlock || b instanceof CaveVines || b instanceof ComposterBlock
                || b instanceof BeehiveBlock) return RankFlag.FARMING;
        if (b instanceof SignBlock || b instanceof FlowerPotBlock) return RankFlag.BUILD;
        if (b instanceof EnderChestBlock) return RankFlag.INTERACT; // personal inventory, nothing to steal
        return RankFlag.INTERACT;
    }

    /** Using this item on a block needs which flag? BlockItems are handled as placement (Build). */
    public static RankFlag itemOnBlock(ItemStack stack) {
        Item i = stack.getItem();
        if (i instanceof FlintAndSteelItem || i instanceof FireChargeItem) return RankFlag.FIRE;
        if (i instanceof HoeItem || i instanceof BoneMealItem) return RankFlag.FARMING;
        if (i instanceof BoatItem || i instanceof MinecartItem) return RankFlag.VEHICLES;
        return RankFlag.BUILD;
    }

    /** Right-clicking this entity (with this item in hand) needs which flag? */
    public static RankFlag entityUse(Entity t, ItemStack hand, Player player) {
        String ov = AutoSort.entityOverride(t.getType());
        if (ov != null) return AutoSort.NONE.equals(ov) ? null : RankFlag.byKey(ov);
        if (Integrations.alwaysTalkable(t, hand)) return null;
        if (t instanceof AbstractVillager) return RankFlag.TRADING;
        if (t instanceof ContainerEntity) return RankFlag.CONTAINERS;
        if (t instanceof LeashFenceKnotEntity) return RankFlag.INTERACT;
        if (t instanceof HangingEntity || t instanceof ArmorStand) return RankFlag.BUILD;
        if (t instanceof Boat || t instanceof AbstractMinecart) return RankFlag.VEHICLES;
        if (t instanceof OwnableEntity o && o.getOwnerUUID() != null) {
            return player.getUUID().equals(o.getOwnerUUID()) ? null : RankFlag.INTERACT;
        }
        if (t instanceof Animal a) {
            if (!hand.isEmpty() && (a.isFood(hand) || a instanceof net.neoforged.neoforge.common.IShearable)) return RankFlag.FARMING;
            if (t instanceof Saddleable || t instanceof AbstractHorse) return RankFlag.VEHICLES;
            return RankFlag.FARMING;
        }
        if (t instanceof Saddleable) return RankFlag.VEHICLES;
        return RankFlag.INTERACT;
    }

    /** Hitting this entity needs which flag? null = not protected. */
    public static RankFlag attack(Entity t, Player attacker) {
        if (t instanceof Player) return RankFlag.ATTACK_PLAYERS;
        if (t instanceof ArmorStand || t instanceof HangingEntity || t instanceof EndCrystal) return RankFlag.BUILD;
        if (t instanceof Boat || t instanceof AbstractMinecart) return RankFlag.VEHICLES;
        if (t instanceof OwnableEntity o && o.getOwnerUUID() != null) {
            return attacker.getUUID().equals(o.getOwnerUUID()) ? null : RankFlag.ATTACK_PETS;
        }
        if (t instanceof Enemy) return RankFlag.ATTACK_MONSTERS;
        if (t instanceof net.minecraft.world.entity.LivingEntity) return RankFlag.ATTACK_ANIMALS;
        return null;
    }
}
