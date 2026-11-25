package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.data.CafeHandovers;
import io.github.chakyl.cozycafe.util.CafeAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** The owner forgives all bills a staff member has not handed in. */
public class ServerBoundForgiveHandoverPacket implements EvilPacketsIHateThem.ServerHandled {
    private final BlockPos pos;
    private final UUID holder;

    public ServerBoundForgiveHandoverPacket(BlockPos pos, UUID holder) {
        this.pos = pos;
        this.holder = holder;
    }

    public ServerBoundForgiveHandoverPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.holder = buffer.readUUID();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeUUID(this.holder);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (!player.serverLevel().isLoaded(this.pos)) return;
        if (!(player.serverLevel().getBlockEntity(this.pos) instanceof CafeManagerBlockEntity cafe)) return;
        if (!CafeAccess.checkManage(player, cafe)) return;
        String name = "";
        for (CafeHandovers.Summary summary : CafeHandovers.get(player.server).forCafe(cafe.getCafeId())) {
            if (summary.holder().equals(this.holder)) name = summary.holderName();
        }
        if (!CafeHandovers.get(player.server).forgive(player.server, cafe.getCafeId(), this.holder)) return;
        CafeManagerBlockEntity.feedback(player, Component.translatable("message.cozyrestaurant.handover.forgiven", name), true);
        ServerPlayer staff = player.server.getPlayerList().getPlayer(this.holder);
        if (staff != null && staff != player) {
            staff.displayClientMessage(Component.translatable("message.cozyrestaurant.handover.forgiven_staff", cafe.getCafeName()).withStyle(ChatFormatting.GREEN), false);
        }
    }
}
