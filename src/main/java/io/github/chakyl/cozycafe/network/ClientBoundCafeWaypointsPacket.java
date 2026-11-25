package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.data.CafeDirectory;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/** The full list of cafes for the players' maps; sent on join and whenever a cafe changes. */
public class ClientBoundCafeWaypointsPacket implements EvilPacketsIHateThem.ClientHandled {
    private final List<CafeDirectory.Entry> cafes;

    public ClientBoundCafeWaypointsPacket(List<CafeDirectory.Entry> cafes) {
        this.cafes = List.copyOf(cafes);
    }

    public ClientBoundCafeWaypointsPacket(FriendlyByteBuf buffer) {
        this.cafes = CafeDirectory.readList(buffer);
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.cafes.size());
        for (CafeDirectory.Entry entry : this.cafes) entry.write(buffer);
    }

    @Override
    public void handleOnClient() {
        io.github.chakyl.cozycafe.client.CafeWaypoints.receive(this.cafes);
    }
}
