package io.github.chakyl.cozycafe.test;

import dev.slarrties.privit.common.region.Color;
import dev.slarrties.privit.common.region.RegionGroups;
import dev.slarrties.privit.server.region.Region;
import dev.slarrties.privit.server.world.WorldRegistry;
import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.blockentities.PlatingStationBlockEntity;
import io.github.chakyl.cozycafe.blocks.CafeMenuBlock;
import io.github.chakyl.cozycafe.data.CafeMenuEntry;
import io.github.chakyl.cozycafe.item.ServingPlateItem;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import io.github.chakyl.cozycafe.util.CafeLookup;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Stage2GameTests implements FabricGameTest {

    private static CafeManagerBlockEntity placeManager(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, CozyRegistry.BlockRegistry.CAFE_MANAGER.get());
        return (CafeManagerBlockEntity) helper.getBlockEntity(rel);
    }

    private static void fillMenu(CafeManagerBlockEntity cafe, int count) {
        ItemStack[] items = {new ItemStack(Items.BREAD), new ItemStack(Items.APPLE), new ItemStack(Items.CAKE), new ItemStack(Items.STONE), new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.MILK_BUCKET)};
        for (int i = 0; i < count; i++) cafe.addToMenu(items[i]);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void menuAcceptsAnyItemWithPrices(GameTestHelper helper) {
        CafeManagerBlockEntity cafe = placeManager(helper, new BlockPos(1, 1, 1));
        helper.assertTrue(cafe.addToMenu(new ItemStack(Items.STONE)) == CafeManagerBlockEntity.AddResult.ADDED, "a block can be a menu position");
        helper.assertTrue(cafe.addToMenu(new ItemStack(Items.DIAMOND_SWORD)) == CafeManagerBlockEntity.AddResult.ADDED, "any item can be a menu position");
        helper.assertTrue(cafe.addToMenu(new ItemStack(Items.STONE, 5)) == CafeManagerBlockEntity.AddResult.DUPLICATE, "duplicates are refused");
        helper.assertTrue(cafe.getMenu().get(0).price() == CafeMenuEntry.MIN_PRICE, "new positions cost 1");

        cafe.setCurrency(new ItemStack(Items.EMERALD, 12));
        helper.assertTrue(cafe.getCurrency().getCount() == 1 && cafe.getCurrency().is(Items.EMERALD), "currency is one item");
        cafe.setPrice(0, 7);
        cafe.setPrice(1, 10_000);
        helper.assertTrue(cafe.getMenu().get(0).price() == 7, "price is set");
        helper.assertTrue(cafe.getMenu().get(1).price() == 5 * 64, "price is capped to 5 stacks of the currency");
        cafe.setCurrency(new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(cafe.getMenu().get(1).price() == 5, "an unstackable currency caps prices at 5");

        for (int i = 0; i < 60; i++) cafe.addToMenu(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(10 + i)));
        helper.assertTrue(cafe.getMenu().size() == CafeManagerBlockEntity.MAX_MENU_SIZE, "menu holds at most 50 positions, got " + cafe.getMenu().size());
        helper.assertTrue(cafe.addToMenu(new ItemStack(Items.NETHERITE_SCRAP)) == CafeManagerBlockEntity.AddResult.FULL, "a full menu refuses more");

        cafe.setNpcCustomers(false);
        cafe.addRating(0);
        cafe.deposit(new ItemStack(Items.EMERALD, 30));
        CafeManagerBlockEntity copy = new CafeManagerBlockEntity(cafe.getBlockPos(), cafe.getBlockState());
        copy.load(cafe.saveWithoutMetadata());
        helper.assertTrue(copy.getMenu().size() == 50 && copy.getMenu().get(0).price() == 5, "menu and prices survive save/load");
        helper.assertTrue(copy.getCurrency().is(Items.DIAMOND_SWORD), "currency survives save/load");
        helper.assertTrue(!copy.hasNpcCustomers(), "guest mode survives save/load");
        helper.assertTrue(copy.getRatingCount() == 2 && copy.getRevenue().countOf(new ItemStack(Items.EMERALD)) == 30, "rating and money survive save/load");

        // Breaking the manager keeps everything but the owner and the open state in the item.
        CompoundTag itemData = new CompoundTag();
        cafe.saveCafeData(itemData);
        helper.assertTrue(!itemData.contains("owner") && !itemData.contains("open") && itemData.contains("revenue"), "item data carries the cafe, not the owner");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void ratingStartsAtFiveStars(GameTestHelper helper) {
        CafeManagerBlockEntity cafe = placeManager(helper, new BlockPos(1, 1, 1));
        helper.assertTrue(cafe.getRatingCount() == 1 && cafe.getStars() == 5, "a new cafe has one 5-star rating");
        cafe.addRating(0);
        helper.assertTrue(cafe.getAverageRating() == 2.5 && cafe.getStars() == 3, "average 2.5 rounds to 3 stars");
        cafe.addRating(0);
        cafe.addRating(1);
        helper.assertTrue(cafe.getStars() == 2, "average 1.5 rounds to 2 stars, got " + cafe.getStars());
        helper.assertTrue(cafe.getSpawnIntervalTicks() == 13 * 1200L, "2 stars: an NPC visit every 13 minutes, got " + cafe.getSpawnIntervalTicks());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void revenueIsKeptUntilWithdrawn(GameTestHelper helper) {
        CafeManagerBlockEntity cafe = placeManager(helper, new BlockPos(1, 1, 1));
        cafe.deposit(new ItemStack(Items.EMERALD, 64));
        cafe.deposit(new ItemStack(Items.EMERALD, 66));
        cafe.deposit(new ItemStack(Items.NETHERITE_SCRAP, 1));
        helper.assertTrue(cafe.getRevenue().countOf(new ItemStack(Items.EMERALD)) == 130, "same items add up");
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        owner.setGameMode(net.minecraft.world.level.GameType.CREATIVE); // creative must not lose the overflow either
        owner.getInventory().clearContent();
        long given = cafe.withdrawRevenue(owner);
        helper.assertTrue(given == 131 && cafe.getRevenue().isEmpty(), "everything fits, got " + given);
        helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 130, "emeralds are in the inventory");

        // A full inventory: what doesn't fit stays in the cash register.
        cafe.getRevenue().deposit(new ItemStack(Items.DIAMOND), 64L * 40);
        owner.getInventory().clearContent();
        given = cafe.withdrawRevenue(owner);
        helper.assertTrue(given == 64L * 36, "36 slots of diamonds handed out, got " + given);
        helper.assertTrue(cafe.getRevenue().countOf(new ItemStack(Items.DIAMOND)) == 64L * 4, "the rest stays stored");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "cafeNeedsClaimCurrencyAndFourPositions")
    public void cafeNeedsClaimCurrencyAndFourPositions(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        CafeManagerBlockEntity cafe = placeManager(helper, rel);
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(rel).offset(0, 0, 3000);
        helper.assertTrue(io.github.chakyl.cozycafe.compat.PrivitCompat.claimAt(level, far).isEmpty() && CafeLookup.placementError(level, far) != null, "outside a claim a manager can't be placed");
        if (io.github.chakyl.cozycafe.compat.PrivitCompat.claimAt(level, helper.absolutePos(rel)).isEmpty()) {
            helper.assertTrue(cafe.openError() != null, "outside a claim the cafe can't open");
        }

        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        Region region = TestClaims.create(helper, rel, 4, owner);
        helper.assertTrue(CafeLookup.placementError(level, helper.absolutePos(rel)) == null, "the claim's own manager is fine");
        helper.assertTrue(CafeLookup.placementError(level, helper.absolutePos(rel.east())) != null, "a second manager in the claim is refused");

        fillMenu(cafe, 4);
        helper.assertTrue(cafe.toggleOpenFromMenu() != null && !cafe.isOpen(), "no currency: can't open");
        cafe.setCurrency(new ItemStack(Items.EMERALD));
        cafe.removeFromMenu(0);
        helper.assertTrue(cafe.toggleOpenFromMenu() != null && !cafe.isOpen(), "3 positions: can't open");
        cafe.addToMenu(new ItemStack(Items.BREAD));
        helper.assertTrue(cafe.toggleOpenFromMenu() == null && cafe.isOpen(), "claim + currency + 4 positions: opens");
        helper.assertTrue(cafe.toggleOpenFromMenu() == null && !cafe.isOpen(), "and closes again");

        // A second manager that ended up in the same claim blocks opening.
        helper.setBlock(rel.east(2), CozyRegistry.BlockRegistry.CAFE_MANAGER.get());
        helper.assertTrue(cafe.toggleOpenFromMenu() != null && !cafe.isOpen(), "two managers in one claim: can't open");
        helper.setBlock(rel.east(2), Blocks.AIR);
        helper.assertTrue(cafe.toggleOpenFromMenu() == null && cafe.isOpen(), "opens once the second manager is gone");
        cafe.setOpen(false, true);
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "npcSpawnsOnClaimBorder")
    public void npcSpawnsOnClaimBorder(GameTestHelper helper) {
        BlockPos rel = new BlockPos(4, 1, 4);
        for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
            helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
        }
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        Region region = TestClaims.create(helper, rel, 3, owner);
        BoundingBox bounds = region.bounds();
        BlockPos spawn = CafeLookup.findBorderSpawn(helper.getLevel(), bounds, helper.absolutePos(rel), helper.getLevel().random);
        helper.assertTrue(spawn != null, "a free border spot is found");
        boolean onBorder = spawn.getX() == bounds.minX() || spawn.getX() == bounds.maxX() || spawn.getZ() == bounds.minZ() || spawn.getZ() == bounds.maxZ();
        helper.assertTrue(onBorder && bounds.isInside(spawn), "spawn " + spawn + " lies on the border of " + bounds);
        helper.assertTrue(helper.getLevel().getBlockState(spawn.below()).is(Blocks.STONE) && helper.getLevel().getBlockState(spawn).isAir(), "spawn stands on the floor");
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "npcOrdersAndIsServedOnOnePlate", timeoutTicks = 200)
    public void npcOrdersAndIsServedOnOnePlate(GameTestHelper helper) {
        BlockPos managerRel = new BlockPos(1, 1, 1);
        BlockPos tableRel = new BlockPos(3, 1, 3);
        CafeManagerBlockEntity cafe = placeManager(helper, managerRel);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        owner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        stranger.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        cafe.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        Region region = TestClaims.create(helper, managerRel, 5, owner);
        fillMenu(cafe, 5);
        cafe.setCurrency(new ItemStack(Items.EMERALD));
        cafe.setNpcCustomers(false); // no random NPC groups during the test
        helper.assertTrue(cafe.toggleOpenFromMenu() == null, "cafe opens");

        helper.setBlock(tableRel, CozyRegistry.BlockRegistry.CAFE_MENU.get());
        CafeMenuBlockEntity table = (CafeMenuBlockEntity) helper.getBlockEntity(tableRel);
        helper.assertTrue(CafeLookup.tablesInClaim(helper.getLevel(), io.github.chakyl.cozycafe.compat.PrivitCompat.claimAt(helper.getLevel(), helper.absolutePos(tableRel)).orElseThrow()).contains(table), "table belongs to the claim");
        table.startVisit(cafe.getBlockPos(), "MHF_Steve", null);
        helper.assertTrue(table.getHasCustomer() && !table.canReceiveNewCustomer(), "customer sits down");
        for (int i = 0; i < 400 && !table.hasOrder(); i++) table.tick(helper.getLevel(), table.getBlockPos(), table.getBlockState());
        List<ItemStack> order = new ArrayList<>(table.getOrder());
        helper.assertTrue(order.size() >= 1 && order.size() <= 3, "NPC orders 1-3 positions, got " + order.size());
        for (ItemStack item : order) helper.assertTrue(cafe.priceOf(item) > 0, "ordered " + item + " is on the menu");

        ItemStack plate = ServingPlateItem.withItems(new ItemStack(CozyRegistry.ItemRegistry.SERVING_PLATE.get()), order);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, plate.copy());
        table.handleServe(stranger, stranger.getMainHandItem());
        helper.assertTrue(table.hasOrder(), "a stranger can't serve");

        owner.setItemInHand(InteractionHand.MAIN_HAND, plate.copy());
        table.handleServe(owner, owner.getMainHandItem());
        helper.assertTrue(table.hasOrder(), "the owner without the apron can't serve");

        owner.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CozyRegistry.ItemRegistry.APRON.get()));
        List<ItemStack> wrong = new ArrayList<>(order);
        wrong.add(new ItemStack(Items.STONE));
        owner.setItemInHand(InteractionHand.MAIN_HAND, ServingPlateItem.withItems(plate, wrong));
        table.handleServe(owner, owner.getMainHandItem());
        helper.assertTrue(table.hasOrder() && !owner.getMainHandItem().isEmpty(), "a plate that isn't the order is refused");

        List<ItemStack> reversed = new ArrayList<>(order);
        java.util.Collections.reverse(reversed);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ServingPlateItem.withItems(plate, reversed));
        table.handleServe(owner, owner.getMainHandItem());
        helper.assertTrue(!table.hasOrder() && table.getEating().size() == order.size(), "the whole order is served on one plate");
        helper.assertTrue(table.getBlockState().getValue(CafeMenuBlock.DISH), "the plate is on the table");

        for (int i = 0; i < 400 && table.getPhase() == CafeMenuBlockEntity.Phase.EATING; i++) table.tick(helper.getLevel(), table.getBlockPos(), table.getBlockState());
        helper.assertTrue(table.getPhase() == CafeMenuBlockEntity.Phase.AFTER_MEAL && table.getBlockState().getValue(CafeMenuBlock.DIRTY), "after eating a dirty plate is left");
        helper.assertTrue(!table.canReceiveNewCustomer(), "a dirty table gets no new customer");
        owner.getInventory().clearContent();
        owner.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CozyRegistry.ItemRegistry.APRON.get()));
        table.handleClearPlate(owner);
        helper.assertTrue(owner.getInventory().countItem(CozyRegistry.ItemRegistry.DIRTY_SERVING_PLATE.get()) == 1, "staff take the dirty plate");
        // Without a bill the NPC leaves unpaid once its patience runs out.
        table.expireDeadline();
        table.tick(helper.getLevel(), table.getBlockPos(), table.getBlockState());
        helper.assertTrue(!table.getHasCustomer() && table.getBill() == null, "the NPC leaves without a bill");
        helper.assertTrue(table.canReceiveNewCustomer(), "clean table is free again");
        cafe.setOpen(false, true);
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void platingStationBuildsOnePlate(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, CozyRegistry.BlockRegistry.PLATING_STATION.get());
        PlatingStationBlockEntity station = (PlatingStationBlockEntity) helper.getBlockEntity(rel);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CozyRegistry.ItemRegistry.SERVING_PLATE.get(), 2));
        station.handlePlating(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "one plate goes on the station");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 10));
        for (int i = 0; i < 8; i++) station.handlePlating(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(ServingPlateItem.getItems(station.getPlateItem()).size() == ServingPlateItem.CAPACITY, "a plate holds 6 items");
        helper.assertTrue(player.getMainHandItem().getCount() == 10 - ServingPlateItem.CAPACITY, "only 6 were taken from the hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        station.handlePlating(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().is(Items.STONE) && ServingPlateItem.getItems(station.getPlateItem()).size() == 5, "shift + empty hand takes the last item back");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        station.handlePlating(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(ServingPlateItem.getItems(player.getMainHandItem()).size() == 5 && station.getPlateItem().isEmpty(), "empty hand takes the plate");

        helper.assertTrue(ServingPlateItem.matchesOrder(List.of(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD)), List.of(new ItemStack(Items.BREAD), new ItemStack(Items.APPLE))), "order of items on the plate doesn't matter");
        helper.assertTrue(!ServingPlateItem.matchesOrder(List.of(new ItemStack(Items.APPLE), new ItemStack(Items.APPLE)), List.of(new ItemStack(Items.BREAD), new ItemStack(Items.APPLE))), "but every ordered item must be there");
        helper.succeed();
    }
}
