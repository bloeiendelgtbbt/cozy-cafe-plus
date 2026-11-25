package io.github.chakyl.cozycafe.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persistent list of block positions per dimension: every Cafe Manager ({@link #get}) and every cafe table ({@link #tables}).
 * Lets the server find cafes and their tables inside a Privit claim without scanning chunks.
 */
public class CafeIndex extends SavedData {
    private final Map<String, Set<BlockPos>> cafesByDimension = new HashMap<>();

    /** All Cafe Manager positions. */
    public static CafeIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CafeIndex::load, CafeIndex::new, "cozyrestaurant_cafes");
    }

    /** All cafe table (Cafe Menu block) positions. */
    public static CafeIndex tables(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CafeIndex::load, CafeIndex::new, "cozyrestaurant_tables");
    }

    private static String key(Level level) {
        return level.dimension().location().toString();
    }

    public void add(ServerLevel level, BlockPos pos) {
        if (this.cafesByDimension.computeIfAbsent(key(level), k -> new LinkedHashSet<>()).add(pos.immutable())) {
            this.setDirty();
        }
    }

    public void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> cafes = this.cafesByDimension.get(key(level));
        if (cafes != null && cafes.remove(pos)) {
            this.setDirty();
        }
    }

    public Set<BlockPos> getCafes(Level level) {
        Set<BlockPos> cafes = this.cafesByDimension.get(key(level));
        return cafes == null ? Collections.emptySet() : Collections.unmodifiableSet(cafes);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (Map.Entry<String, Set<BlockPos>> entry : this.cafesByDimension.entrySet()) {
            ListTag positions = new ListTag();
            for (BlockPos pos : entry.getValue()) {
                positions.add(LongTag.valueOf(pos.asLong()));
            }
            tag.put(entry.getKey(), positions);
        }
        return tag;
    }

    private static CafeIndex load(CompoundTag tag) {
        CafeIndex index = new CafeIndex();
        for (String dimension : tag.getAllKeys()) {
            Set<BlockPos> positions = new LinkedHashSet<>();
            for (Tag entry : tag.getList(dimension, Tag.TAG_LONG)) {
                positions.add(BlockPos.of(((LongTag) entry).getAsLong()));
            }
            index.cafesByDimension.put(dimension, positions);
        }
        return index;
    }
}
