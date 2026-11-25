package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.data.CafeMenuEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Opens "Мой заказ" for a player guest: what was served, what is still coming, the countdown. */
public class ClientBoundOrderStatusPacket implements EvilPacketsIHateThem.ClientHandled {
    public final BlockPos tablePos;
    public final String cafeName;
    public final ItemStack currency;
    public final List<CafeMenuEntry> served;
    public final List<CafeMenuEntry> pending;
    public final CafeMenuBlockEntity.Phase phase;
    /** Ticks left on the countdown, or -1. */
    public final int ticksLeft;
    /** Part of the order lies on the table to be taken. */
    public final boolean foodOnTable;

    public ClientBoundOrderStatusPacket(BlockPos tablePos, String cafeName, ItemStack currency, List<CafeMenuEntry> served,
                                        List<CafeMenuEntry> pending, CafeMenuBlockEntity.Phase phase, int ticksLeft, boolean foodOnTable) {
        this.tablePos = tablePos;
        this.cafeName = cafeName;
        this.currency = currency;
        this.served = List.copyOf(served);
        this.pending = List.copyOf(pending);
        this.phase = phase;
        this.ticksLeft = ticksLeft;
        this.foodOnTable = foodOnTable;
    }

    public ClientBoundOrderStatusPacket(FriendlyByteBuf buffer) {
        this.tablePos = buffer.readBlockPos();
        this.cafeName = buffer.readUtf();
        this.currency = buffer.readItem();
        this.served = readLines(buffer);
        this.pending = readLines(buffer);
        this.phase = buffer.readEnum(CafeMenuBlockEntity.Phase.class);
        this.ticksLeft = buffer.readVarInt();
        this.foodOnTable = buffer.readBoolean();
    }

    private static List<CafeMenuEntry> readLines(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<CafeMenuEntry> lines = new ArrayList<>();
        for (int i = 0; i < size; i++) lines.add(new CafeMenuEntry(buffer.readItem(), buffer.readVarInt()));
        return lines;
    }

    private static void writeLines(FriendlyByteBuf buffer, List<CafeMenuEntry> lines) {
        buffer.writeVarInt(lines.size());
        for (CafeMenuEntry line : lines) {
            buffer.writeItem(line.item());
            buffer.writeVarInt(line.price());
        }
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.tablePos);
        buffer.writeUtf(this.cafeName);
        buffer.writeItem(this.currency);
        writeLines(buffer, this.served);
        writeLines(buffer, this.pending);
        buffer.writeEnum(this.phase);
        buffer.writeVarInt(this.ticksLeft);
        buffer.writeBoolean(this.foodOnTable);
    }

    @Override
    public void handleOnClient() {
        io.github.chakyl.cozycafe.client.ClientHooks.openOrderStatus(this);
    }
}
