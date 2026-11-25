package io.github.chakyl.cozycafe.event;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.network.ClientBoundRolesPacket;
import io.github.chakyl.cozycafe.network.EvilPacketsIHateThem;
import io.github.chakyl.cozycafe.util.ApronRules;
import io.github.chakyl.cozycafe.util.CafeLookup;
import io.github.chakyl.cozycafe.util.CafeRole;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Works out which players show a role label and keeps every client up to date.
 * A label shows while the player's cafe is open and they stand inside its Privit claim:
 * the owner always, a waiter only when listed in the cafe and dressed in the apron.
 */
public final class RoleTracker {
    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static Map<UUID, CafeRole> currentRoles = new HashMap<>();

    private RoleTracker() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % UPDATE_INTERVAL_TICKS != 0) return;
        Map<UUID, CafeRole> roles = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CafeRole role = computeRole(player);
            if (role != null) roles.put(player.getUUID(), role);
        }
        if (!roles.equals(currentRoles)) {
            currentRoles = roles;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sendTo(player);
            }
        }
    }

    @Nullable
    public static CafeRole computeRole(ServerPlayer player) {
        if (player.isSpectator()) return null;
        CafeManagerBlockEntity cafe = CafeLookup.cafeAt(player.serverLevel(), player.blockPosition());
        if (cafe == null || !cafe.isOpen()) return null;
        if (cafe.isOwner(player.getUUID())) return CafeRole.OWNER;
        if (cafe.isWaiter(player.getUUID()) && ApronRules.isDressedAsWaiter(player)) return CafeRole.WAITER;
        return null;
    }

    public static Map<UUID, CafeRole> getCurrentRoles() {
        return currentRoles;
    }

    public static void sendTo(ServerPlayer player) {
        EvilPacketsIHateThem.sendToPlayer(new ClientBoundRolesPacket(new HashMap<>(currentRoles)), player);
    }

    public static void reset() {
        currentRoles = new HashMap<>();
    }
}
