package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A seated guest confirms the order: the chosen menu positions and how many minutes they will wait. */
public class ServerBoundPlaceOrderPacket implements EvilPacketsIHateThem.ServerHandled {
    private final BlockPos tablePos;
    private final List<ItemStack> items;
    private final int minutes;

    public ServerBoundPlaceOrderPacket(BlockPos tablePos, List<ItemStack> items, int minutes) {
        this.tablePos = tablePos;
        this.items = List.copyOf(items);
        this.minutes = minutes;
    }

    public ServerBoundPlaceOrderPacket(FriendlyByteBuf buffer) {
        this.tablePos = buffer.readBlockPos();
        // Never trust the size: an order has at most MAX_ORDER_SIZE positions.
        int size = Math.min(buffer.readVarInt(), CafeManagerBlockEntity.MAX_ORDER_SIZE + 1);
        List<ItemStack> read = new ArrayList<>();
        for (int i = 0; i < size; i++) read.add(buffer.readItem());
        this.items = read;
        this.minutes = buffer.readVarInt();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.tablePos);
        buffer.writeVarInt(this.items.size());
        for (ItemStack item : this.items) buffer.writeItem(item);
        buffer.writeVarInt(this.minutes);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (!player.serverLevel().isLoaded(this.tablePos)
                || !(player.serverLevel().getBlockEntity(this.tablePos) instanceof CafeMenuBlockEntity table)) {
            return;
        }
        Component error = table.placeOrder(player, this.items, this.minutes);
        if (error != null) {
            player.displayClientMessage(error.copy().withStyle(ChatFormatting.RED), true);
        } else {
            player.displayClientMessage(Component.translatable("message.cozyrestaurant.order.placed").withStyle(ChatFormatting.GREEN), true);
        }
    }
}
