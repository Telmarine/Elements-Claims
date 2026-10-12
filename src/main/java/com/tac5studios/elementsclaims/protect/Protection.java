package com.tac5studios.elementsclaims.protect;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.RankFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.integration.Integrations;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Layer 1 of protection: NeoForge's standard events. Covers vanilla and every mod that uses the normal events.
 * Each check goes through {@link Access}, so owners, members by rank, staff bypass and switched-off flags
 * are handled in one place.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Protection {

    private Protection() {}

    // ---------- breaking and placing ----------

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof Level level)) return;
        if (Integrations.ownGrave(e.getPlayer(), level, e.getPos())) return;
        if (!Access.check(e.getPlayer(), level, e.getPos(), RankFlag.BUILD)) e.setCanceled(true);
    }

    /** Stops the mining animation before it starts. Left-clicking a shop (buying) uses Shops: Use instead. */
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Level level = e.getLevel();
        if (level.isClientSide()) return;
        BlockState state = level.getBlockState(e.getPos());
        if (Integrations.isShop(level, e.getPos(), state)) {
            if (!Access.check(e.getEntity(), level, e.getPos(), RankFlag.SHOPS_USE)) e.setCanceled(true);
            return;
        }
        if (Integrations.ownGrave(e.getEntity(), level, e.getPos())) return;
        if (!Access.check(e.getEntity(), level, e.getPos(), RankFlag.BUILD)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof Player p) || !(e.getLevel() instanceof Level level)) return;
        if (!Access.check(p, level, e.getPos(), RankFlag.BUILD)) {
            e.setCanceled(true);
            if (p instanceof ServerPlayer sp) sp.inventoryMenu.sendAllDataToRemote();
        }
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent e) {
        if (!(e.getEntity() instanceof Player p) || !(e.getLevel() instanceof Level level)) return;
        if (!Access.can(p, level, e.getPos(), RankFlag.FARMING)) e.setCanceled(true);
    }

    // ---------- right-clicking blocks ----------

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        Level level = e.getLevel();
        if (level.isClientSide()) return;
        BlockPos pos = e.getPos();
        BlockState state = level.getBlockState(pos);
        Direction face = e.getFace() != null ? e.getFace() : Direction.UP;
        ItemStack stack = e.getItemStack();

        // 1. Using the block itself (vanilla skips it when sneaking with something in hand).
        boolean handsFull = !p.getMainHandItem().isEmpty() || !p.getOffhandItem().isEmpty();
        boolean usesBlock = !(p.isSecondaryUseActive() && handsFull);
        if (usesBlock && Classify.interactive(state.getBlock()) && !Integrations.ownGrave(p, level, pos)) {
            RankFlag flag = Integrations.isShop(level, pos, state) ? RankFlag.SHOPS_USE : Classify.blockUse(level, pos, state);
            if (flag != null && !Access.check(p, level, pos, flag)) {
                e.setUseBlock(TriState.FALSE);
                if (state.getBlock() instanceof DoorBlock && p instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundBlockUpdatePacket(level, pos.above()));
                    sp.connection.send(new ClientboundBlockUpdatePacket(level, pos.below()));
                }
            }
        }

        // 2. Using the held item on the block.
        if (stack.isEmpty()) return;
        boolean allowed;
        if (stack.getItem() instanceof BlockItem) {
            BlockPos place = state.canBeReplaced() ? pos : pos.relative(face);
            allowed = Access.check(p, level, place, RankFlag.BUILD);
        } else if (stack.getItem() instanceof BucketItem) {
            allowed = Access.check(p, level, pos, RankFlag.BUILD) && Access.check(p, level, pos.relative(face), RankFlag.BUILD);
        } else if (Classify.usedOnBlocks(stack.getItem())) {
            RankFlag flag = Classify.itemOnBlock(stack);
            allowed = switch (flag) {
                case FARMING -> Access.check(p, level, pos, flag);
                case VEHICLES -> Access.check(p, level, pos.relative(face), flag);
                default -> Access.check(p, level, pos, flag) && Access.check(p, level, pos.relative(face), flag);
            };
        } else {
            allowed = true;
        }
        if (!allowed) {
            e.setUseItem(TriState.FALSE);
            if (p instanceof ServerPlayer sp) sp.inventoryMenu.sendAllDataToRemote();
        }
    }

    // ---------- right-clicking with an item in the air ----------

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return;
        ItemStack stack = e.getItemStack();
        RankFlag flag;
        if (stack.getItem() instanceof BucketItem) flag = RankFlag.BUILD;
        else if (stack.getItem() instanceof BoatItem) flag = RankFlag.VEHICLES;
        else return;
        HitResult hit = p.pick(p.blockInteractionRange(), 1.0F, true);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = bh.getBlockPos();
        if (!Access.check(p, p.level(), pos, flag) || !Access.check(p, p.level(), pos.relative(bh.getDirection()), flag)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            if (p instanceof ServerPlayer sp) sp.inventoryMenu.sendAllDataToRemote();
        }
    }

    // ---------- mobs and other entities ----------

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (denyEntityUse(e.getEntity(), e.getTarget(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Armor stands use this one. */
    @SubscribeEvent
    public static void onEntityInteractAt(PlayerInteractEvent.EntityInteractSpecific e) {
        if (denyEntityUse(e.getEntity(), e.getTarget(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static boolean denyEntityUse(Player p, Entity target, ItemStack hand) {
        if (p.level().isClientSide()) return false;
        if (Integrations.isEasyNpc(target) && Integrations.isNpcTool(hand)) {
            // Easy NPC editing / moving tools: land managers only.
            Claim c = ClaimStore.at(p.level(), target.blockPosition());
            return c != null && p instanceof ServerPlayer sp && !Manage.canManageLand(sp, c);
        }
        if (target instanceof Player) return false; // riding players is checked when mounting
        RankFlag flag = Classify.entityUse(target, hand, p);
        return flag != null && !Access.check(p, p.level(), target.blockPosition(), flag);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return;
        RankFlag flag = Classify.attack(e.getTarget(), p);
        if (flag != null && !Access.check(p, p.level(), e.getTarget().blockPosition(), flag)) e.setCanceled(true);
    }

    /** Arrows, tridents and other projectiles shot by players. Melee is stopped earlier by onAttack. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent e) {
        Entity src = e.getSource().getEntity();
        if (!(src instanceof Player p) || p.level().isClientSide()) return;
        if (e.getSource().getDirectEntity() == p || e.getEntity() == p) return;
        RankFlag flag = Classify.attack(e.getEntity(), p);
        if (flag != null && !Access.check(p, p.level(), e.getEntity().blockPosition(), flag)) e.setCanceled(true);
    }

    // ---------- items and teleports ----------

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre e) {
        Player p = e.getPlayer();
        if (p.level().isClientSide()) return;
        ItemEntity item = e.getItemEntity();
        Entity thrower = item.getOwner();
        if (thrower != null && thrower.getUUID().equals(p.getUUID())) return; // your own drops, death drops included
        if (!Access.can(p, p.level(), item.blockPosition(), RankFlag.ITEM_PICKUP)) e.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent
    public static void onPearl(EntityTeleportEvent.EnderPearl e) {
        ServerPlayer p = e.getPlayer();
        BlockPos to = BlockPos.containing(e.getTargetX(), e.getTargetY(), e.getTargetZ());
        if (!Access.check(p, p.level(), to, RankFlag.TELEPORT_ITEMS)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChorus(EntityTeleportEvent.ChorusFruit e) {
        if (!(e.getEntity() instanceof Player p)) return;
        BlockPos to = BlockPos.containing(e.getTargetX(), e.getTargetY(), e.getTargetZ());
        if (!Access.check(p, p.level(), to, RankFlag.TELEPORT_ITEMS)) e.setCanceled(true);
    }
}
