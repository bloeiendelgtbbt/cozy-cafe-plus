package io.github.chakyl.cozycafe.compat;

import dev.slarrties.privit.server.region.Region;
import dev.slarrties.privit.server.world.DimensionContext;
import dev.slarrties.privit.server.world.WorldRegistry;
import io.github.chakyl.cozycafe.CozyCafe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;

/**
 * Access to Privit claims. Privit has no public API, so this goes through its server classes and fails soft:
 * if Privit changes, cafe areas just stop resolving instead of crashing the server.
 * Note: Privit only runs its region system on a dedicated server.
 */
public final class PrivitCompat {
    private static boolean failed = false;

    private PrivitCompat() {
    }

    public record ClaimInfo(java.util.UUID id, String name, BoundingBox bounds) {
    }

    public static Optional<ClaimInfo> claimAt(ServerLevel level, BlockPos pos) {
        if (failed) return Optional.empty();
        try {
            DimensionContext context = WorldRegistry.get(level);
            if (context == null || context.getRegionManager() == null) return Optional.empty();
            Optional<Region> region = context.getRegionManager().getAt(pos);
            return region.map(r -> new ClaimInfo(r.id(), r.name(), r.bounds()));
        } catch (Throwable t) {
            failed = true;
            CozyCafe.LOGGER.error("[COZYRESTAURANT] Privit integration failed, cafe areas from claims are disabled", t);
            return Optional.empty();
        }
    }
}
