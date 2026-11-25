package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.data.CafeDebts;
import io.github.chakyl.cozycafe.util.CafeAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** The owner forgives a guest's whole debt to the cafe. */
public class ServerBoundForgiveDebtPacket implements EvilPacketsIHateThem.ServerHandled {
    private final BlockPos pos;
    private final UUID debtor;

    public ServerBoundForgiveDebtPacket(BlockPos pos, UUID debtor) {
        this.pos = pos;
        this.debtor = debtor;
    }

    public ServerBoundForgiveDebtPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.debtor = buffer.readUUID();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeUUID(this.debtor);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (!player.serverLevel().isLoaded(this.pos)) return;
        if (!(player.serverLevel().getBlockEntity(this.pos) instanceof CafeManagerBlockEntity cafe)) return;
        if (!CafeAccess.checkManage(player, cafe)) return;
        CafeDebts.Debt debt = cafe.getDebts().all().get(this.debtor);
        if (debt == null) return;
        String name = debt.name();
        cafe.forgiveDebt(this.debtor);
        CafeManagerBlockEntity.feedback(player, Component.translatable("message.cozyrestaurant.debt.forgiven", name), true);
        ServerPlayer guest = player.server.getPlayerList().getPlayer(this.debtor);
        if (guest != null) {
            guest.displayClientMessage(Component.translatable("message.cozyrestaurant.debt.forgiven_guest", cafe.getCafeName()).withStyle(ChatFormatting.GREEN), false);
        }
    }
}
