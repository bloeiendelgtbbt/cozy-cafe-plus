package io.github.chakyl.cozycafe.util;

import io.github.chakyl.cozycafe.compat.TrinketsCompat;
import io.github.chakyl.cozycafe.item.ApronItem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The apron can only be worn with no other armor and no accessories (Trinkets), and nothing else can be put on over it.
 * The usual equip paths are blocked up front (apron use, armor slots, armor right-click); {@link #enforce} runs every
 * tick on the server as the final guard for everything else (dispensers, Trinkets slots, other mods).
 */
public final class ApronRules {
    private static final boolean TRINKETS = FabricLoader.getInstance().isModLoaded("trinkets");
    private static final EquipmentSlot[] OTHER_ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Set<UUID> WORE_APRON_LAST_TICK = new HashSet<>();

    private ApronRules() {
    }

    public static boolean isApron(ItemStack stack) {
        return stack.getItem() instanceof ApronItem;
    }

    public static boolean isWearingApron(Player player) {
        return isApron(player.getItemBySlot(EquipmentSlot.CHEST));
    }

    public static boolean hasOtherArmor(Player player) {
        for (EquipmentSlot slot : OTHER_ARMOR) {
            if (!player.getItemBySlot(slot).isEmpty()) return true;
        }
        return false;
    }

    public static boolean hasAccessories(Player player) {
        return TRINKETS && TrinketsCompat.hasAnyTrinket(player);
    }

    /** All armor slots (including the chest) and all accessory slots are empty. */
    public static boolean canPutOnApron(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && !hasOtherArmor(player) && !hasAccessories(player);
    }

    /** Wearing the apron and nothing else: the outfit a waiter needs. */
    public static boolean isDressedAsWaiter(Player player) {
        return isWearingApron(player) && !hasOtherArmor(player) && !hasAccessories(player);
    }

    public static void tellCannotPutOn(Player player) {
        player.displayClientMessage(Component.translatable("message.cozyrestaurant.apron.remove_armor").withStyle(ChatFormatting.RED), true);
    }

    public static void tellNoArmorWithApron(Player player) {
        player.displayClientMessage(Component.translatable("message.cozyrestaurant.apron.no_armor").withStyle(ChatFormatting.RED), true);
    }

    public static void enforce(ServerPlayer player) {
        boolean wearing = isWearingApron(player);
        if (wearing && (hasOtherArmor(player) || hasAccessories(player))) {
            if (WORE_APRON_LAST_TICK.contains(player.getUUID())) {
                // The apron was already on: whatever was just put on comes off again.
                for (EquipmentSlot slot : OTHER_ARMOR) {
                    ItemStack stack = player.getItemBySlot(slot);
                    if (!stack.isEmpty()) {
                        player.setItemSlot(slot, ItemStack.EMPTY);
                        giveBack(player, stack);
                    }
                }
                if (TRINKETS) {
                    List<ItemStack> removed = TrinketsCompat.removeAllTrinkets(player);
                    removed.forEach(stack -> giveBack(player, stack));
                }
                tellNoArmorWithApron(player);
            } else {
                // The apron is what was just put on: take it off.
                ItemStack apron = player.getItemBySlot(EquipmentSlot.CHEST);
                player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                giveBack(player, apron);
                tellCannotPutOn(player);
                wearing = false;
            }
            player.inventoryMenu.broadcastChanges();
        }
        if (wearing) {
            WORE_APRON_LAST_TICK.add(player.getUUID());
        } else {
            WORE_APRON_LAST_TICK.remove(player.getUUID());
        }
    }

    public static void forget(UUID playerId) {
        WORE_APRON_LAST_TICK.remove(playerId);
    }

    private static void giveBack(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack);
    }
}
