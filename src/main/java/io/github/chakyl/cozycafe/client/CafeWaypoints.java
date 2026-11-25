package io.github.chakyl.cozycafe.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.chakyl.cozycafe.CozyCafe;
import io.github.chakyl.cozycafe.compat.XaeroCafeWaypoints;
import io.github.chakyl.cozycafe.data.CafeDirectory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The cafes the server told us about and which of them this player already has as waypoints. A waypoint the player
 * deleted is remembered as dismissed and never comes back (until {@code /cafes restore}). Cafe ids are unique
 * across servers, so one file serves them all.
 */
@Environment(EnvType.CLIENT)
public final class CafeWaypoints {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int RECONCILE_INTERVAL = 40;

    /** What we put into the player's map, per Xaero world (one per server dimension), so we can find it again. */
    public static final class Tracked {
        public int x;
        public int y;
        public int z;
        public String name;
        public int color;
    }

    private static final class Store {
        Set<UUID> dismissed = new HashSet<>();
        Map<String, Map<UUID, Tracked>> tracked = new HashMap<>();
    }

    private static List<CafeDirectory.Entry> cafes = List.of();
    private static Store store;
    private static boolean dirty;
    private static int timer;
    private static Boolean xaeroLoaded;

    private CafeWaypoints() {
    }

    public static void receive(List<CafeDirectory.Entry> list) {
        cafes = List.copyOf(list);
        timer = RECONCILE_INTERVAL; // reconcile on the next tick
    }

    public static void clear() {
        cafes = List.of();
    }

    public static boolean hasXaero() {
        if (xaeroLoaded == null) xaeroLoaded = FabricLoader.getInstance().isModLoaded("xaerominimap") || FabricLoader.getInstance().isModLoaded("xaerobetterpvp");
        return xaeroLoaded;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || !hasXaero()) return;
        if (++timer < RECONCILE_INTERVAL) return;
        timer = 0;
        try {
            XaeroCafeWaypoints.reconcile(cafes, minecraft.level.dimension().location());
        } catch (Throwable t) {
            // Xaero has no stable API: if it changed, waypoints just stop working instead of crashing the game.
            CozyCafe.LOGGER.error("[COZYRESTAURANT] Xaero waypoints failed, disabling them", t);
            xaeroLoaded = false;
        }
        if (dirty) save();
    }

    // ---- State used by the Xaero bridge ----

    public static boolean isDismissed(UUID cafe) {
        return store().dismissed.contains(cafe);
    }

    public static void dismiss(UUID cafe) {
        store().dismissed.add(cafe);
        dirty = true;
    }

    public static Map<UUID, Tracked> tracked(String world) {
        return store().tracked.computeIfAbsent(world, k -> new HashMap<>());
    }

    public static void markDirty() {
        dirty = true;
    }

    /** {@code /cafes restore}: every dismissed cafe comes back on the map. */
    public static Component restore() {
        if (!hasXaero()) return Component.translatable("message.cozyrestaurant.waypoints.no_xaero");
        int count = store().dismissed.size();
        store().dismissed.clear();
        dirty = true;
        save();
        timer = RECONCILE_INTERVAL;
        return count == 0 ? Component.translatable("message.cozyrestaurant.waypoints.nothing_to_restore")
                : Component.translatable("message.cozyrestaurant.waypoints.restored", count);
    }

    // ---- File ----

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("cozyrestaurant_waypoints.json");
    }

    private static Store store() {
        if (store == null) {
            store = new Store();
            Path file = file();
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    Store loaded = GSON.fromJson(reader, Store.class);
                    if (loaded != null) {
                        if (loaded.dismissed != null) store.dismissed.addAll(loaded.dismissed);
                        if (loaded.tracked != null) store.tracked.putAll(loaded.tracked);
                    }
                } catch (Exception e) {
                    CozyCafe.LOGGER.error("[COZYRESTAURANT] Could not read {}", file, e);
                }
            }
        }
        return store;
    }

    private static void save() {
        dirty = false;
        try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            GSON.toJson(store(), writer);
        } catch (Exception e) {
            CozyCafe.LOGGER.error("[COZYRESTAURANT] Could not save {}", file(), e);
        }
    }
}
