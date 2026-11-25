package io.github.chakyl.cozycafe.data;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * New cafes a player missed: when each cafe first opened, and when each player was last online. On joining, a player
 * gets the cafes that opened while they were away; a player joining for the first time gets every cafe of the server.
 */
public class CafeNews extends SavedData {
    /** At most this many cafes are listed; the rest is summed up as "and N more". */
    public static final int MAX_LISTED = 10;

    private record Opening(long time, @Nullable UUID owner) {
    }

    private final Map<UUID, Opening> openings = new HashMap<>();
    private final Map<UUID, Long> lastSeen = new HashMap<>();

    public static CafeNews get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CafeNews::load, CafeNews::new, "cozyrestaurant_news");
    }

    /** A cafe opened for the first time (it was announced to everyone online). */
    public static void cafeOpened(MinecraftServer server, UUID cafeId, @Nullable UUID owner) {
        CafeNews news = get(server);
        news.openings.put(cafeId, new Opening(System.currentTimeMillis(), owner));
        news.setDirty();
    }

    public static void playerLeft(MinecraftServer server, UUID player) {
        CafeNews news = get(server);
        news.lastSeen.put(player, System.currentTimeMillis());
        news.setDirty();
    }

    /** Tells a joining player about the cafes they missed, then remembers they are here now. */
    public static void playerJoined(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CafeNews news = get(server);
        Component message = news.missedCafes(player.getUUID(), CafeDirectory.get(server).all());
        news.lastSeen.put(player.getUUID(), System.currentTimeMillis());
        news.setDirty();
        if (message != null) player.sendSystemMessage(message);
    }

    /** The message for a joining player, or null when there is nothing new for them. */
    @Nullable
    public Component missedCafes(UUID player, List<CafeDirectory.Entry> cafes) {
        Long seen = this.lastSeen.get(player);
        List<CafeDirectory.Entry> missed = new ArrayList<>();
        for (CafeDirectory.Entry cafe : cafes) {
            if (seen == null) {
                missed.add(cafe);
                continue;
            }
            Opening opening = this.openings.get(cafe.id());
            if (opening != null && opening.time() > seen && !player.equals(opening.owner())) missed.add(cafe);
        }
        if (missed.isEmpty()) return null;
        MutableComponent message = Component.translatable(seen == null
                ? "message.cozyrestaurant.cafe.all_cafes" : "message.cozyrestaurant.cafe.missed").withStyle(ChatFormatting.WHITE);
        for (int i = 0; i < Math.min(MAX_LISTED, missed.size()); i++) {
            message.append("\n").append(line(missed.get(i)));
        }
        if (missed.size() > MAX_LISTED) {
            message.append("\n").append(Component.translatable("message.cozyrestaurant.cafe.and_more", missed.size() - MAX_LISTED).withStyle(ChatFormatting.GRAY));
        }
        return message;
    }

    private static Component line(CafeDirectory.Entry cafe) {
        MutableComponent line = Component.literal(" • ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("«" + cafe.name() + "»").withStyle(ChatFormatting.YELLOW))
                .append(" ");
        line.append(cafe.stars() < 0
                ? Component.translatable("waypoint.cozyrestaurant.no_ratings").withStyle(ChatFormatting.GRAY)
                : Component.literal("★".repeat(cafe.stars()) + "☆".repeat(5 - cafe.stars())).withStyle(ChatFormatting.GOLD));
        line.append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(cafe.pos().getX() + " " + cafe.pos().getY() + " " + cafe.pos().getZ()).withStyle(ChatFormatting.GOLD));
        if (!cafe.dimension().equals(Level.OVERWORLD.location())) {
            line.append(Component.literal(" (" + cafe.dimension().getPath() + ")").withStyle(ChatFormatting.GRAY));
        }
        return line;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag openingList = new ListTag();
        this.openings.forEach((cafe, opening) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("cafe", cafe);
            tag.putLong("time", opening.time());
            if (opening.owner() != null) tag.putUUID("owner", opening.owner());
            openingList.add(tag);
        });
        nbt.put("openings", openingList);
        ListTag seenList = new ListTag();
        this.lastSeen.forEach((player, time) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("player", player);
            tag.putLong("time", time);
            seenList.add(tag);
        });
        nbt.put("lastSeen", seenList);
        return nbt;
    }

    private static CafeNews load(CompoundTag nbt) {
        CafeNews news = new CafeNews();
        for (Tag t : nbt.getList("openings", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) t;
            news.openings.put(tag.getUUID("cafe"), new Opening(tag.getLong("time"), tag.hasUUID("owner") ? tag.getUUID("owner") : null));
        }
        for (Tag t : nbt.getList("lastSeen", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) t;
            news.lastSeen.put(tag.getUUID("player"), tag.getLong("time"));
        }
        return news;
    }
}
