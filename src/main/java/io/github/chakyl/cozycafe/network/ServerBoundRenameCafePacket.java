package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;


public class ServerBoundRenameCafePacket implements EvilPacketsIHateThem.ServerHandled {
    private final BlockPos pos;
    private final String name;

    public ServerBoundRenameCafePacket(BlockPos pos, String name) {
        this.pos = pos;
        this.name = name;
    }

    public ServerBoundRenameCafePacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.name = buffer.readUtf();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeUtf(this.name);
    }

    @Override
    public void handle(ServerPlayer player) {
        {
            if (player != null) {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(this.pos)) {
                    BlockEntity cafeManager = level.getBlockEntity(this.pos);
                    if (cafeManager instanceof CafeManagerBlockEntity cafeManagerBlockEntity && io.github.chakyl.cozycafe.util.CafeAccess.checkManage(player, cafeManagerBlockEntity)) {
                        cafeManagerBlockEntity.setCafeName(this.name.length() > 32 ? this.name.substring(0, 32) : this.name);
                    }
                }
            }
        }
    }
}
