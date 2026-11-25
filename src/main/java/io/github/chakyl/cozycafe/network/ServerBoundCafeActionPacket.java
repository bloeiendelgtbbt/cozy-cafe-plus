package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.gui.CafeManagerMenu;
import io.github.chakyl.cozycafe.gui.CafeSettingsMenu;
import io.github.chakyl.cozycafe.gui.MenuSelectorMenu;
import io.github.chakyl.cozycafe.util.CafeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Every management button of the Cafe Manager screens. The server re-checks that the player may manage this cafe.
 */
public class ServerBoundCafeActionPacket implements EvilPacketsIHateThem.ServerHandled {
    public enum Action {
        TOGGLE_OPEN, SHOW_AREA, CLEAR, OPEN_MANAGER, OPEN_MENU_EDITOR, OPEN_SETTINGS,
        SET_NPC_CUSTOMERS, WITHDRAW, SET_PRICE, REMOVE_MENU_ITEM
    }

    private final BlockPos pos;
    private final Action action;
    private final int a;
    private final int b;

    public ServerBoundCafeActionPacket(BlockPos pos, Action action) {
        this(pos, action, 0, 0);
    }

    public ServerBoundCafeActionPacket(BlockPos pos, Action action, int a, int b) {
        this.pos = pos;
        this.action = action;
        this.a = a;
        this.b = b;
    }

    public ServerBoundCafeActionPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.action = buffer.readEnum(Action.class);
        this.a = buffer.readVarInt();
        this.b = buffer.readVarInt();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeEnum(this.action);
        buffer.writeVarInt(this.a);
        buffer.writeVarInt(this.b);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(this.pos) || !(level.getBlockEntity(this.pos) instanceof CafeManagerBlockEntity cafe)) return;
        if (!CafeAccess.checkManage(player, cafe)) return;
        switch (this.action) {
            case TOGGLE_OPEN -> {
                Component error = cafe.toggleOpenFromMenu();
                if (error != null) CafeManagerBlockEntity.feedback(player, error, false);
            }
            case SHOW_AREA -> {
                Component message = cafe.showCafeArea();
                if (message != null) player.displayClientMessage(message, true);
            }
            case CLEAR -> cafe.clearCafeData();
            case OPEN_MANAGER -> CafeManagerMenu.open(player, cafe);
            case OPEN_MENU_EDITOR -> {
                if (!cafe.hasCurrency()) {
                    CafeManagerBlockEntity.feedback(player, Component.translatable("block.cozyrestaurant.cafe_manager.no_currency"), false);
                } else if (cafe.isOpen()) {
                    CafeManagerBlockEntity.feedback(player, Component.translatable("tooltip.cozyrestaurant.cafe_manager.cannot_edit"), false);
                } else {
                    MenuSelectorMenu.open(player, cafe);
                }
            }
            case OPEN_SETTINGS -> CafeSettingsMenu.open(player, cafe);
            case SET_NPC_CUSTOMERS -> cafe.setNpcCustomers(this.a != 0);
            case WITHDRAW -> {
                long given = cafe.withdrawRevenue(player);
                boolean left = !cafe.getRevenue().isEmpty();
                Component message = given == 0
                        ? Component.translatable(left ? "message.cozyrestaurant.revenue.inventory_full" : "message.cozyrestaurant.revenue.empty")
                        : Component.translatable(left ? "message.cozyrestaurant.revenue.partly" : "message.cozyrestaurant.revenue.taken", given);
                CafeManagerBlockEntity.feedback(player, message, given > 0);
            }
            case SET_PRICE -> {
                if (!cafe.isOpen()) cafe.setPrice(this.a, this.b);
            }
            case REMOVE_MENU_ITEM -> {
                if (!cafe.isOpen()) cafe.removeFromMenu(this.a);
            }
        }
    }
}
