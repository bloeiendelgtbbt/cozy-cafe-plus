package io.github.chakyl.cozycafe.data;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.network.ClientBoundCafeWaypointsPacket;
import io.github.chakyl.cozycafe.network.EvilPacketsIHateThem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every cafe that has ever opened, with what players' maps need to show it (Xaero waypoints): where it is, its name,
 * stars and whether it's open. Kept by the server so cafes in unloaded chunks are known too; every change is sent
 * to all players.
 */
public class CafeDirectory extends SavedData {
    /** {@code stars} is -1 while the cafe has no ratings from guests yet. */
    public record Entry(UUID id, ResourceLocation dimension, BlockPos pos, String name, int stars, boolean open) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(this.id);
            buf.writeResourceLocation(this.dimension);
            buf.writeBlockPos(this.pos);
            buf.writeUtf(this.name);
            buf.writeByte(this.stars);
            buf.writeBoolean(this.open);
        }

        public static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readUUID(), buf.readResourceLocation(), buf.readBlockPos(), buf.readUtf(), buf.readByte(), buf.readBoolean());
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", this.id);
            tag.putString("dimension", this.dimension.toString());
            tag.put("pos", NbtUtils.writeBlockPos(this.pos));
            tag.putString("name", this.name);
            tag.putInt("stars", this.stars);
            tag.putBoolean("open", this.open);
            return tag;
        }

        static Entry load(CompoundTag tag) {
            return new Entry(tag.getUUID("id"), new ResourceLocation(tag.getString("dimension")), NbtUtils.readBlockPos(tag.getCompound("pos")),
                    tag.getString("name"), tag.getInt("stars"), tag.getBoolean("open"));
        }
    }

    private final Map<UUID, Entry> cafes = new LinkedHashMap<>();

    public static CafeDirectory get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CafeDirectory::load, CafeDirectory::new, "cozyrestaurant_directory");
    }

    public List<Entry> all() {
        return List.copyOf(this.cafes.values());
    }

    /** Brings the cafe's entry up to date; players are told only when something they see changed. */
    public static void update(CafeManagerBlockEntity cafe) {
        if (!(cafe.getLevel() instanceof ServerLevel level) || cafe.isRemoved() || !cafe.hasEverOpened()) return;
        Entry entry = new Entry(cafe.getCafeId(), level.dimension().location(), cafe.getBlockPos(), cafe.getCafeName(),
                cafe.hasGuestRatings() ? cafe.getStars() : -1, cafe.isOpen());
        CafeDirectory directory = get(level.getServer());
        if (entry.equals(directory.cafes.get(entry.id()))) return;
        directory.cafes.put(entry.id(), entry);
        directory.setDirty();
        directory.broadcast(level.getServer());
    }

    /** The manager was broken: its waypoint goes away. */
    public static void remove(ServerLevel level, UUID cafeId) {
        CafeDirectory directory = get(level.getServer());
        if (directory.cafes.remove(cafeId) != null) {
            directory.setDirty();
            directory.broadcast(level.getServer());
        }
    }

    private void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) this.sendTo(player);
    }

    public void sendTo(ServerPlayer player) {
        EvilPacketsIHateThem.sendToPlayer(new ClientBoundCafeWaypointsPacket(this.all()), player);
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (Entry entry : this.cafes.values()) list.add(entry.save());
        nbt.put("cafes", list);
        return nbt;
    }

    private static CafeDirectory load(CompoundTag nbt) {
        CafeDirectory directory = new CafeDirectory();
        for (Tag tag : nbt.getList("cafes", Tag.TAG_COMPOUND)) {
            Entry entry = Entry.load((CompoundTag) tag);
            directory.cafes.put(entry.id(), entry);
        }
        return directory;
    }

    /** For tests and the packet. */
    public static List<Entry> readList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(Entry.read(buf));
        return entries;
    }
}
