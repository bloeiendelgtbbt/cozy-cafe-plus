package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** The guest cancels what hasn't been served of their order. */
public class ServerBoundCancelOrderPacket implements EvilPacketsIHateThem.ServerHandled {
    private final BlockPos tablePos;

    public ServerBoundCancelOrderPacket(BlockPos tablePos) {
        this.tablePos = tablePos;
    }

    public ServerBoundCancelOrderPacket(FriendlyByteBuf buffer) {
        this.tablePos = buffer.readBlockPos();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.tablePos);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (!player.serverLevel().isLoaded(this.tablePos)) return;
        if (player.serverLevel().getBlockEntity(this.tablePos) instanceof CafeMenuBlockEntity table) {
            table.cancelOrder(player);
        }
    }
}
