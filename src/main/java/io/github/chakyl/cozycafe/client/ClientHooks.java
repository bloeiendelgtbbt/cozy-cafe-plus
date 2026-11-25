package io.github.chakyl.cozycafe.client;

import io.github.chakyl.cozycafe.util.CafeRole;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side state and handlers for packets, kept out of the packet classes so the dedicated server never loads them.
 */
@Environment(EnvType.CLIENT)
public final class ClientHooks {
    private static Map<UUID, CafeRole> roles = new HashMap<>();

    private ClientHooks() {
    }

    public static void setRoles(Map<UUID, CafeRole> newRoles) {
        roles = newRoles;
    }

    @Nullable
    public static CafeRole getRole(UUID playerId) {
        return roles.get(playerId);
    }

    public static void clearRoles() {
        roles = new HashMap<>();
    }

    public static void showCafeFeedback(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FeedbackScreen screen) {
            screen.showFeedback(message);
        } else if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }

    public static void openOrderScreen(net.minecraft.core.BlockPos tablePos, String cafeName, net.minecraft.world.item.ItemStack currency,
                                       java.util.List<io.github.chakyl.cozycafe.data.CafeMenuEntry> menu) {
        Minecraft.getInstance().setScreen(new io.github.chakyl.cozycafe.gui.OrderScreen(tablePos, cafeName, currency, menu));
    }

    public static void openOrderStatus(io.github.chakyl.cozycafe.network.ClientBoundOrderStatusPacket status) {
        Minecraft.getInstance().setScreen(new io.github.chakyl.cozycafe.gui.OrderStatusScreen(status));
    }
}
