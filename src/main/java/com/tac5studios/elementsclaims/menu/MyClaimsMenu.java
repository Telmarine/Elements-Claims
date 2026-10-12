package com.tac5studios.elementsclaims.menu;

import com.tac5studios.elementsclaims.claim.Claim;
import com.tac5studios.elementsclaims.claim.ClaimStore;
import com.tac5studios.elementsclaims.claim.Claiming;
import com.tac5studios.elementsclaims.claim.PlayerData;
import com.tac5studios.elementsclaims.config.Features;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Button;
import com.tac5studios.elementsclaims.ui.Icons;
import com.tac5studios.elementsclaims.ui.PagedMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/** /claim outside a claim: your claims (click to open Land Config) and your personal settings. */
public final class MyClaimsMenu extends PagedMenu<Claim> {

    public MyClaimsMenu() {
        super(Msg.menu("my.title"), Layout.SLOTS);
    }

    @Override
    protected List<Claim> entries() {
        return new ArrayList<>(ClaimStore.ownedBy(viewer.getUUID()));
    }

    @Override
    protected void drawEntry(Claim c, int slot) {
        ChunkPos mid = c.center();
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.menu("info.chunks_line", "chunks", c.chunks().size()));
        lore.add(Msg.menu("info.where", "dimension", c.dimension(), "x", mid.getMiddleBlockX(), "z", mid.getMiddleBlockZ()));
        lore.add(Msg.menu("info.members_line", "members", c.members().size()));
        if (c.abandonedSince > 0) lore.add(Msg.menu("info.abandoned"));
        lore.add(Msg.menu("menu.player_click"));
        set(slot, Button.of(Icons.named(Items.FILLED_MAP, Msg.menu("menu.info_name", "claim", c.name()), lore),
                (p, click) -> LandConfigMenu.open(p, c)));
    }

    @Override
    protected Component emptyText() {
        return Msg.menu("my.empty");
    }

    @Override
    protected void drawNav() {
        int used = ClaimStore.chunksUsed(viewer.getUUID());
        navButton(0, Button.display(Icons.named(Items.PAPER, Msg.menu("my.counter", "used", used, "max", Claiming.allowance(viewer)))));
        if (!Features.on(Features.PERSONAL_SETTINGS)) return;
        PlayerData d = ClaimStore.player(viewer.getUUID());
        navButton(3, Button.of(MenuItems.toggle(Items.OAK_SIGN, Msg.raw("my.titles"), Msg.raw("my.titles_info"), d.titles, true), (p, c) -> {
            d.titles = !d.titles;
            ClaimStore.markDirty();
            refresh();
        }));
        navButton(4, Button.of(MenuItems.toggle(Items.WRITABLE_BOOK, Msg.raw("my.trust_messages"), Msg.raw("my.trust_messages_info"), d.trustMessages, true), (p, c) -> {
            d.trustMessages = !d.trustMessages;
            ClaimStore.markDirty();
            refresh();
        }));
    }
}
