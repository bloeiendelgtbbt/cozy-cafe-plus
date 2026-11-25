package io.github.chakyl.cozycafe.util;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Who may manage a cafe: its owner (the player who placed the Cafe Manager) and server operators.
 */
public final class CafeAccess {
    /** Operator permission level that grants access to every cafe (same level as most admin commands). */
    public static final int OPERATOR_LEVEL = 2;
    private static final double MAX_DISTANCE_SQR = 10.0 * 10.0;

    private CafeAccess() {
    }

    public static boolean isOperator(Player player) {
        return player.hasPermissions(OPERATOR_LEVEL);
    }

    public static boolean canManage(Player player, CafeManagerBlockEntity cafe) {
        return cafe.isOwner(player.getUUID()) || isOperator(player);
    }

    /** Cafe staff: the owner or a listed waiter (the apron is checked separately). */
    public static boolean isStaff(Player player, CafeManagerBlockEntity cafe) {
        return cafe.isOwner(player.getUUID()) || cafe.isWaiter(player.getUUID());
    }

    /** Plates (empty, full or dirty) are handled only by the cafe's staff and operators. */
    public static boolean canHandlePlates(Player player, CafeManagerBlockEntity cafe) {
        return isStaff(player, cafe) || isOperator(player);
    }

    public static Component platesStaffOnlyMessage() {
        return Component.translatable("message.cozyrestaurant.plate.staff_only").withStyle(ChatFormatting.RED);
    }

    /** Serving guests needs staff status and the apron (with no armor or accessories). */
    public static boolean canServe(Player player, CafeManagerBlockEntity cafe) {
        return isStaff(player, cafe) && ApronRules.isDressedAsWaiter(player);
    }

    /** A table (or anything else) outside every cafe's claim. */
    public static Component noCafeMessage() {
        return Component.translatable("message.cozyrestaurant.order.no_cafe").withStyle(ChatFormatting.RED);
    }

    public static Component cannotServeMessage() {
        return Component.translatable("block.cozyrestaurant.cafe_menu.staff_only").withStyle(ChatFormatting.RED);
    }

    public static Component deniedMessage(CafeManagerBlockEntity cafe) {
        if (!cafe.hasOwner()) {
            return Component.translatable("block.cozyrestaurant.cafe_manager.no_owner").withStyle(ChatFormatting.RED);
        }
        return Component.translatable("block.cozyrestaurant.cafe_manager.not_owner", cafe.getOwnerName()).withStyle(ChatFormatting.RED);
    }

    /**
     * Server-side check for management packets: the player must be close to the manager and allowed to manage it.
     * Tells the player why when access is denied.
     */
    public static boolean checkManage(ServerPlayer player, CafeManagerBlockEntity cafe) {
        if (player.distanceToSqr(cafe.getBlockPos().getCenter()) > MAX_DISTANCE_SQR) {
            return false;
        }
        if (!canManage(player, cafe)) {
            player.displayClientMessage(deniedMessage(cafe), true);
            return false;
        }
        return true;
    }
}
