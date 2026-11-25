package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.util.CafeRole;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Full list of players that currently show a cafe role label (owner / waiter) above their name.
 */
public class ClientBoundRolesPacket implements EvilPacketsIHateThem.ClientHandled {
    private final Map<UUID, CafeRole> roles;

    public ClientBoundRolesPacket(Map<UUID, CafeRole> roles) {
        this.roles = roles;
    }

    public ClientBoundRolesPacket(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        this.roles = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID id = buffer.readUUID();
            CafeRole role = buffer.readEnum(CafeRole.class);
            this.roles.put(id, role);
        }
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.roles.size());
        this.roles.forEach((id, role) -> {
            buffer.writeUUID(id);
            buffer.writeEnum(role);
        });
    }

    @Override
    public void handleOnClient() {
        io.github.chakyl.cozycafe.client.ClientHooks.setRoles(this.roles);
    }
}
