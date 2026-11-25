package io.github.chakyl.cozycafe.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shift reports for owners who were offline when their cafe closed; shown when they join. */
public class CafeReports extends SavedData {
    private final Map<UUID, List<String>> pending = new HashMap<>();

    public static CafeReports get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CafeReports::load, CafeReports::new, "cozyrestaurant_reports");
    }

    /** Tells the owner now, or keeps the report until they join. */
    public static void deliver(MinecraftServer server, UUID owner, Component report) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            player.sendSystemMessage(report);
            return;
        }
        CafeReports reports = get(server);
        reports.pending.computeIfAbsent(owner, id -> new ArrayList<>()).add(Component.Serializer.toJson(report));
        reports.setDirty();
    }

    public void sendPending(ServerPlayer player) {
        List<String> reports = this.pending.remove(player.getUUID());
        if (reports == null) return;
        this.setDirty();
        for (String json : reports) {
            Component report = Component.Serializer.fromJson(json);
            if (report != null) player.sendSystemMessage(report);
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag owners = new ListTag();
        this.pending.forEach((owner, reports) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("owner", owner);
            ListTag list = new ListTag();
            for (String json : reports) list.add(StringTag.valueOf(json));
            tag.put("reports", list);
            owners.add(tag);
        });
        nbt.put("owners", owners);
        return nbt;
    }

    private static CafeReports load(CompoundTag nbt) {
        CafeReports reports = new CafeReports();
        for (Tag t : nbt.getList("owners", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) t;
            if (!tag.hasUUID("owner")) continue;
            List<String> list = new ArrayList<>();
            for (Tag json : tag.getList("reports", Tag.TAG_STRING)) list.add(json.getAsString());
            if (!list.isEmpty()) reports.pending.put(tag.getUUID("owner"), list);
        }
        return reports;
    }
}
