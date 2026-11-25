package io.github.chakyl.cozycafe.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Result of a cafe management action, shown inside the open cafe screen (or in the action bar).
 */
public class ClientBoundCafeFeedbackPacket implements EvilPacketsIHateThem.ClientHandled {
    private final Component message;

    public ClientBoundCafeFeedbackPacket(Component message) {
        this.message = message;
    }

    public ClientBoundCafeFeedbackPacket(FriendlyByteBuf buffer) {
        this.message = buffer.readComponent();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeComponent(this.message);
    }

    @Override
    public void handleOnClient() {
        io.github.chakyl.cozycafe.client.ClientHooks.showCafeFeedback(this.message);
    }
}
