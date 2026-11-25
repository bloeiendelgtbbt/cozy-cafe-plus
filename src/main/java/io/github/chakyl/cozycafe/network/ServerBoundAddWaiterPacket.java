package io.github.chakyl.cozycafe.network;

import com.mojang.authlib.GameProfile;
import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.util.CafeAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;

/**
 * Owner adds a waiter by nickname. The name is resolved through online players and the server's
 * user cache (usercache.json), and stored as a UUID so later nickname changes don't matter.
 */
public class ServerBoundAddWaiterPacket implements EvilPacketsIHateThem.ServerHandled {
    private static final int MAX_NAME_LENGTH = 16;
    private final BlockPos pos;
    private final String name;

    public ServerBoundAddWaiterPacket(BlockPos pos, String name) {
        this.pos = pos;
        this.name = name;
    }

    public ServerBoundAddWaiterPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.name = buffer.readUtf(MAX_NAME_LENGTH * 4);
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeUtf(this.name, MAX_NAME_LENGTH * 4);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (!player.serverLevel().isLoaded(this.pos)) return;
        if (!(player.serverLevel().getBlockEntity(this.pos) instanceof CafeManagerBlockEntity cafe)) return;
        if (!CafeAccess.checkManage(player, cafe)) return;

        String trimmed = this.name.trim();
        Optional<GameProfile> profile = trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH ? Optional.empty() : findProfile(player.getServer(), trimmed);
        if (profile.isEmpty()) {
            feedback(player, Component.translatable("message.cozyrestaurant.waiters.not_found"), false);
            return;
        }
        GameProfile found = profile.get();
        if (cafe.isOwner(found.getId())) {
            feedback(player, Component.translatable("message.cozyrestaurant.waiters.is_owner"), false);
        } else if (cafe.isWaiter(found.getId())) {
            feedback(player, Component.translatable("message.cozyrestaurant.waiters.already", found.getName()), false);
        } else if (cafe.getWaiters().size() >= CafeManagerBlockEntity.MAX_WAITERS) {
            feedback(player, Component.translatable("message.cozyrestaurant.waiters.full", CafeManagerBlockEntity.MAX_WAITERS), false);
        } else {
            cafe.addWaiter(found.getId(), found.getName());
            feedback(player, Component.translatable("message.cozyrestaurant.waiters.added", found.getName()), true);
        }
    }

    private static Optional<GameProfile> findProfile(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return Optional.of(online.getGameProfile());
        if (server.getProfileCache() == null) return Optional.empty();
        // Only players who have joined this server before (usercache.json). GameProfileCache.get(name) is not
        // used on purpose: for unknown names it asks Mojang (online mode) or invents an offline profile.
        GameProfileCache.GameProfileInfo info = server.getProfileCache().profilesByName.get(name.toLowerCase(Locale.ROOT));
        return info == null ? Optional.empty() : Optional.of(info.getProfile());
    }

    public static void feedback(ServerPlayer player, Component message, boolean success) {
        EvilPacketsIHateThem.sendToPlayer(new ClientBoundCafeFeedbackPacket(message.copy().withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED)), player);
    }
}
