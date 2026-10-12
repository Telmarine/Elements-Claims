package com.tac5studios.elementsclaims.menu;

import com.mojang.authlib.GameProfile;
import com.tac5studios.elementsclaims.claim.LandFlag;
import com.tac5studios.elementsclaims.claim.RankFlag;
import com.tac5studios.elementsclaims.messages.Msg;
import com.tac5studios.elementsclaims.ui.Icons;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Icons shared by the claim menus. */
public final class MenuItems {

    private MenuItems() {}

    public static Item icon(RankFlag f) {
        return switch (f) {
            case ENTER -> Items.OAK_DOOR;
            case BUILD -> Items.BRICKS;
            case CONTAINERS -> Items.CHEST;
            case DOORS -> Items.OAK_FENCE_GATE;
            case REDSTONE -> Items.REDSTONE;
            case INTERACT -> Items.CRAFTING_TABLE;
            case FARMING -> Items.WHEAT;
            case TRADING -> Items.EMERALD;
            case VEHICLES -> Items.MINECART;
            case ITEM_PICKUP -> Items.HOPPER;
            case FIRE -> Items.FLINT_AND_STEEL;
            case TELEPORT_ITEMS -> Items.ENDER_PEARL;
            case FLIGHT -> Items.ELYTRA;
            case ATTACK_PLAYERS -> Items.IRON_SWORD;
            case ATTACK_PETS -> Items.BONE;
            case ATTACK_ANIMALS -> Items.LEATHER;
            case ATTACK_MONSTERS -> Items.ROTTEN_FLESH;
            case SHOPS_USE -> Items.GOLD_NUGGET;
            case SHOPS_CREATE -> Items.BARREL;
            case MANAGE_PLAYERS -> Items.PLAYER_HEAD;
            case MANAGE_LAND -> Items.FILLED_MAP;
            case SUB_AREAS -> Items.STICK;
        };
    }

    public static Item icon(LandFlag f) {
        return switch (f) {
            case PVP -> Items.IRON_SWORD;
            case EXPLOSIONS -> Items.TNT;
            case MOB_GRIEFING -> Items.CREEPER_HEAD;
            case OUTSIDE_GRIEFING -> Items.PISTON;
            case FIRE_SPREAD -> Items.CAMPFIRE;
            case FLUID_FLOW -> Items.WATER_BUCKET;
            case MONSTER_SPAWNING -> Items.ZOMBIE_HEAD;
            case ANIMAL_SPAWNING -> Items.EGG;
            case CROP_TRAMPLING -> Items.FARMLAND;
            case GROWTH -> Items.BONE_MEAL;
            case DECAY_MELT -> Items.ICE;
            case TITLES -> Items.OAK_SIGN;
        };
    }

    /** A flag toggle: name, description, ON/OFF and the click hint. */
    public static ItemStack toggle(Item item, String name, String description, boolean on, boolean canEdit) {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.menu("menu.flag_description", "text", description));
        lore.add(Msg.menu(on ? "menu.flag_on" : "menu.flag_off"));
        if (canEdit) lore.add(Msg.menu("menu.flag_click"));
        ItemStack s = Icons.named(item, Msg.menu(on ? "menu.flag_name_on" : "menu.flag_name_off", "flag", name), lore);
        if (on) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    public static ItemStack head(MinecraftServer server, UUID id, String name, List<Component> lore) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        GameProfile profile = online != null ? online.getGameProfile() : new GameProfile(id, name);
        ItemStack s = new ItemStack(Items.PLAYER_HEAD);
        s.set(DataComponents.PROFILE, new ResolvableProfile(profile));
        return Icons.name(s, Msg.menu("menu.player_name", "player", name), lore);
    }

    public static ItemStack back() {
        return Icons.named(Items.ARROW, Msg.menu("menu.back"));
    }

    /** "Flag Name" from a key like attack_players. */
    public static String nice(String key) {
        StringBuilder b = new StringBuilder();
        for (String part : key.split("_")) {
            if (part.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return b.toString();
    }
}
