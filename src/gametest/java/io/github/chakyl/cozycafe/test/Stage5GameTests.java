package io.github.chakyl.cozycafe.test;

import dev.slarrties.privit.common.region.rule.Rule;
import dev.slarrties.privit.server.region.Region;
import dev.slarrties.privit.server.region.protection.RegionPermissionChecker;
import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity.Phase;
import io.github.chakyl.cozycafe.blockentities.PlatingStationBlockEntity;
import io.github.chakyl.cozycafe.blocks.CafeMenuBlock;
import io.github.chakyl.cozycafe.blocks.CafeSignBlock;
import io.github.chakyl.cozycafe.blockentities.CafeSignBlockEntity;
import io.github.chakyl.cozycafe.data.CafeBill;
import io.github.chakyl.cozycafe.data.CafeShift;
import io.github.chakyl.cozycafe.item.ServingPlateItem;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import io.github.chakyl.cozycafe.entities.CustomerEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;
import java.util.UUID;

/** The order rework: parts, cancelling, leaving, food thrown away, the bill deadline, the shift report, plates and doors. */
public class Stage5GameTests implements FabricGameTest {
    private static final BlockPos MANAGER = new BlockPos(1, 1, 1);
    private static final BlockPos TABLE = new BlockPos(4, 1, 4);
    private static final BlockPos STATION = new BlockPos(2, 1, 4);

