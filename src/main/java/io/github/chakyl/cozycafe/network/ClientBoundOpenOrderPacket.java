package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.data.CafeMenuEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the order screen for a seated guest. The menu travels with the packet: the cafe manager may be far away
 * and not loaded on the guest's client.
 */
public class ClientBoundOpenOrderPacket implements EvilPacketsIHateThem.ClientHandled {
    private final BlockPos tablePos;
    private final String cafeName;
    private final ItemStack currency;
    private final List<CafeMenuEntry> menu;

    public ClientBoundOpenOrderPacket(BlockPos tablePos, String cafeName, ItemStack currency, List<CafeMenuEntry> menu) {
        this.tablePos = tablePos;
        this.cafeName = cafeName;
        this.currency = currency;
        this.menu = List.copyOf(menu);
    }

    public ClientBoundOpenOrderPacket(FriendlyByteBuf buffer) {
        this.tablePos = buffer.readBlockPos();
        this.cafeName = buffer.readUtf();
        this.currency = buffer.readItem();
        int size = buffer.readVarInt();
        List<CafeMenuEntry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) entries.add(new CafeMenuEntry(buffer.readItem(), buffer.readVarInt()));
        this.menu = entries;
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.tablePos);
        buffer.writeUtf(this.cafeName);
        buffer.writeItem(this.currency);
        buffer.writeVarInt(this.menu.size());
        for (CafeMenuEntry entry : this.menu) {
            buffer.writeItem(entry.item());
            buffer.writeVarInt(entry.price());
        }
    }

    @Override
    public void handleOnClient() {
        io.github.chakyl.cozycafe.client.ClientHooks.openOrderScreen(this.tablePos, this.cafeName, this.currency, this.menu);
    }
}
