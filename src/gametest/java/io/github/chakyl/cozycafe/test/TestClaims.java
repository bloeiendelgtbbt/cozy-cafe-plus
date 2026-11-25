package io.github.chakyl.cozycafe.test;

import dev.slarrties.privit.common.region.Color;
import dev.slarrties.privit.common.region.RegionGroups;
import dev.slarrties.privit.server.region.Region;
import dev.slarrties.privit.server.world.WorldRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.UUID;

/** Temporary Privit claims for tests. Claims outlive a test, so every test deletes its claim before it ends. */
final class TestClaims {
    private TestClaims() {
    }

    static Region create(GameTestHelper helper, BlockPos rel, int radius, ServerPlayer owner) {
        BlockPos abs = helper.absolutePos(rel);
        BoundingBox bounds = new BoundingBox(abs.getX() - radius, abs.getY() - 2, abs.getZ() - radius, abs.getX() + radius, abs.getY() + 4, abs.getZ() + radius);
        Region region = new Region(UUID.randomUUID(), "test cafe", bounds, abs, Color.GOLD, RegionGroups.create(owner.getUUID()));
        WorldRegistry.get(helper.getLevel()).getRegionManager().loadRegion(region);
        return region;
    }

    static void delete(GameTestHelper helper, Region region, ServerPlayer owner) {
        WorldRegistry.get(helper.getLevel()).getRegionManager().tryDelete(region.id(), owner);
        helper.assertTrue(WorldRegistry.get(helper.getLevel()).getRegionManager().getById(region.id()).isEmpty(), "test claim must be deleted");
    }
}
