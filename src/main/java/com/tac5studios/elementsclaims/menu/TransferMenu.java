package com.tac5studios.elementsclaims.menu;

import com.tac5studios.elementsclaims.ElementsClaims;
import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
import com.tac5studios.elementsclaims.ui.Icons;
import com.tac5studios.elementsclaims.ui.Menu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shown to the player receiving a claim, after the owner confirmed the transfer. They pick:
 *  - add it to their current (active) property, when it's in the same dimension
 *  - keep it as a property of its own, when they have a free property slot
 *  - decline
 * Their total chunk allowance always applies.
 */
public final class TransferMenu extends Menu {

    private final Claim claim;
    private final UUID from;
    private boolean answered;

    private TransferMenu(Claim claim, UUID from) {
        super(3, Msg.menu("transfer.menu_title", "claim", claim.name()));
        this.claim = claim;
        this.from = from;
    }

    public static void offer(ServerPlayer receiver, Claim claim, UUID from) {
        new TransferMenu(claim, from).open(receiver);
    }

    @Override
    protected void draw() {
        for (int i = 0; i < size(); i++) set(i, Button.display(Icons.filler()));
        UUID me = viewer.getUUID();
        List<Component> info = new ArrayList<>();
        info.add(Msg.menu("transfer.from", "player", Claiming.name(viewer.server, from)));
        info.add(Msg.menu("transfer.confirm_chunks", "chunks", claim.chunkCount()));
        info.add(Msg.menu("transfer.your_room", Claiming.room(me)));
        set(4, Button.display(Icons.named(Items.FILLED_MAP, Msg.menu("menu.info_name", "claim", claim.name()), info)));

        Claim join = Claiming.joinTarget(me, claim);
        if (join != null) {
            set(11, Button.of(Icons.named(Items.LIME_STAINED_GLASS_PANE, Msg.menu("transfer.add_to", "property", join.name()),
                    List.of(Msg.menu("transfer.add_to_info"))), (p, c) -> accept(p, true)));
        }
        if (Claiming.canKeepSeparate(me)) {
            set(13, Button.of(Icons.named(Items.LIGHT_BLUE_STAINED_GLASS_PANE, Msg.menu("transfer.keep_separate"),
                    List.of(Msg.menu("transfer.keep_separate_info"))), (p, c) -> accept(p, false)));
        }
        set(15, Button.of(Icons.named(Items.RED_STAINED_GLASS_PANE, Msg.menu("transfer.decline")), (p, c) -> {
            answered = true;
            close();
            Msg.send(p, "transfer.declined_you", "claim", claim.name());
            tell(p.server, from, "transfer.declined", "claim", claim.name(), "player", p.getGameProfile().getName());
            com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.TRANSFERS, p, claim, "transfer declined", "offered by " + Claiming.name(p.server, from));
        }));
    }

    private void accept(ServerPlayer p, boolean addToCurrent) {
        answered = true;
        close();
        UUID me = p.getUUID();
        // Check everything again: the claim or the players' limits may have changed while the menu was open.
        if (ClaimStore.byId(claim.id()) != claim || !claim.isOwner(from)) {
            Msg.send(p, "transfer.gone", "claim", claim.name());
            return;
        }
        if (!Claiming.hasRoom(me, claim)) {
            Msg.send(p, "transfer.no_chunks_you", Claiming.pairs(Claiming.room(me), "player", p.getGameProfile().getName(), "claim", claim.name(), "chunks", claim.chunkCount()));
            tell(p.server, from, "transfer.failed", "claim", claim.name(), "player", p.getGameProfile().getName());
            return;
        }
        String name = claim.name();
        if (addToCurrent) {
            Claim into = Claiming.joinTarget(me, claim);
            if (into == null) {
                Msg.send(p, "transfer.gone", "claim", name);
                return;
            }
            Claiming.transferInto(claim, into);
            Msg.send(p, "transfer.added", "claim", name, "property", into.name());
        } else {
            if (!Claiming.canKeepSeparate(me)) {
                Msg.send(p, "transfer.no_property_slot", "player", p.getGameProfile().getName(), "claim", name);
                tell(p.server, from, "transfer.failed", "claim", name, "player", p.getGameProfile().getName());
                return;
            }
            Claiming.transfer(claim, me);
            Msg.send(p, "transfer.received", "claim", name, "player", Claiming.name(p.server, from));
        }
        tell(p.server, from, "transfer.done", "claim", name, "player", p.getGameProfile().getName());
        ElementsClaims.LOGGER.info("[Claims] {} gave claim {} to {} ({}).", Claiming.name(p.server, from), name,
                p.getGameProfile().getName(), addToCurrent ? "added to their property" : "kept separate");
        com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.TRANSFERS, Claiming.name(p.server, from), from, claim, "transferred",
                name + " to " + p.getGameProfile().getName() + (addToCurrent ? " (added to their active property)" : " (kept as its own property)"));
    }

    @Override
    protected void onClose(ServerPlayer player) {
        if (answered) return;
        answered = true;
        Msg.send(player, "transfer.declined_you", "claim", claim.name());
        tell(player.server, from, "transfer.declined", "claim", claim.name(), "player", player.getGameProfile().getName());
        com.tac5studios.elementsclaims.log.ActionLog.log(com.tac5studios.elementsclaims.log.ActionLog.Category.TRANSFERS, player, claim, "transfer declined", "closed the menu, offered by " + Claiming.name(player.server, from));
    }

    private static void tell(MinecraftServer server, UUID who, String key, Object... pairs) {
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p != null) Msg.send(p, key, pairs);
    }
}
