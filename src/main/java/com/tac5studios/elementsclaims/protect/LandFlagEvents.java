package com.tac5studios.elementsclaims.protect;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.LandFlag;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.integration.Integrations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Land flags (SPEC section 4), enforced through NeoForge events. Fluids, fire spread, dispensers, leaf decay
 * and melting are enforced by mixins (see the mixin package) through {@link LandRules}.
 * Also: riding (Vehicles & Riding / Interact via EntityMountEvent) and Carry On pickups.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class LandFlagEvents {

    private LandFlagEvents() {}

    // ---------- PvP ----------

    /** PvP off: no player-vs-player damage in the claim, including arrows and tamed pets fighting for players. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDamage(LivingIncomingDamageEvent e) {
        if (!Features.landFlagOn(LandFlag.PVP)) return;
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide()) return;
        Player attacker = playerBehind(e.getSource().getEntity());
        if (attacker == null) return;
        Player victim = target instanceof Player p ? p : ownerOf(target);
        if (victim == null || victim == attacker) return;
        if (!LandRules.allows(target.level(), target.blockPosition(), LandFlag.PVP)
                || !LandRules.allows(attacker.level(), attacker.blockPosition(), LandFlag.PVP)) {
            e.setCanceled(true);
        }
    }

    private static Player playerBehind(Entity src) {
        if (src instanceof Player p) return p;
        if (src instanceof OwnableEntity o && o.getOwner() instanceof Player p) return p;
        return null;
    }

    private static Player ownerOf(Entity e) {
        return e instanceof OwnableEntity o && o.getOwner() instanceof Player p ? p : null;
    }

    // ---------- explosions ----------

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        Level level = e.getLevel();
        if (level.isClientSide() || !Features.landFlagOn(LandFlag.EXPLOSIONS)) return;
        e.getAffectedBlocks().removeIf(pos -> !LandRules.allows(level, pos, LandFlag.EXPLOSIONS));
        e.getAffectedEntities().removeIf(ent -> (ent instanceof HangingEntity || ent instanceof ArmorStand
                || ent instanceof Boat || ent instanceof AbstractMinecart || ent instanceof net.minecraft.world.entity.item.ItemEntity)
                && !LandRules.allows(level, ent.blockPosition(), LandFlag.EXPLOSIONS));
    }

    // ---------- mob griefing ----------

    @SubscribeEvent
    public static void onMobGrief(EntityMobGriefingEvent e) {
        Entity ent = e.getEntity();
        if (ent == null || ent.level().isClientSide() || ent instanceof Player) return;
        if (!LandRules.allows(ent.level(), ent.blockPosition(), LandFlag.MOB_GRIEFING)) e.setCanGrief(false);
    }

    /** Withers, the dragon and other mobs that destroy blocks they touch. */
    @SubscribeEvent
    public static void onLivingDestroy(LivingDestroyBlockEvent e) {
        if (e.getEntity() instanceof Player || e.getEntity().level().isClientSide()) return;
        if (!LandRules.allows(e.getEntity().level(), e.getPos(), LandFlag.MOB_GRIEFING)) e.setCanceled(true);
    }

    // ---------- outside griefing ----------

    /** Pistons outside a claim can't push, pull or break blocks inside it. */
    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre e) {
        if (!(e.getLevel() instanceof Level level) || level.isClientSide()) return;
        if (!Features.landFlagOn(LandFlag.OUTSIDE_GRIEFING)) return;
        BlockPos piston = e.getPos();
        PistonStructureResolver r = e.getStructureHelper();
        if (r == null) return;
        if (!r.resolve()) return;
        List<BlockPos> touched = new ArrayList<>(r.getToPush());
        touched.addAll(r.getToDestroy());
        for (BlockPos p : r.getToPush()) touched.add(p.relative(e.getDirection()));
        touched.add(piston.relative(e.getDirection()));
        for (BlockPos p : touched) {
            if (!LandRules.crossBorderAllowed(level, piston, p)) {
                e.setCanceled(true);
                return;
            }
        }
    }

    /** Projectiles from outside a claim (or from untrusted players) can't hit blocks or decorations inside it. */
    @SubscribeEvent
    public static void onProjectile(ProjectileImpactEvent e) {
        Projectile proj = e.getProjectile();
        Level level = proj.level();
        if (level.isClientSide()) return;
        HitResult hit = e.getRayTraceResult();
        // Fishing rods (vanilla, Aquaculture): hooking a mob needs its Attack flag, hooking an item needs Item Pickup.
        if (proj instanceof net.minecraft.world.entity.projectile.FishingHook && proj.getOwner() instanceof Player angler
                && hit instanceof EntityHitResult eh) {
            Entity t = eh.getEntity();
            RankFlag f = t instanceof net.minecraft.world.entity.item.ItemEntity ? RankFlag.ITEM_PICKUP : Classify.attack(t, angler);
            if (f != null && !Access.check(angler, level, t.blockPosition(), f)) e.setCanceled(true);
            return;
        }
        BlockPos at;
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) at = bh.getBlockPos();
        else if (hit instanceof EntityHitResult eh && !(eh.getEntity() instanceof LivingEntity && !(eh.getEntity() instanceof ArmorStand))) at = eh.getEntity().blockPosition();
        else return;
        Claim c = ClaimStore.at(level, at);
        if (c == null) return;
        Entity owner = proj.getOwner();
        if (owner instanceof Player p) {
            if (!Access.can(p, c, at, RankFlag.BUILD)) e.setCanceled(true);
            return;
        }
        BlockPos from = owner != null ? owner.blockPosition() : BlockPos.containing(proj.position().subtract(proj.getDeltaMovement().scale(4)));
        if (!LandRules.crossBorderAllowed(level, from, at)) e.setCanceled(true);
    }

    // ---------- spawning ----------

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent e) {
        MobSpawnType t = e.getSpawnType();
        if (t != MobSpawnType.NATURAL && t != MobSpawnType.CHUNK_GENERATION && t != MobSpawnType.PATROL
                && t != MobSpawnType.EVENT && t != MobSpawnType.REINFORCEMENT) return;
        if (!(e.getLevel() instanceof Level level) || level.isClientSide()) return;
        BlockPos pos = BlockPos.containing(e.getX(), e.getY(), e.getZ());
        MobCategory cat = e.getEntity().getType().getCategory();
        LandFlag flag = cat == MobCategory.MONSTER ? LandFlag.MONSTER_SPAWNING : cat == MobCategory.MISC ? null : LandFlag.ANIMAL_SPAWNING;
        if (flag != null && !LandRules.allows(level, pos, flag)) e.setSpawnCancelled(true);
    }

    // ---------- farming ----------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTrample(BlockEvent.FarmlandTrampleEvent e) {
        if (!(e.getLevel() instanceof Level level) || level.isClientSide()) return;
        if (!LandRules.allows(level, e.getPos(), LandFlag.CROP_TRAMPLING)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Pre e) {
        if (!(e.getLevel() instanceof Level level) || level.isClientSide()) return;
        if (!LandRules.allows(level, e.getPos(), LandFlag.GROWTH)) e.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
    }

    @SubscribeEvent
    public static void onTreeGrow(BlockGrowFeatureEvent e) {
        if (!(e.getLevel() instanceof Level level) || level.isClientSide()) return;
        if (!LandRules.allows(level, e.getPos(), LandFlag.GROWTH)) e.setCanceled(true);
    }

    // ---------- riding (Vehicles & Riding, seats = Interact) ----------

    @SubscribeEvent
    public static void onMount(EntityMountEvent e) {
        if (!e.isMounting() || !(e.getEntityMounting() instanceof ServerPlayer p)) return;
        Entity vehicle = e.getEntityBeingMounted();
        if (vehicle == null || vehicle.level().isClientSide()) return;
        boolean ride = vehicle instanceof Boat || vehicle instanceof AbstractMinecart || vehicle instanceof Animal
                || vehicle instanceof Saddleable || vehicle instanceof Player;
        if (vehicle instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID())) return;
        RankFlag flag = ride ? RankFlag.VEHICLES : RankFlag.INTERACT;
        if (!Access.check(p, vehicle.level(), vehicle.blockPosition(), flag)) e.setCanceled(true);
    }

    // ---------- Carry On (sneak + empty hand on a block) ----------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCarryOnBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!Integrations.carryOn()) return;
        Player p = e.getEntity();
        if (p.level().isClientSide() || !p.isShiftKeyDown() || !p.getMainHandItem().isEmpty() || !p.getOffhandItem().isEmpty()) return;
        Level level = e.getLevel();
        BlockPos pos = e.getPos();
        Claim c = ClaimStore.at(level, pos);
        if (c == null) return;
        BlockState state = level.getBlockState(pos);
        if (level.getBlockEntity(pos) == null) return; // Carry On only picks up blocks with contents
        boolean staff = p instanceof ServerPlayer sp && Access.staff(sp, c);
        if (!staff && (Integrations.neverCarry(level, pos, state)
                || !Access.check(p, level, pos, RankFlag.BUILD) || !Access.check(p, level, pos, RankFlag.CONTAINERS))) {
            e.setCanceled(true);
        }
    }
}
