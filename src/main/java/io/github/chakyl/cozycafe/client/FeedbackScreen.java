package io.github.chakyl.cozycafe.client;

import net.minecraft.network.chat.Component;

/** A cafe screen that shows the server's answer (success or the reason for a refusal) itself. */
public interface FeedbackScreen {
    void showFeedback(Component message);
}
