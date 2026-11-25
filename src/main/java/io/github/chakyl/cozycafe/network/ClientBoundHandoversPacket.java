package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.data.CafeHandovers;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** The player's own bills not yet handed in, per cafe; {@code open} also opens their personal tablet. */
public class ClientBoundHandoversPacket implements EvilPacketsIHateThem.ClientHandled {
    private final List<CafeHandovers.Summary> bills;
    private final boolean open;

    public ClientBoundHandoversPacket(List<CafeHandovers.Summary> bills, boolean open) {
        this.bills = List.copyOf(bills);
        this.open = open;
    }

    public ClientBoundHandoversPacket(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<CafeHandovers.Summary> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) list.add(CafeHandovers.Summary.read(buffer));
        this.bills = list;
        this.open = buffer.readBoolean();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.bills.size());
        for (CafeHandovers.Summary summary : this.bills) summary.write(buffer);
        buffer.writeBoolean(this.open);
    }

    @Override
    public void handleOnClient() {
        io.github.chakyl.cozycafe.client.ClientHandovers.receive(this.bills, this.open);
    }
}
