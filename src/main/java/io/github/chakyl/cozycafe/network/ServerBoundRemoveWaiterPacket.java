package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.util.CafeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class ServerBoundRemoveWaiterPacket implements EvilPacketsIHateThem.ServerHandled {
    private final BlockPos pos;
    private final UUID waiter;

    public ServerBoundRemoveWaiterPacket(BlockPos pos, UUID waiter) {
        this.pos = pos;
        this.waiter = waiter;
    }

    public ServerBoundRemoveWaiterPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.waiter = buffer.readUUID();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeUUID(this.waiter);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (!player.serverLevel().isLoaded(this.pos)) return;
        if (!(player.serverLevel().getBlockEntity(this.pos) instanceof CafeManagerBlockEntity cafe)) return;
        if (!CafeAccess.checkManage(player, cafe)) return;
        String name = cafe.getWaiters().stream().filter(w -> w.id().equals(this.waiter)).map(CafeManagerBlockEntity.Waiter::name).findFirst().orElse("?");
        if (cafe.removeWaiter(this.waiter)) {
            ServerBoundAddWaiterPacket.feedback(player, Component.translatable("message.cozyrestaurant.waiters.removed", name), true);
        }
    }
}
