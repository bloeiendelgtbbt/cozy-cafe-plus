package io.github.chakyl.cozycafe.util;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.compat.PrivitCompat;
import io.github.chakyl.cozycafe.data.CafeIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A cafe is a Privit claim with one Cafe Manager in it. Everything inside the claim (tables, staff) belongs to that cafe.
 */
public final class CafeLookup {
    /** How far above and below a table NPC spawn points are searched for on the claim border. */
    private static final int SPAWN_SEARCH_HEIGHT = 8;
    private static final int SPAWN_ATTEMPTS = 64;

    private CafeLookup() {
    }

    @Nullable
    public static CafeManagerBlockEntity cafeAt(ServerLevel level, BlockPos pos) {
        Optional<PrivitCompat.ClaimInfo> claim = PrivitCompat.claimAt(level, pos);
        if (claim.isEmpty()) return null;
        return cafeInClaim(level, claim.get());
    }

    @Nullable
    public static CafeManagerBlockEntity cafeInClaim(ServerLevel level, PrivitCompat.ClaimInfo claim) {
        for (BlockPos cafePos : managersInClaim(level, claim)) {
            if (level.isLoaded(cafePos) && level.getBlockEntity(cafePos) instanceof CafeManagerBlockEntity cafe) {
                return cafe;
            }
        }
        return null;
    }

    /** Positions of every Cafe Manager inside the claim, loaded or not. Stale entries of loaded chunks are dropped. */
    public static List<BlockPos> managersInClaim(ServerLevel level, PrivitCompat.ClaimInfo claim) {
        CafeIndex index = CafeIndex.get(level.getServer());
        List<BlockPos> result = new ArrayList<>();
        List<BlockPos> stale = new ArrayList<>();
        for (BlockPos cafePos : index.getCafes(level)) {
            if (!claim.bounds().isInside(cafePos)) continue;
            if (level.isLoaded(cafePos) && !(level.getBlockEntity(cafePos) instanceof CafeManagerBlockEntity)) {
                stale.add(cafePos);
                continue;
            }
            result.add(cafePos);
        }
        stale.forEach(pos -> index.remove(level, pos));
        return result;
    }

    /** Loaded tables (Cafe Menu blocks) inside the claim. */
    public static List<CafeMenuBlockEntity> tablesInClaim(ServerLevel level, PrivitCompat.ClaimInfo claim) {
        CafeIndex index = CafeIndex.tables(level.getServer());
        List<CafeMenuBlockEntity> result = new ArrayList<>();
        List<BlockPos> stale = new ArrayList<>();
        for (BlockPos tablePos : index.getCafes(level)) {
            if (!claim.bounds().isInside(tablePos) || !level.isLoaded(tablePos)) continue;
            if (level.getBlockEntity(tablePos) instanceof CafeMenuBlockEntity table) {
                result.add(table);
            } else {
                stale.add(tablePos);
            }
        }
        stale.forEach(pos -> index.remove(level, pos));
        return result;
    }

    /** The claim a Cafe Manager stands in. */
    public static Optional<PrivitCompat.ClaimInfo> claimOf(CafeManagerBlockEntity cafe) {
        if (!(cafe.getLevel() instanceof ServerLevel level)) return Optional.empty();
        return PrivitCompat.claimAt(level, cafe.getBlockPos());
    }

    /** True if the claim holds a Cafe Manager other than the one at {@code self}. */
    public static boolean hasOtherManager(ServerLevel level, PrivitCompat.ClaimInfo claim, BlockPos self) {
        for (BlockPos pos : managersInClaim(level, claim)) {
            if (!pos.equals(self)) return true;
        }
        return false;
    }

    /**
     * Why a Cafe Manager can't be placed at {@code pos}, or null if it can: it has to stand inside a Privit claim
     * that has no other Cafe Manager yet.
     */
    @Nullable
    public static Component placementError(ServerLevel level, BlockPos pos) {
        Optional<PrivitCompat.ClaimInfo> claim = PrivitCompat.claimAt(level, pos);
        if (claim.isEmpty()) {
            return Component.translatable("block.cozyrestaurant.cafe_manager.place_outside_claim").withStyle(ChatFormatting.RED);
        }
        if (hasOtherManager(level, claim.get(), pos)) {
            return Component.translatable("block.cozyrestaurant.cafe_manager.place_second_in_claim").withStyle(ChatFormatting.RED);
        }
        return null;
    }

    /**
     * A spot on the border of the claim where an NPC customer can stand, near the height of {@code near}.
     * Returns null if the border has no free spot (for example when it runs through solid walls).
     */
    @Nullable
    public static BlockPos findBorderSpawn(ServerLevel level, BoundingBox bounds, BlockPos near, RandomSource random) {
        int width = bounds.maxX() - bounds.minX();
        int depth = bounds.maxZ() - bounds.minZ();
        int perimeter = 2 * (width + depth);
        if (perimeter <= 0) return null;
        int minY = Math.max(bounds.minY(), near.getY() - SPAWN_SEARCH_HEIGHT);
        int maxY = Math.min(bounds.maxY() - 1, near.getY() + SPAWN_SEARCH_HEIGHT);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            int step = random.nextInt(perimeter);
            int x;
            int z;
            if (step < width) {
                x = bounds.minX() + step;
                z = bounds.minZ();
            } else if ((step -= width) < depth) {
                x = bounds.maxX();
                z = bounds.minZ() + step;
            } else if ((step -= depth) < width) {
                x = bounds.maxX() - step;
                z = bounds.maxZ();
            } else {
                step -= width;
                x = bounds.minX();
                z = bounds.maxZ() - step;
            }
            // Closest free height to the table first.
            for (int dy = 0; dy <= SPAWN_SEARCH_HEIGHT; dy++) {
                for (int sign : dy == 0 ? new int[]{1} : new int[]{1, -1}) {
                    int y = near.getY() + dy * sign;
                    if (y < minY || y > maxY) continue;
                    pos.set(x, y, z);
                    if (level.isLoaded(pos) && canStandAt(level, pos)) return pos.immutable();
                }
            }
        }
        return null;
    }

    private static boolean canStandAt(ServerLevel level, BlockPos feet) {
        BlockPos ground = feet.below();
        BlockState groundState = level.getBlockState(ground);
        if (!groundState.isFaceSturdy(level, ground, Direction.UP)) return false;
        return isFree(level, feet) && isFree(level, feet.above());
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}
