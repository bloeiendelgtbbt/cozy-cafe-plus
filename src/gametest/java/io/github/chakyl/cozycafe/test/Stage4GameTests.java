package io.github.chakyl.cozycafe.test;

import dev.slarrties.privit.server.region.Region;
import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.data.CafeDirectory;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

public class Stage4GameTests implements FabricGameTest {
    private static final BlockPos MANAGER = new BlockPos(1, 1, 1);

    private static CafeDirectory.Entry entry(GameTestHelper helper, UUID id) {
        return CafeDirectory.get(helper.getLevel().getServer()).all().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "cafeDirectory")
    public void cafeDirectoryFollowsTheCafe(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        owner.setGameMode(GameType.SURVIVAL);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        helper.setBlock(MANAGER, CozyRegistry.BlockRegistry.CAFE_MANAGER.get());
        CafeManagerBlockEntity cafe = (CafeManagerBlockEntity) helper.getBlockEntity(MANAGER);
        cafe.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        cafe.setCafeName("Тестовое кафе");
        UUID id = cafe.getCafeId();
        helper.assertTrue(entry(helper, id) == null, "a cafe that never opened is not on the map");

        cafe.addToMenu(new ItemStack(Items.BREAD));
        cafe.addToMenu(new ItemStack(Items.APPLE));
        cafe.addToMenu(new ItemStack(Items.CAKE));
        cafe.addToMenu(new ItemStack(Items.STONE));
        cafe.setCurrency(new ItemStack(Items.EMERALD));
        cafe.setNpcCustomers(false);
        helper.assertTrue(cafe.toggleOpenFromMenu() == null, "cafe opens");
        CafeDirectory.Entry opened = entry(helper, id);
        helper.assertTrue(opened != null && opened.open() && opened.stars() == -1 && opened.name().equals("Тестовое кафе")
                && opened.pos().equals(helper.absolutePos(MANAGER)), "listed on first opening, no ratings yet: " + opened);

        cafe.addRating(3);
        cafe.setCafeName("Новое имя");
        cafe.setOpen(false, true);
        CafeDirectory.Entry changed = entry(helper, id);
        helper.assertTrue(changed.stars() == cafe.getStars() && changed.name().equals("Новое имя") && !changed.open(), "name, stars and closed follow: " + changed);

        CafeManagerBlockEntity copy = new CafeManagerBlockEntity(cafe.getBlockPos(), cafe.getBlockState());
        copy.load(cafe.saveWithoutMetadata());
        helper.assertTrue(copy.getCafeId().equals(id) && copy.hasEverOpened() && copy.hasGuestRatings(), "id and flags survive save/load");

        helper.setBlock(MANAGER, Blocks.AIR);
        helper.assertTrue(entry(helper, id) == null, "a broken manager leaves the map");
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }
}
