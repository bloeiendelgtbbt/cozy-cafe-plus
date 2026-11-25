package io.github.chakyl.cozycafe.test;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeSignBlockEntity;
import io.github.chakyl.cozycafe.blockentities.PlatingStationBlockEntity;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class CozyCafeGameTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void blocksHaveBlockEntities(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 0), CozyRegistry.BlockRegistry.CAFE_MANAGER.get());
        helper.setBlock(new BlockPos(1, 1, 0), CozyRegistry.BlockRegistry.CAFE_MENU.get());
        helper.setBlock(new BlockPos(2, 1, 0), CozyRegistry.BlockRegistry.CAFE_SIGN.get());
        helper.setBlock(new BlockPos(3, 1, 0), CozyRegistry.BlockRegistry.PLATING_STATION.get());
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockEntity(new BlockPos(0, 1, 0)) instanceof CafeManagerBlockEntity, "cafe manager BE");
            helper.assertTrue(helper.getBlockEntity(new BlockPos(1, 1, 0)) instanceof CafeMenuBlockEntity, "cafe menu BE");
            helper.assertTrue(helper.getBlockEntity(new BlockPos(2, 1, 0)) instanceof CafeSignBlockEntity, "cafe sign BE");
            helper.assertTrue(helper.getBlockEntity(new BlockPos(3, 1, 0)) instanceof PlatingStationBlockEntity, "plating station BE");
        });
    }
}
