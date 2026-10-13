package com.tac5studios.elementsclaims.subarea;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Manage;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.claim.SubArea;
import com.tac5studios.elementsclaims.config.ClaimsConfig;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.deed.Deed;
import com.tac5studios.elementsclaims.messages.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Sub-area Stick (SPEC 9): right-click a block for the first corner, right-click another block in the same
 * claim for the second. Sneak + right-click clears the selection. Right-click inside an existing sub-area (with no
 * corner picked) opens it.
 */
@EventBusSubscriber(modid = ElementsClaims.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class SubAreas {

    public record Selection(UUID claim, String dim, BlockPos first) {}

    private static final Map<UUID, Selection> SELECTING = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private SubAreas() {}

    public static ItemStack stick() {
        return Deed.tool(ClaimsConfig.stickItem, Deed.STICK, "stick.name");
    }

    public static boolean isStick(ItemStack s) {
        return Deed.STICK.equals(Deed.kind(s));
    }

    public static boolean holding(ServerPlayer p) {
        return isStick(p.getMainHandItem()) || isStick(p.getOffhandItem());
    }

    public static Selection selection(UUID player) {
        return SELECTING.get(player);
    }

    /**
     * May this player add, remove and rank players in this sub-area? The owner (and staff), or the sub-area's
     * manager, who only the owner can pick. Managers can only give ranks below their own claim rank.
     */
    public static boolean canManageMembers(ServerPlayer p, Claim c, SubArea a) {
        if (Manage.full(p, c)) return true;
        UUID id = p.getUUID();
        return Features.on(Features.SUB_AREA_MANAGER) && a.manager != null && a.manager.equals(id) && a.members().containsKey(id) && !c.isBanned(id);
    }

    /**
     * Ranks this player can hand out inside the sub-area. The owner gets every trusted rank. The manager gets
     * only the ranks below their own rank in the sub-area.
     */
    public static java.util.List<com.tac5studios.elementsclaims.claim.Rank> assignable(ServerPlayer p, Claim c, SubArea a) {
        java.util.List<com.tac5studios.elementsclaims.claim.Rank> out = new java.util.ArrayList<>();
        int last = c.ranks().size() - 1; // Visitor
        int lvl;
        if (Manage.full(p, c)) lvl = 0;
        else if (canManageMembers(p, c, a)) {
            com.tac5studios.elementsclaims.claim.Rank own = c.rank(a.members().get(p.getUUID()));
            lvl = own == null ? last : c.ranks().indexOf(own);
        } else return out;
        for (int i = Math.max(1, lvl + 1); i < last; i++) out.add(c.ranks().get(i));
        return out;
    }

    /** May this player create and edit sub-areas in this claim? */
    public static boolean canManage(ServerPlayer p, Claim c) {
        if (Manage.full(p, c)) return true;
        UUID id = p.getUUID();
        return c.isMember(id) && !c.isBanned(id) && c.rankOf(id).has(RankFlag.SUB_AREAS);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || !isStick(e.getItemStack())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        long now = p.server.getTickCount();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < 5) return;
        LAST.put(p.getUUID(), now);
        if (!Features.on(Features.SUB_AREAS)) {
            Msg.send(p, "general.disabled");
            return;
        }
        click(p, e.getPos());
    }

    private static void click(ServerPlayer p, BlockPos pos) {
        if (p.isShiftKeyDown()) {
            if (SELECTING.remove(p.getUUID()) != null) Msg.send(p, "stick.cleared");
            return;
        }
        Claim c = ClaimStore.at(p.level(), pos);
        if (c == null || !canManage(p, c)) {
            Msg.send(p, "stick.not_your_claim");
            return;
        }
        Selection sel = SELECTING.get(p.getUUID());
        if (sel == null) {
            SubArea existing = c.subAreaAt(ClaimStore.dim(p.level()), pos);
            if (existing != null) {
                new SubAreaMenu(c, existing).open(p);
                return;
            }
            SELECTING.put(p.getUUID(), new Selection(c.id(), ClaimStore.dim(p.level()), pos.immutable()));
            Msg.send(p, "stick.first", "x", pos.getX(), "y", pos.getY(), "z", pos.getZ());
            return;
        }
        if (!sel.claim().equals(c.id()) || !sel.dim().equals(ClaimStore.dim(p.level()))) {
            Msg.send(p, "stick.other_claim");
            return;
        }
        SELECTING.remove(p.getUUID());
        if (!inside(c, sel.dim(), sel.first(), pos)) {
            Msg.send(p, "stick.outside");
            return;
        }
        if (c.subAreas().size() >= ClaimsConfig.maxSubAreas) {
            Msg.send(p, "stick.too_many", "max", ClaimsConfig.maxSubAreas);
            return;
        }
        SubArea area = new SubArea(UUID.randomUUID(), Msg.raw("stick.default_name", "number", c.subAreas().size() + 1), sel.dim(), sel.first(), pos);
        for (SubArea other : c.subAreas()) {
            if (other.dimension().equals(area.dimension()) && overlaps(other, area)) {
                Msg.send(p, "stick.overlap", "area", other.name());
                return;
            }
        }
        c.subAreas().add(area);
        ClaimStore.markDirty();
        com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.SUB_AREAS, p, c, "sub-area made", area.name() + " " + area.min().toShortString() + " to " + area.max().toShortString());
        Msg.send(p, "stick.created", "area", area.name(), "blocks", area.volume());
        new SubAreaMenu(c, area).open(p);
    }

    /** Both corners' columns and every block between them in the claim. */
    private static boolean inside(Claim c, String dim, BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX()) >> 4, maxX = Math.max(a.getX(), b.getX()) >> 4;
        int minZ = Math.min(a.getZ(), b.getZ()) >> 4, maxZ = Math.max(a.getZ(), b.getZ()) >> 4;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (!c.has(dim, net.minecraft.world.level.ChunkPos.asLong(x, z))) return false;
        return true;
    }

    private static boolean overlaps(SubArea a, SubArea b) {
        BlockPos a1 = a.min(), a2 = a.max(), b1 = b.min(), b2 = b.max();
        return a1.getX() <= b2.getX() && a2.getX() >= b1.getX() && a1.getY() <= b2.getY() && a2.getY() >= b1.getY()
                && a1.getZ() <= b2.getZ() && a2.getZ() >= b1.getZ();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SELECTING.remove(e.getEntity().getUUID());
        LAST.remove(e.getEntity().getUUID());
    }
}
