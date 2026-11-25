package io.github.chakyl.cozycafe.compat;

import io.github.chakyl.cozycafe.CozyCafe;
import io.github.chakyl.cozycafe.client.CafeWaypoints;
import io.github.chakyl.cozycafe.data.CafeDirectory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cafes as ordinary waypoints in Xaero's Minimap (main set of the current dimension). Only touched when Xaero is
 * installed; Xaero has no API for this, so it goes through its classes and is switched off if they change.
 */
@Environment(EnvType.CLIENT)
public final class XaeroCafeWaypoints {
    private static final String SYMBOL = "☕";

    private XaeroCafeWaypoints() {
    }

    public static void reconcile(List<CafeDirectory.Entry> cafes, ResourceLocation dimension) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return;
        MinimapWorld world = session.getWorldManager().getCurrentWorld();
        // Xaero may still be working out which world/dimension we're in: wait rather than misread "deleted".
        if (world == null || world.getDimId() == null || !world.getDimId().location().equals(dimension)) return;
        WaypointSet mainSet = world.getWaypointSet(MinimapWorld.DEFAULT_SET);
        if (mainSet == null) return;

        Map<UUID, CafeWaypoints.Tracked> tracked = CafeWaypoints.tracked(world.getFullPath().toString());
        boolean changed = false;
        Set<UUID> present = new HashSet<>();
        for (CafeDirectory.Entry cafe : cafes) {
            if (!cafe.dimension().equals(dimension)) continue;
            present.add(cafe.id());
            if (CafeWaypoints.isDismissed(cafe.id())) continue;
            String name = nameOf(cafe);
            int color = (cafe.open() ? WaypointColor.GOLD : WaypointColor.GRAY).ordinal();
            int x = cafe.pos().getX(), y = cafe.pos().getY() + 1, z = cafe.pos().getZ();
            CafeWaypoints.Tracked known = tracked.get(cafe.id());
            if (known != null) {
                Waypoint waypoint = find(world, known);
                if (waypoint == null) {
                    // The player deleted (or reworked) our waypoint: leave this cafe alone for good.
                    CafeWaypoints.dismiss(cafe.id());
                    tracked.remove(cafe.id());
                    CafeWaypoints.markDirty();
                    continue;
                }
                if (waypoint.getX() != x || waypoint.getY() != y || waypoint.getZ() != z || !name.equals(waypoint.getName()) || waypoint.getWaypointColor().ordinal() != color) {
                    waypoint.setX(x);
                    waypoint.setY(y);
                    waypoint.setZ(z);
                    waypoint.setName(name);
                    waypoint.setColor(WaypointColor.values()[color].ordinal());
                    remember(known, x, y, z, name, color);
                    changed = true;
                }
            } else {
                Waypoint waypoint = new Waypoint(x, y, z, name, SYMBOL, WaypointColor.values()[color]);
                mainSet.add(waypoint);
                CafeWaypoints.Tracked fresh = new CafeWaypoints.Tracked();
                remember(fresh, x, y, z, name, color);
                tracked.put(cafe.id(), fresh);
                changed = true;
            }
        }
        // Cafes that are gone (manager broken): their waypoints go too.
        for (Iterator<Map.Entry<UUID, CafeWaypoints.Tracked>> it = tracked.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, CafeWaypoints.Tracked> entry = it.next();
            if (present.contains(entry.getKey())) continue;
            removeWaypoint(world, entry.getValue());
            it.remove();
            changed = true;
        }
        if (changed) {
            CafeWaypoints.markDirty();
            try {
                session.getWorldManagerIO().saveWorld(world);
            } catch (Exception e) {
                CozyCafe.LOGGER.error("[COZYRESTAURANT] Could not save Xaero waypoints", e);
            }
        }
    }

    /** "Кафе у реки ★★★★☆", "(нет оценок)" instead of stars, "(закрыто)" while closed. */
    static String nameOf(CafeDirectory.Entry cafe) {
        StringBuilder name = new StringBuilder(cafe.name()).append(' ');
        if (cafe.stars() < 0) {
            name.append(Component.translatable("waypoint.cozyrestaurant.no_ratings").getString());
        } else {
            for (int i = 1; i <= 5; i++) name.append(i <= cafe.stars() ? '★' : '☆');
        }
        if (!cafe.open()) name.append(' ').append(Component.translatable("waypoint.cozyrestaurant.closed").getString());
        return name.toString();
    }

    private static void remember(CafeWaypoints.Tracked tracked, int x, int y, int z, String name, int color) {
        tracked.x = x;
        tracked.y = y;
        tracked.z = z;
        tracked.name = name;
        tracked.color = color;
    }

    /** Our waypoint, or null if the player deleted it. Xaero's "Delete" first only makes a waypoint temporary. */
    private static Waypoint find(MinimapWorld world, CafeWaypoints.Tracked tracked) {
        for (WaypointSet set : world.getIterableWaypointSets()) {
            for (Waypoint waypoint : set.getWaypoints()) {
                if (waypoint.isTemporary()) continue;
                if (waypoint.getX() == tracked.x && waypoint.getY() == tracked.y && waypoint.getZ() == tracked.z && waypoint.getName().equals(tracked.name)) {
                    return waypoint;
                }
            }
        }
        return null;
    }

    private static void removeWaypoint(MinimapWorld world, CafeWaypoints.Tracked tracked) {
        for (WaypointSet set : world.getIterableWaypointSets()) {
            for (Waypoint waypoint : set.getWaypoints()) {
                if (waypoint.getX() == tracked.x && waypoint.getY() == tracked.y && waypoint.getZ() == tracked.z && waypoint.getName().equals(tracked.name)) {
                    set.remove(waypoint);
                    return;
                }
            }
        }
    }
}
