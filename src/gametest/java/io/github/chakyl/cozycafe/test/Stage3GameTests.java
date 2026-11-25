package io.github.chakyl.cozycafe.test;

import dev.slarrties.privit.server.region.Region;
import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity.Phase;
import io.github.chakyl.cozycafe.blocks.CafeMenuBlock;
import io.github.chakyl.cozycafe.data.CafeBill;
import io.github.chakyl.cozycafe.item.BillItem;
import io.github.chakyl.cozycafe.item.ServingPlateItem;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class Stage3GameTests implements FabricGameTest {
    private static final BlockPos MANAGER = new BlockPos(1, 1, 1);
    private static final BlockPos TABLE = new BlockPos(4, 1, 4);

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

    /** Puts the player into a boat right next to the table. */
    private static void seat(GameTestHelper helper, ServerPlayer player) {
        BlockPos at = helper.absolutePos(TABLE.east());
        Boat boat = EntityType.BOAT.create(helper.getLevel());
        boat.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.getLevel().addFreshEntity(boat);
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.startRiding(boat, true);
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

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().countItem(item);
    }

    private static ItemStack findCollectedBill(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) if (BillItem.isCollected(stack)) return stack;
        return ItemStack.EMPTY;
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "playerGuestFullVisit")
    public void playerGuestFullVisit(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer waiter = survivalPlayer(helper);
        ServerPlayer guest = survivalPlayer(helper);
        ServerPlayer other = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        cafe.addWaiter(waiter.getUUID(), waiter.getGameProfile().getName());
        CafeMenuBlockEntity table = placeTable(helper);
        int ratingsBefore = cafe.getRatingCount();

        // Ordering
        guest.moveTo(helper.absolutePos(TABLE.east()).getCenter());
        helper.assertTrue(table.orderError(guest) != null, "standing guests can't order");
        seat(helper, guest);
        helper.assertTrue(table.isSeatedHere(guest) && table.orderError(guest) == null, "a seated guest can order");
        List<ItemStack> tooMany = java.util.Collections.nCopies(7, new ItemStack(Items.BREAD));
        helper.assertTrue(table.placeOrder(guest, tooMany, 10) != null, "more than 6 positions are refused");
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.BREAD)), 0) != null, "0 minutes are refused");
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.BREAD)), 901) != null, "more than 900 minutes are refused");
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.DIAMOND)), 10) != null, "only menu positions can be ordered");
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.BREAD), new ItemStack(Items.BREAD)), 10) != null, "a position can't be sent twice");
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.BREAD), new ItemStack(Items.APPLE)), 10) == null, "order placed");
        helper.assertTrue(table.hasOrder() && table.getOrder().size() == 2 && table.getOrderDuration() == 10 * 1200, "two positions, 10 minutes");
        seat(helper, other);
        helper.assertTrue(table.orderError(other) != null, "one guest per table");

        // Serving
        waiter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CozyRegistry.ItemRegistry.BILL.get(), 3));
        table.handleIssueBill(waiter, waiter.getMainHandItem());
        helper.assertTrue(table.getBill() == null, "no bill before the meal");
        dress(waiter);
        waiter.setItemInHand(InteractionHand.MAIN_HAND, ServingPlateItem.withItems(new ItemStack(CozyRegistry.ItemRegistry.SERVING_PLATE.get()), List.of(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD))));
        table.handleServe(waiter, waiter.getMainHandItem());
        helper.assertTrue(table.getPhase() == Phase.EATING && table.getBlockState().getValue(CafeMenuBlock.DISH), "the plate is put on the table");
        tick(helper, table, 40);
        helper.assertTrue(table.getPhase() == Phase.EATING, "a player's plate waits to be taken");

        CafeMenuBlock.handleClick(table, guest, ItemStack.EMPTY);
        helper.assertTrue(count(guest, Items.BREAD) == 1 && count(guest, Items.APPLE) == 1, "the guest takes the food");
        helper.assertTrue(table.getPhase() == Phase.AFTER_MEAL && table.getBlockState().getValue(CafeMenuBlock.DIRTY), "the dirty plate stays on the table");

        // The bill
        waiter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CozyRegistry.ItemRegistry.BILL.get(), 3));
        table.handleIssueBill(waiter, waiter.getMainHandItem());
        helper.assertTrue(table.getBill() == null, "no bill while a plate is on the table");
        CafeMenuBlock.handleClick(table, waiter, ItemStack.EMPTY);
        helper.assertTrue(count(waiter, CozyRegistry.ItemRegistry.DIRTY_SERVING_PLATE.get()) == 1, "the waiter clears the dirty plate");
        waiter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CozyRegistry.ItemRegistry.BILL.get(), 3));
        CafeMenuBlock.handleClick(table, waiter, waiter.getMainHandItem());
        CafeBill bill = table.getBill();
        helper.assertTrue(bill != null && table.getPhase() == Phase.BILL, "the bill is on the table");
        helper.assertTrue(bill.getSum() == 5 && bill.getLines().size() == 2, "sum is 2 + 3, got " + bill.getSum());

        CafeMenuBlock.handleClick(table, waiter, ItemStack.EMPTY);
        helper.assertTrue(table.getBill() == bill, "an unpaid bill stays while the guest is here");
        bill.getPayment().setItem(0, new ItemStack(Items.EMERALD, 4));
        helper.assertTrue(!bill.hasEnoughMoney() && !bill.isPaid(), "4 of 5 is not enough");
        bill.getPayment().setItem(1, new ItemStack(Items.EMERALD, 3));
        bill.getPayment().setItem(2, new ItemStack(Items.DIAMOND, 1));
        helper.assertTrue(bill.hasEnoughMoney() && !bill.isPaid(), "enough money, but no rating yet");
        table.rateBill(other, 1);
        helper.assertTrue(bill.getRating() == 0, "only the guest rates");
        table.rateBill(guest, 4);
        helper.assertTrue(bill.isPaid(), "paid and rated: green");

        // Save/load keeps the bill and its contents.
        CafeMenuBlockEntity copy = new CafeMenuBlockEntity(table.getBlockPos(), table.getBlockState());
        copy.load(table.saveWithoutMetadata());
        helper.assertTrue(copy.getBill() != null && copy.getBill().currencyPaid() == 7 && copy.getBill().getRating() == 4 && copy.getPhase() == Phase.BILL, "bill survives save/load");

        // Picking it up: 5 emeralds for the cafe, the rest are the waiter's tips.
        waiter.getInventory().clearContent();
        dress(waiter);
        CafeMenuBlock.handleClick(table, waiter, ItemStack.EMPTY);
        helper.assertTrue(table.getBill() == null && table.isFree(), "the table is free again");
        ItemStack collected = findCollectedBill(waiter);
        helper.assertTrue(!collected.isEmpty() && BillItem.earnings(collected).getCount() == 5 && BillItem.rating(collected) == 4, "the collected bill carries 5 emeralds and 4 stars");
        helper.assertTrue(count(waiter, Items.EMERALD) == 2 && count(waiter, Items.DIAMOND) == 1, "2 emeralds and a diamond are tips");
        helper.assertTrue(cafe.getRatingCount() == ratingsBefore, "the rating counts only at the cash register");

        // Cashing in at the manager (a waiter may do it).
        helper.assertTrue(cafe.cashBill(other, collected) == false && !collected.isEmpty(), "strangers can't cash bills");
        helper.assertTrue(cafe.cashBill(waiter, collected) && collected.isEmpty(), "the waiter cashes the bill");
        helper.assertTrue(cafe.getRevenue().countOf(new ItemStack(Items.EMERALD)) == 5, "5 emeralds in the cash register");
        helper.assertTrue(cafe.getRatingCount() == ratingsBefore + 1, "the 4-star rating counts now");

        cafe.setOpen(false, true);
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "tipsThatDontFitDrop")
    public void tipsThatDontFitDrop(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer guest = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        CafeMenuBlockEntity table = placeTable(helper);
        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.CAKE)), 5) == null, "order placed");
        dress(owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ServingPlateItem.withItems(new ItemStack(CozyRegistry.ItemRegistry.SERVING_PLATE.get()), List.of(new ItemStack(Items.CAKE))));
        table.handleServe(owner, owner.getMainHandItem());
        table.handleTakeFood(guest);
        table.handleClearPlate(owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CozyRegistry.ItemRegistry.BILL.get()));
        table.handleIssueBill(owner, owner.getMainHandItem());
        CafeBill bill = table.getBill();
        helper.assertTrue(bill != null && bill.getSum() == 1, "bill for 1 emerald");
        bill.getPayment().setItem(0, new ItemStack(Items.EMERALD, 1));
        bill.getPayment().setItem(1, new ItemStack(Items.GOLD_INGOT, 9));
        table.rateBill(guest, 5);

        // A full inventory: the collected bill and the tips land at the waiter's feet.
        for (int i = 0; i < owner.getInventory().items.size(); i++) owner.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        table.handleCollectBill(owner);
        AABB around = owner.getBoundingBox().inflate(3);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
        helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.GOLD_INGOT) && e.getItem().getCount() == 9), "the tip dropped at the feet");
        ItemEntity billDrop = drops.stream().filter(e -> BillItem.isCollected(e.getItem())).findFirst().orElse(null);
        helper.assertTrue(billDrop != null, "the collected bill dropped too");
        helper.assertTrue(cafe.cashBill(owner, billDrop.getItem()), "the owner cashes it");
        helper.assertTrue(cafe.getRevenue().countOf(new ItemStack(Items.EMERALD)) == 1, "1 emerald earned");
        drops.forEach(ItemEntity::discard);
        cafe.setOpen(false, true);
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "playerTimeoutAndLeaving")
    public void playerTimeoutAndLeaving(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer guest = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        CafeMenuBlockEntity table = placeTable(helper);
        int ratings = cafe.getRatingCount();
        double average = cafe.getAverageRating();

        // Getting up and even walking off before anything comes is not cancelling: the table stays the guest's.
        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.BREAD)), 1) == null, "order placed");
        walkAway(helper, guest);
        tick(helper, table, awayLimit() + 20);
        helper.assertTrue(guest.getUUID().equals(table.getGuestPlayer()) && table.hasOrder(), "getting up keeps the order");
        // The cancel button: nothing was served, no penalty.
        table.cancelOrder(guest);
        helper.assertTrue(table.isFree() && cafe.getRatingCount() == ratings, "cancelling costs the cafe nothing");

        // Waiting too long: cancelled, the guest is kicked out of the seat and the cafe gets 0 stars.
        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.BREAD)), 1) == null, "order placed again");
        table.expireDeadline();
        tick(helper, table, 10);
        helper.assertTrue(table.isFree() && !guest.isPassenger(), "timeout cancels the order and dismounts the guest");
        helper.assertTrue(cafe.getRatingCount() == ratings + 1 && cafe.getAverageRating() < average, "the cafe gets a 0-star rating");
        seat(helper, guest);
        helper.assertTrue(table.orderError(guest) == null, "the guest may order again");

        cafe.setOpen(false, true);
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "npcPaysScrapWithTheBill", timeoutTicks = 200)
    public void npcPaysScrapWithTheBill(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        CafeMenuBlockEntity table = placeTable(helper);
        int ratings = cafe.getRatingCount();
        table.startVisit(cafe.getBlockPos(), "MHF_Steve", null);
        for (int i = 0; i < 400 && !table.hasOrder(); i++) tick(helper, table, 1);
        double points = 0;
        for (ItemStack dish : table.getOrder()) points += CafeMenuBlockEntity.foodPoints(dish);
        int least = (int) Math.floor(points / 40.0);
        dress(owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ServingPlateItem.withItems(new ItemStack(CozyRegistry.ItemRegistry.SERVING_PLATE.get()), table.getOrder()));
        table.handleServe(owner, owner.getMainHandItem());
        for (int i = 0; i < 400 && table.getPhase() == Phase.EATING; i++) tick(helper, table, 1);
        helper.assertTrue(table.getPhase() == Phase.AFTER_MEAL && table.getHasCustomer(), "after eating the NPC waits for the bill");
        table.handleClearPlate(owner);

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CozyRegistry.ItemRegistry.BILL.get()));
        CafeMenuBlock.handleClick(table, owner, owner.getMainHandItem());
        CafeBill bill = table.getBill();
        helper.assertTrue(bill != null && bill.isNpc() && bill.isPaid(), "the NPC pays at once: green");
        int paid = bill.currencyPaid();
        helper.assertTrue(bill.getCurrency().is(Items.NETHERITE_SCRAP) && (paid == least || paid == least + 1),
                "netherite scrap by how filling the food was: " + points + " points, paid " + paid);
        helper.assertTrue(!table.getHasCustomer(), "and leaves");
        helper.assertTrue(!table.canReceiveNewCustomer(), "no new guest while the bill lies on the table");

        owner.getInventory().clearContent();
        dress(owner);
        CafeMenuBlock.handleClick(table, owner, ItemStack.EMPTY);
        ItemStack collected = findCollectedBill(owner);
        helper.assertTrue(table.canReceiveNewCustomer(), "bill picked up, table free");
        if (paid > 0) {
            helper.assertTrue(!collected.isEmpty() && cafe.cashBill(owner, collected), "cashed");
            helper.assertTrue(cafe.getRevenue().countOf(new ItemStack(Items.NETHERITE_SCRAP)) == paid, paid + " scrap in the cash register");
        } else {
            helper.assertTrue(collected.isEmpty(), "nothing paid, nothing to cash");
        }
        helper.assertTrue(cafe.getRatingCount() == ratings, "NPCs never rate");

        cafe.setOpen(false, true);
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }

    /** NPCs pay by how filling the food is: 40 points a scrap, the rest a chance; non-food pays nothing. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void npcPaysByFoodPoints(GameTestHelper helper) {
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(1);
        List<ItemStack> grass = List.of(new ItemStack(Items.GRASS), new ItemStack(Items.DIRT), new ItemStack(Items.DIAMOND));
        for (int i = 0; i < 50; i++) helper.assertTrue(CafeMenuBlockEntity.npcPayment(grass, random) == 0, "non-food pays nothing");
        List<ItemStack> dinner = List.of(new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.BREAD));
        double points = 0;
        for (ItemStack dish : dinner) points += CafeMenuBlockEntity.foodPoints(dish);
        helper.assertTrue(Math.abs(points - 52.6) < 0.01, "steak 20.8 + steak 20.8 + bread 11 = 52.6, got " + points);
        int ones = 0, twos = 0;
        for (int i = 0; i < 2000; i++) {
            int pay = CafeMenuBlockEntity.npcPayment(dinner, random);
            helper.assertTrue(pay == 1 || pay == 2, "52.6 points: 1 scrap, maybe 2");
            if (pay == 1) ones++; else twos++;
        }
        double share = twos / 2000.0;
        helper.assertTrue(share > 0.25 && share < 0.38, "the second scrap comes about 31% of the time, got " + share);
        helper.assertTrue(CafeMenuBlockEntity.foodPoints(new ItemStack(Items.CAKE)) > 16, "a whole cake counts all its slices");
        helper.succeed();
    }

    static int awayLimit() {
        return io.github.chakyl.cozycafe.CozyCafe.CONFIG.guestAwayLimit.get();
    }

    private static void walkAway(GameTestHelper helper, ServerPlayer player) {
        player.stopRiding();
        BlockPos far = helper.absolutePos(TABLE).offset(20, 0, 0);
        player.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "unpaidMealBecomesDebt")
    public void unpaidMealBecomesDebt(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer guest = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        CafeMenuBlockEntity table = placeTable(helper);
        dress(owner);
        int ratings = cafe.getRatingCount();

        // Served, not taken, gone for too long: the served order is a debt; a red dot until the plate is cleared.
        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.CAKE)), 5) == null, "order placed");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ServingPlateItem.withItems(new ItemStack(CozyRegistry.ItemRegistry.SERVING_PLATE.get()), List.of(new ItemStack(Items.CAKE))));
        table.handleServe(owner, owner.getMainHandItem());
        walkAway(helper, guest);
        tick(helper, table, awayLimit() - 20);
        helper.assertTrue(!cafe.owes(guest.getUUID()) && guest.getUUID().equals(table.getGuestPlayer()), "a while away is still fine");
        tick(helper, table, 40);
        helper.assertTrue(cafe.debtOf(guest.getUUID()) != null && cafe.debtOf(guest.getUUID()).getCount() == 1 && table.getPhase() == Phase.NONE, "served and gone: a debt of 1");
        helper.assertTrue(table.isLeftOwing(), "the red dot shows the abandoned plate");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        table.handleClearPlate(owner);
        helper.assertTrue(ServingPlateItem.getItems(owner.getMainHandItem()).size() == 1 || owner.getInventory().items.stream().anyMatch(st -> ServingPlateItem.getItems(st).size() == 1), "the full plate comes back");
        helper.assertTrue(!table.isLeftOwing() && table.isFree(), "cleared: no dot, the table is free");
        cafe.forgiveDebt(guest.getUUID());
        owner.getInventory().clearContent();
        dress(owner);

        // Ate and left before the bill came: no debt, the bill is just awaited.
        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.CAKE), new ItemStack(Items.BREAD)), 5) == null, "order placed");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ServingPlateItem.withItems(new ItemStack(CozyRegistry.ItemRegistry.SERVING_PLATE.get()), List.of(new ItemStack(Items.CAKE), new ItemStack(Items.BREAD))));
        table.handleServe(owner, owner.getMainHandItem());
        table.handleTakeFood(guest);
        walkAway(helper, guest);
        tick(helper, table, awayLimit() + 20);
        helper.assertTrue(!cafe.owes(guest.getUUID()) && table.getPhase() == Phase.AFTER_MEAL, "no bill yet: waiting, no debt");
        // The bill comes, the guest stays away: it becomes a debt.
        table.handleClearPlate(owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CozyRegistry.ItemRegistry.BILL.get()));
        table.handleIssueBill(owner, owner.getMainHandItem());
        helper.assertTrue(table.getPhase() == Phase.BILL, "the bill is on the table");
        tick(helper, table, awayLimit() + 20);
        ItemStack debt = cafe.debtOf(guest.getUUID());
        helper.assertTrue(debt != null && debt.is(Items.EMERALD) && debt.getCount() == 3, "debt of 3 emeralds, got " + debt);
        owner.getInventory().clearContent();
        dress(owner);
        table.handleCollectBill(owner);
        helper.assertTrue(table.isFree(), "the table is free again");

        CafeManagerBlockEntity copy = new CafeManagerBlockEntity(cafe.getBlockPos(), cafe.getBlockState());
        copy.load(cafe.saveWithoutMetadata());
        helper.assertTrue(copy.debtOf(guest.getUUID()) != null && copy.debtOf(guest.getUUID()).getCount() == 3, "debt survives save/load");

        // Back at a table: no ordering, the debt bill comes instead of the menu.
        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.CAKE)), 5) != null, "a debtor can't order");
        table.requestOrderScreen(guest);
        CafeBill bill = table.getBill();
        helper.assertTrue(bill != null && bill.isDebt() && bill.getSum() == 3 && table.getPhase() == Phase.BILL, "a debt bill for 3");
        helper.assertTrue(table.getBlockState().getValue(CafeMenuBlock.BILL), "the table shows the bill");
        table.rateBill(guest, 5);
        helper.assertTrue(bill.getRating() == 0, "no rating for a debt");

        // Paid 1 and left again: 1 is taken, 2 are still owed.
        bill.getPayment().setItem(0, new ItemStack(Items.EMERALD, 1));
        walkAway(helper, guest);
        tick(helper, table, 10);
        table.handleCollectBill(owner);
        ItemStack partial = findCollectedBill(owner);
        helper.assertTrue(!partial.isEmpty() && BillItem.earnings(partial).getCount() == 1 && BillItem.rating(partial) == 0, "the 1 emerald is collected");
        helper.assertTrue(cafe.debtOf(guest.getUUID()).getCount() == 2, "2 still owed");
        helper.assertTrue(table.isFree() && !table.getBlockState().getValue(CafeMenuBlock.BILL), "the menu book is back");
        helper.assertTrue(cafe.cashBill(owner, partial), "partial bill cashed");

        // Paying the rest (plus a tip) clears the debt.
        seat(helper, guest);
        table.requestOrderScreen(guest);
        bill = table.getBill();
        helper.assertTrue(bill != null && bill.getSum() == 2, "debt bill for the remaining 2");
        bill.getPayment().setItem(0, new ItemStack(Items.EMERALD, 2));
        bill.getPayment().setItem(1, new ItemStack(Items.DIAMOND, 1));
        helper.assertTrue(bill.isPaid(), "paid without a rating");
        table.handleCollectBill(owner);
        helper.assertTrue(!cafe.owes(guest.getUUID()), "debt cleared");
        helper.assertTrue(count(owner, Items.DIAMOND) == 1, "the tip goes to the waiter");
        helper.assertTrue(cafe.cashBill(owner, findCollectedBill(owner)), "cashed");
        helper.assertTrue(cafe.getRevenue().countOf(new ItemStack(Items.EMERALD)) == 3, "3 emeralds in total");
        helper.assertTrue(table.orderError(guest) == null && table.placeOrder(guest, List.of(new ItemStack(Items.BREAD)), 5) == null, "ordering again");

        // A regular bill paid only in part when leaving: the rest becomes a debt.
        owner.setItemInHand(InteractionHand.MAIN_HAND, ServingPlateItem.withItems(new ItemStack(CozyRegistry.ItemRegistry.SERVING_PLATE.get()), List.of(new ItemStack(Items.BREAD))));
        table.handleServe(owner, owner.getMainHandItem());
        table.handleTakeFood(guest);
        table.handleClearPlate(owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CozyRegistry.ItemRegistry.BILL.get()));
        table.handleIssueBill(owner, owner.getMainHandItem());
        table.getBill().getPayment().setItem(0, new ItemStack(Items.EMERALD, 1));
        table.rateBill(guest, 5);
        walkAway(helper, guest);
        tick(helper, table, awayLimit() + 20);
        helper.assertTrue(cafe.debtOf(guest.getUUID()) != null && cafe.debtOf(guest.getUUID()).getCount() == 1, "1 of 2 owed");
        owner.getInventory().clearContent();
        dress(owner);
        table.handleCollectBill(owner);
        ItemStack left = findCollectedBill(owner);
        helper.assertTrue(BillItem.earnings(left).getCount() == 1 && BillItem.rating(left) == 0, "the paid part, without the rating");
        cafe.cashBill(owner, left);
        helper.assertTrue(cafe.getRatingCount() == ratings, "no ratings from any of this");

        // The owner forgives a debt while the debt bill is on the table: the guest gets their money back and may order.
        helper.assertTrue(table.isFree(), "table free before the forgiveness check");
        seat(helper, guest);
        guest.getInventory().clearContent();
        table.requestOrderScreen(guest);
        helper.assertTrue(table.getBill() != null && table.getBill().isDebt(), "debt bill for the remaining 1");
        table.getBill().getPayment().setItem(0, new ItemStack(Items.GOLD_INGOT, 2));
        cafe.forgiveDebt(guest.getUUID());
        helper.assertTrue(!cafe.owes(guest.getUUID()), "forgiven");
        CafeMenuBlock.handleClick(table, guest, ItemStack.EMPTY);
        helper.assertTrue(table.getBill() == null && count(guest, Items.GOLD_INGOT) == 2, "the bill is gone and the gold is back");
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.BREAD)), 5) == null, "ordering after forgiveness");

        cafe.setOpen(false, true);
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "offlineCountsAsAway")
    public void offlineCountsAsAway(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer guest = survivalPlayer(helper);
        Region region = TestClaims.create(helper, MANAGER, 6, owner);
        CafeManagerBlockEntity cafe = openCafe(helper, owner);
        CafeMenuBlockEntity table = placeTable(helper);
        dress(owner);
        int limit = awayLimit();

        seat(helper, guest);
        helper.assertTrue(table.placeOrder(guest, List.of(new ItemStack(Items.CAKE)), 5) == null, "order placed");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ServingPlateItem.withItems(new ItemStack(CozyRegistry.ItemRegistry.SERVING_PLATE.get()), List.of(new ItemStack(Items.CAKE))));
        table.handleServe(owner, owner.getMainHandItem());
        table.handleTakeFood(guest);
        table.handleClearPlate(owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CozyRegistry.ItemRegistry.BILL.get()));
        table.handleIssueBill(owner, owner.getMainHandItem());

        // The connection drops for a while and comes back: nothing happens.
        table.setGuestOfflineForTest(true);
        tick(helper, table, limit - 20);
        helper.assertTrue(guest.getUUID().equals(table.getGuestPlayer()) && !cafe.owes(guest.getUUID()), "still the guest");
        table.setGuestOfflineForTest(false);
        tick(helper, table, 10);
        helper.assertTrue(guest.getUUID().equals(table.getGuestPlayer()), "back in time, the visit goes on");

        // Offline longer than the limit counts as leaving.
        table.setGuestOfflineForTest(true);
        tick(helper, table, limit - 20);
        helper.assertTrue(!cafe.owes(guest.getUUID()), "the count starts over after coming back");
        tick(helper, table, 40);
        helper.assertTrue(cafe.owes(guest.getUUID()) && table.getGuestPlayer() == null, "offline too long counts as leaving");

        cafe.forgiveDebt(guest.getUUID());
        cafe.setOpen(false, true);
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }
}
