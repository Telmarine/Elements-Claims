package com.tac5studios.elementsclaims.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.protect.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Links to other mods (SPEC section 6). Every link is optional: it only runs when the mod is installed and its
 * switch under [integrations.mods] in features.toml is on. All other mods are reached by reflection, so a missing
 * or changed mod never crashes Claims — the link just does nothing.
 *
 * Mods that use NeoForge's normal events (Waystones use, FTB Ultimine, Apothic Spawners, Supplementaries slingshot,
 * deployers...) are already covered by the standard protection. The links below handle the rest.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Integrations {

    private static final Set<Long> CRATES = new HashSet<>();
    private static final Set<String> CRATE_DIMS = new HashSet<>();
    private static boolean registered;

    private Integrations() {}

    private static boolean on(String modid) {
        return Features.mod(modid) && ModList.get().isLoaded(modid);
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        loadCrates();
        if (registered) return;
        registered = true;
        if (on("sophisticatedcore")) sophisticated();
        if (on("waystones")) waystones();
        if (on("carryon")) carryOnEntities();
    }

    // ---------- always usable blocks / mobs ----------

    /** Blocks everyone may use in any claim: Lootr loot containers, Xyndra's crates. */
    public static boolean alwaysUsable(Level level, BlockPos pos, BlockState state) {
        String ns = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace();
        if ("lootr".equals(ns) && on("lootr")) return true;
        return on("xyndras_crates") && isCrate(level, pos);
    }

    /** Easy NPCs: everyone can talk to them (editing tools are checked separately). */
    public static boolean alwaysTalkable(Entity e, ItemStack hand) {
        if (!on("easy_npc")) return false;
        String ns = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace();
        return "easy_npc".equals(ns) && !isNpcTool(hand);
    }

    /** Easy NPC wand / move / preset tools. Using them on an NPC needs Manage Land. */
    public static boolean isNpcTool(ItemStack hand) {
        if (hand.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(hand.getItem()).toString();
        return id.startsWith("easy_npc") && (id.endsWith("_wand") || id.endsWith("move_easy_npc") || id.contains("preset"));
    }

    public static boolean isEasyNpc(Entity e) {
        return "easy_npc".equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
    }

    /** Gravestone: the grave's owner may always open and break their own grave. */
    public static boolean ownGrave(Player p, Level level, BlockPos pos) {
        if (!on("gravestone")) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !be.getClass().getName().equals("de.maxhenkel.gravestone.tileentity.GraveStoneTileEntity")) return false;
        try {
            Object death = be.getClass().getMethod("getDeath").invoke(be);
            Object owner = death.getClass().getMethod("getPlayerUUID").invoke(death);
            return p.getUUID().equals(owner);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    /** Shop blocks (Spud's Shops, Elements: Economy shops) use the Shops: Use flag instead of Containers. */
    public static boolean isShop(Level level, BlockPos pos, BlockState state) {
        String ns = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace();
        if ("spudaciousshops".equals(ns) && on("spudaciousshops")) return true;
        if (on("elements_economy")) {
            if (!shopGuardLooked) {
                shopGuardLooked = true;
                try {
                    shopGuard = Class.forName("com.tac5studios.elementseconomy.shop.ShopGuard").getMethod("isProtected", Level.class, BlockPos.class);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                    ElementsClaims.LOGGER.warn("[Claims] Elements: Economy shop link unavailable: {}", ex.toString());
                }
            }
            if (shopGuard == null) return false;
            try {
                return Boolean.TRUE.equals(shopGuard.invoke(null, level, pos));
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return false;
            }
        }
        return false;
    }

    private static Method shopGuard;
    private static boolean shopGuardLooked;

    /** MineColonies: no claims over colonies. */
    public static boolean inColony(Level level, BlockPos pos) {
        if (!on("minecolonies")) return false;
        try {
            Class<?> mgr = Class.forName("com.minecolonies.api.colony.IColonyManager");
            Object inst = mgr.getMethod("getInstance").invoke(null);
            Method m = mgr.getMethod("isCoordinateInAnyColony", Level.class, BlockPos.class);
            return Boolean.TRUE.equals(m.invoke(inst, level, pos));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return false;
        }
    }

    /** Carry On is installed and its link is on. */
    public static boolean carryOn() {
        return on("carryon");
    }

    /** Blocks Carry On may never pick up inside a claim: shop stock, crates, waystones / warp plates. */
    public static boolean neverCarry(Level level, BlockPos pos, BlockState state) {
        String ns = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace();
        return isShop(level, pos, state) || "waystones".equals(ns) || "lootr".equals(ns) || isCrate(level, pos);
    }

    public static boolean createOn() {
        return on("create");
    }

    public static boolean crystalToolsOn() {
        return on("crystal_tools");
    }

    public static boolean tomsOn() {
        return on("toms_storage");
    }

    // ---------- Xyndra's Crates: config/xyndras-crate/crate_data.json ----------

    private static void loadCrates() {
        CRATES.clear();
        CRATE_DIMS.clear();
        if (!on("xyndras_crates")) return;
        Path p = FMLPaths.CONFIGDIR.get().resolve("xyndras-crate").resolve("crate_data.json");
        if (!Files.exists(p)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var en : o.entrySet()) {
                String key = en.getKey();
                int i = key.lastIndexOf(':');
                String dim = i < 0 ? "minecraft:overworld" : key.substring(0, i);
                long pos = Long.parseLong(i < 0 ? key : key.substring(i + 1));
                CRATES.add(pos ^ dim.hashCode() * 31L);
                CRATE_DIMS.add(dim);
            }
            ElementsClaims.LOGGER.info("[Claims] Xyndra's Crates: {} crates can always be opened.", CRATES.size());
        } catch (Exception ex) {
            ElementsClaims.LOGGER.warn("[Claims] Could not read {}: {}", p, ex.toString());
        }
    }

    private static boolean isCrate(Level level, BlockPos pos) {
        if (CRATES.isEmpty()) return false;
        String dim = ClaimStore.dim(level);
        return CRATE_DIMS.contains(dim) && CRATES.contains(pos.asLong() ^ dim.hashCode() * 31L);
    }

    /** Re-read crate positions (/claims reload). */
    public static void reload() {
        loadCrates();
    }

    // ---------- Sophisticated Core: pump upgrades ----------

    private static void sophisticated() {
        try {
            Class<?> wh = Class.forName("net.p3pp3rf1y.sophisticatedcore.util.WorldHelper");
            Method add = wh.getMethod("addAdditionalInteractionCheck", BiPredicate.class);
            BiPredicate<Player, BlockPos> check = (player, pos) -> player == null || Access.can(player, player.level(), pos, RankFlag.BUILD);
            add.invoke(null, check);
            ElementsClaims.LOGGER.info("[Claims] Linked Sophisticated Core.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            ElementsClaims.LOGGER.warn("[Claims] Sophisticated Core link failed: {}", ex.toString());
        }
    }

    // ---------- Waystones: no teleporting into claims you can't enter ----------

    @SuppressWarnings("unchecked")
    private static void waystones() {
        try {
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent$Pre");
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, cls, ev -> {
                try {
                    Entity entity = (Entity) cls.getMethod("getEntity").invoke(ev);
                    if (!(entity instanceof ServerPlayer p)) return;
                    Level level = (Level) cls.getMethod("getTargetLevel").invoke(ev);
                    var loc = (net.minecraft.world.phys.Vec3) cls.getMethod("getTargetPosition").invoke(ev);
                    BlockPos pos = BlockPos.containing(loc);
                    Claim c = ClaimStore.at(level, pos);
                    if (c != null && !Access.can(p, c, pos, RankFlag.ENTER)) {
                        ((ICancellableEvent) ev).setCanceled(true);
                        Access.deny(p, c, RankFlag.ENTER);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Waystones changed: the Enter check in Movement still pushes the player out.
                }
            });
            ElementsClaims.LOGGER.info("[Claims] Linked Waystones.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            ElementsClaims.LOGGER.warn("[Claims] Waystones link failed: {}", ex.toString());
        }
    }

    // ---------- Carry On: its own entity pickup event ----------

    @SuppressWarnings("unchecked")
    private static void carryOnEntities() {
        try {
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("tschipp.carryon.events.EntityPickupEvent");
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, cls, ev -> {
                try {
                    Object player = field(ev, "player");
                    Object target = field(ev, "target");
                    if (!(player instanceof ServerPlayer p) || !(target instanceof Entity t)) return;
                    if (!mayCarry(p, t)) ((ICancellableEvent) ev).setCanceled(true);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            });
            ElementsClaims.LOGGER.info("[Claims] Linked Carry On.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            ElementsClaims.LOGGER.warn("[Claims] Carry On link failed: {}", ex.toString());
        }
    }

    /** Picking up a mob needs its Attack flag; Easy NPCs can never be carried in claims. */
    public static boolean mayCarry(ServerPlayer p, Entity t) {
        Claim c = ClaimStore.at(t.level(), t.blockPosition());
        if (c == null) return true;
        if (isEasyNpc(t)) return Access.staff(p, c);
        RankFlag f = com.tac5studios.elementsclaims.protect.Classify.attack(t, p);
        return f == null || Access.check(p, t.level(), t.blockPosition(), f);
    }

    private static Object field(Object o, String name) throws ReflectiveOperationException {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** Claims owned by the same player, or the same claim. Used for storage links across a border. */
    public static boolean sameOwner(Claim a, Claim b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        UUID oa = a.owner(), ob = b.owner();
        return oa != null && oa.equals(ob);
    }
}