    /** An open cafe paying in emeralds: bread 2, apple 3, cake 1, stone 1. */
    private static CafeManagerBlockEntity openCafe(GameTestHelper helper, ServerPlayer owner) {
        helper.setBlock(MANAGER, CozyRegistry.BlockRegistry.CAFE_MANAGER.get());
        CafeManagerBlockEntity cafe = (CafeManagerBlockEntity) helper.getBlockEntity(MANAGER);
        cafe.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        cafe.addToMenu(new ItemStack(Items.BREAD));
        cafe.addToMenu(new ItemStack(Items.APPLE));
        cafe.addToMenu(new ItemStack(Items.CAKE));
        cafe.addToMenu(new ItemStack(Items.STONE));
        cafe.setCurrency(new ItemStack(Items.EMERALD));
        cafe.setPrice(0, 2);
        cafe.setPrice(1, 3);
        cafe.setNpcCustomers(false);
        helper.assertTrue(cafe.toggleOpenFromMenu() == null, "cafe opens");
        return cafe;
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static void seat(GameTestHelper helper, ServerPlayer player) {
        BlockPos at = helper.absolutePos(TABLE.east());
        Boat boat = EntityType.BOAT.create(helper.getLevel());
        boat.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.getLevel().addFreshEntity(boat);
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.startRiding(boat, true);
    }

    private static void walkAway(GameTestHelper helper, ServerPlayer player) {
        player.stopRiding();
        BlockPos far = helper.absolutePos(TABLE).offset(20, 0, 0);
        player.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
    }

    private static CafeMenuBlockEntity placeTable(GameTestHelper helper) {
        helper.setBlock(TABLE, CozyRegistry.BlockRegistry.CAFE_MENU.get());
        return (CafeMenuBlockEntity) helper.getBlockEntity(TABLE);
    }

    private static void tick(GameTestHelper helper, CafeMenuBlockEntity table, int times) {
        for (int i = 0; i < times; i++) table.tick(helper.getLevel(), table.getBlockPos(), table.getBlockState());
    }

    private static void dress(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CozyRegistry.ItemRegistry.APRON.get()));
    }

    private static void serve(CafeMenuBlockEntity table, ServerPlayer waiter, ItemStack... food) {
        waiter.setItemInHand(InteractionHand.MAIN_HAND, ServingPlateItem.withItems(new ItemStack(CozyRegistry.ItemRegistry.SERVING_PLATE.get()), List.of(food)));
        table.handleServe(waiter, waiter.getMainHandItem());
    }

    private static void issueBill(CafeMenuBlockEntity table, ServerPlayer waiter) {
        waiter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CozyRegistry.ItemRegistry.BILL.get()));
        table.handleIssueBill(waiter, waiter.getMainHandItem());
    }

    private static void finish(GameTestHelper helper, CafeManagerBlockEntity cafe, Region region, ServerPlayer owner) {
        cafe.setOpen(false, true);
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "partialServingAndCancel")
    public void partialServingAndCancel(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer guest = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        CafeMenuBlockEntity table = placeTable(helper);
        dress(owner);
        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.BREAD), new ItemStack(Items.APPLE), new ItemStack(Items.CAKE)), 10) == null, "order placed");

        // A first part: the apple.
        serve(table, owner, new ItemStack(Items.APPLE));
        helper.assertTrue(table.getPhase() == Phase.WAITING && table.getBlockState().getValue(CafeMenuBlock.DISH), "a part lies on the table, the rest is still coming");
        helper.assertTrue(table.getServedLines().size() == 1 && table.getPendingLines().size() == 2 && table.getOrder().size() == 2, "1 served, 2 pending");
        helper.assertTrue(!table.handleClearPlate(owner) && table.getBlockState().getValue(CafeMenuBlock.DISH), "the staff can't take the guest's food away");
        CafeMenuBlock.handleClick(table, guest, ItemStack.EMPTY);
        helper.assertTrue(guest.getInventory().countItem(Items.APPLE) == 1 && table.getBlockState().getValue(CafeMenuBlock.DIRTY), "the guest takes the apple");
        helper.assertTrue(table.getPhase() == Phase.WAITING, "still waiting for the rest");

        // The next part needs a clean table and only ordered dishes.
        serve(table, owner, new ItemStack(Items.BREAD));
        helper.assertTrue(table.getOrder().size() == 2, "not on a dirty table");
        table.handleClearPlate(owner);
        serve(table, owner, new ItemStack(Items.STONE));
        helper.assertTrue(table.getOrder().size() == 2 && !table.getBlockState().getValue(CafeMenuBlock.DISH), "not with a dish nobody ordered");
        serve(table, owner, new ItemStack(Items.APPLE));
        helper.assertTrue(table.getOrder().size() == 2, "not with a dish already served");
        serve(table, owner, new ItemStack(Items.BREAD));
        helper.assertTrue(table.getServedLines().size() == 2 && table.getOrder().size() == 1, "the bread came");

        // Cancelling now drops only the cake; the bread on the table is the guest's to take.
        table.cancelOrder(guest);
        helper.assertTrue(table.getOrder().isEmpty() && table.getPhase() == Phase.EATING, "the rest is cancelled, the bread waits");
        table.handleTakeFood(guest);
        helper.assertTrue(table.getPhase() == Phase.AFTER_MEAL && table.getOrderDeadline() > 0, "after the meal the bill deadline runs");
        table.handleClearPlate(owner);
        issueBill(table, owner);
        CafeBill bill = table.getBill();
        helper.assertTrue(bill != null && bill.getSum() == 5 && bill.getLines().size() == 2, "the bill holds the apple and the bread: 5, got " + (bill == null ? null : bill.getSum()));

        // Save/load keeps a visit in the middle.
        CafeMenuBlockEntity copy = new CafeMenuBlockEntity(table.getBlockPos(), table.getBlockState());
        copy.load(table.saveWithoutMetadata());
        helper.assertTrue(copy.getServedLines().size() == 2 && copy.getPhase() == Phase.BILL, "served lines survive save/load");
        bill.getPayment().setItem(0, new ItemStack(Items.EMERALD, 5));
        table.rateBill(guest, 5);
        table.handleCollectBill(owner);
        finish(helper, cafe, region, owner);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "timeoutAfterPartIsFree")
    public void timeoutAfterPartIsFree(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer guest = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        CafeMenuBlockEntity table = placeTable(helper);
        dress(owner);
        int ratings = cafe.getRatingCount();
        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.BREAD), new ItemStack(Items.CAKE)), 10) == null, "order placed");
        serve(table, owner, new ItemStack(Items.CAKE));
        table.expireDeadline();
        tick(helper, table, 10);
        helper.assertTrue(table.getOrder().isEmpty() && table.getPhase() == Phase.EATING && guest.getUUID().equals(table.getGuestPlayer()), "the rest is dropped, the cake waits");
        helper.assertTrue(cafe.getRatingCount() == ratings, "no 0 stars when something came");
        table.handleTakeFood(guest);
        table.handleClearPlate(owner);
        issueBill(table, owner);
        helper.assertTrue(table.getBill() != null && table.getBill().getSum() == 1, "only the cake is billed");
        table.getBill().getPayment().setItem(0, new ItemStack(Items.EMERALD, 1));
        table.rateBill(guest, 4);
        table.handleCollectBill(owner);
        finish(helper, cafe, region, owner);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "wastedFoodIsStillPaid")
    public void wastedFoodIsStillPaid(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer guest = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        CafeMenuBlockEntity table = placeTable(helper);
        dress(owner);
        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.BREAD)), 10) == null, "order placed");
        serve(table, owner, new ItemStack(Items.BREAD));
        table.expireFoodForTest();
        tick(helper, table, 10);
        helper.assertTrue(table.getBlockState().getValue(CafeMenuBlock.DIRTY) && !table.getBlockState().getValue(CafeMenuBlock.DISH), "the food is thrown away");
        helper.assertTrue(table.getPhase() == Phase.AFTER_MEAL && guest.getInventory().countItem(Items.BREAD) == 0, "the guest got nothing, the bill is awaited");
        walkAway(helper, guest);
        tick(helper, table, Stage3GameTests.awayLimit() + 20);
        helper.assertTrue(cafe.debtOf(guest.getUUID()) != null && cafe.debtOf(guest.getUUID()).getCount() == 2, "thrown away, still owed: 2");
        cafe.forgiveDebt(guest.getUUID());
        table.handleClearPlate(owner);
        finish(helper, cafe, region, owner);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "noBillInTimeIsOnTheHouse")
    public void noBillInTimeIsOnTheHouse(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer guest = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        CafeMenuBlockEntity table = placeTable(helper);
        dress(owner);
        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.APPLE)), 7) == null, "order placed");
        serve(table, owner, new ItemStack(Items.APPLE));
        table.handleTakeFood(guest);
        helper.assertTrue(table.getPhase() == Phase.AFTER_MEAL && table.getOrderDuration() == 7 * 1200, "the bill must come within the guest's 7 minutes");
        table.expireDeadline();
        tick(helper, table, 10);
        helper.assertTrue(table.getGuestPlayer() == null && !cafe.owes(guest.getUUID()), "no bill in time: on the house");
        table.handleClearPlate(owner);
        helper.assertTrue(table.isFree(), "the table is free");
        finish(helper, cafe, region, owner);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "shiftReport")
    public void shiftReport(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer guest = survivalPlayer(helper);
        ServerPlayer runner = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        CafeMenuBlockEntity table = placeTable(helper);
        dress(owner);

        // One guest pays 5 (plus a tip that doesn't count).
        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.BREAD), new ItemStack(Items.APPLE)), 10) == null, "order placed");
        serve(table, owner, new ItemStack(Items.BREAD), new ItemStack(Items.APPLE));
        table.handleTakeFood(guest);
        table.handleClearPlate(owner);
        issueBill(table, owner);
        table.getBill().getPayment().setItem(0, new ItemStack(Items.EMERALD, 6));
        table.rateBill(guest, 5);
        table.handleCollectBill(owner);

        // Another eats and runs away from the bill.
        seat(helper, runner);
        helper.assertTrue(table.placeOrder(runner, List.of(new ItemStack(Items.APPLE)), 10) == null, "second order");
        serve(table, owner, new ItemStack(Items.APPLE));
        table.handleTakeFood(runner);
        table.handleClearPlate(owner);
        issueBill(table, owner);
        walkAway(helper, runner);
        tick(helper, table, Stage3GameTests.awayLimit() + 20);
        helper.assertTrue(cafe.owes(runner.getUUID()), "the runner owes");

        CafeShift shift = cafe.getShift();
        helper.assertTrue(shift.guests() == 2, "two guests served, got " + shift.guests());
        helper.assertTrue(shift.earned().countOf(new ItemStack(Items.EMERALD)) == 5, "5 earned, got " + shift.earned().countOf(new ItemStack(Items.EMERALD)));
        helper.assertTrue(shift.debtBills() == 1 && shift.debts().countOf(new ItemStack(Items.EMERALD)) == 3, "one unpaid bill of 3");
        String report = shift.report(cafe.getCafeName()).getString();
        helper.assertTrue(report.contains("2") && report.contains("5") && report.contains("3"), "the report names the numbers: " + report);

        // Closing sends the report and starts over; reopening starts a new shift.
        cafe.setOpen(false, true);
        helper.assertTrue(cafe.getShift().guests() == 0 && cafe.getShift().earned().isEmpty(), "the shift is reset after the report");
        cafe.forgiveDebt(runner.getUUID());
        table.handleCollectBill(owner);
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "platesAreForStaff")
    public void platesAreForStaff(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer stranger = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        helper.setBlock(STATION, CozyRegistry.BlockRegistry.PLATING_STATION.get());
        PlatingStationBlockEntity station = (PlatingStationBlockEntity) helper.getBlockEntity(STATION);

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CozyRegistry.ItemRegistry.SERVING_PLATE.get()));
        station.handlePlating(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.getMainHandItem().isEmpty(), "the owner puts a plate on the station");
        stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult result = station.handlePlating(helper.getLevel(), stranger, InteractionHand.MAIN_HAND);
        helper.assertTrue(stranger.getMainHandItem().isEmpty() && result == InteractionResult.SUCCESS, "a stranger can't take it");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        station.handlePlating(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.getMainHandItem().is(CozyRegistry.ItemRegistry.SERVING_PLATE.get()), "the owner takes it back");
        finish(helper, cafe, region, owner);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "cafeDoorsAlwaysOpen")
    public void cafeDoorsAlwaysOpen(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        BlockPos door = helper.absolutePos(TABLE);
        UUID stranger = UUID.randomUUID();
        boolean trapdoorsBefore = RegionPermissionChecker.isAllowed(stranger, Rule.INTERACT_WITH_TRAPDOORS, door, helper.getLevel());
        helper.assertTrue(!RegionPermissionChecker.isAllowed(stranger, Rule.INTERACT_WITH_DOORS, door, helper.getLevel()), "without a cafe the claim keeps strangers' hands off its doors");
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        helper.assertTrue(RegionPermissionChecker.isAllowed(stranger, Rule.INTERACT_WITH_DOORS, door, helper.getLevel()), "doors open for anyone in a cafe");
        helper.assertTrue(RegionPermissionChecker.isAllowed(stranger, Rule.INTERACT_WITH_FENCE_GATES, door, helper.getLevel()), "gates too");
        helper.assertTrue(RegionPermissionChecker.isAllowed(stranger, Rule.INTERACT_WITH_TRAPDOORS, door, helper.getLevel()) == trapdoorsBefore, "trapdoors follow the claim");
        helper.assertTrue(!RegionPermissionChecker.isAllowed(stranger, Rule.BUILD, door, helper.getLevel()), "building is still protected");
        finish(helper, cafe, region, owner);
    }

    /** Like a villager, a guest climbs steps and opens a closed wooden door on the way (the only way in here). */
    @GameTest(template = EMPTY_STRUCTURE, batch = "npcClimbsStepsThroughDoor", timeoutTicks = 600)
    public void npcClimbsStepsThroughDoor(GameTestHelper helper) {
        for (int x = -20; x <= 9; x++) {
            for (int z = -4; z <= 11; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
                // Tall grass and flowers all over, as outside any cafe.
                if (x < 5 && (x + z) % 2 == 0) helper.setBlock(new BlockPos(x, 1, z), (x * 7 + z) % 3 == 0 ? Blocks.POPPY : Blocks.GRASS);
            }
        }
        // A wall at x = 5, six blocks high; the doorway sits two blocks up, reached by two stairs.
        for (int z = -4; z <= 11; z++) {
            for (int y = 1; y <= 6; y++) helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
        }
        // As on the porch in the test world: the steps run along the wall and the guest turns into the door at the top.
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(4, 1, 3), Blocks.COBBLESTONE);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(5, 3, 3), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(5, 4, 3), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // Behind the door: a landing two blocks above the ground.
        for (int z = 2; z <= 4; z++) {
            helper.setBlock(new BlockPos(6, 1, z), Blocks.STONE);
            helper.setBlock(new BlockPos(6, 2, z), Blocks.STONE);
        }
        CustomerEntity customer = helper.spawn(CozyRegistry.EntityRegistry.CUSTOMER.get(), new BlockPos(-18, 1, 3));
        // The sign goal walks to the block under the target and the guest leaves (is removed) once there.
        customer.setTargetSignPos(helper.absolutePos(new BlockPos(6, 4, 3)));
        helper.succeedWhen(() -> helper.assertTrue(customer.isRemoved(), "the guest has not got through the door yet"));
    }

    /** A sign hung anywhere in the cafe's claim follows the cafe without linking; one outside any cafe stays blank. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "signFollowsTheCafe", timeoutTicks = 120)
    public void signFollowsTheCafe(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        BlockPos signPos = new BlockPos(6, 2, 6);
        helper.setBlock(signPos, CozyRegistry.BlockRegistry.CAFE_SIGN.get());
        CafeSignBlockEntity sign = (CafeSignBlockEntity) helper.getBlockEntity(signPos);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(!sign.isInCafe(), "no cafe in the claim yet: the sign says nothing");
            CafeManagerBlockEntity cafe = openCafe(helper, owner);
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(sign.isInCafe(), "the claim's cafe is found without linking");
                helper.assertTrue(helper.getBlockState(signPos).getValue(CafeSignBlock.OPEN), "open cafe: OPEN");
                cafe.setOpen(false, true);
                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(!helper.getBlockState(signPos).getValue(CafeSignBlock.OPEN), "closed cafe: CLOSED");
                    finish(helper, cafe, region, owner);
                });
            });
        });
    }
}
