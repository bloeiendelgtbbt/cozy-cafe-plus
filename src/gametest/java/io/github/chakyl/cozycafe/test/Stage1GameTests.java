package io.github.chakyl.cozycafe.test;

import dev.slarrties.privit.common.region.Color;
import dev.slarrties.privit.common.region.RegionGroups;
import dev.slarrties.privit.server.region.Region;
import dev.slarrties.privit.server.world.WorldRegistry;
import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.data.CafeIndex;
import io.github.chakyl.cozycafe.event.RoleTracker;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import io.github.chakyl.cozycafe.util.ApronRules;
import io.github.chakyl.cozycafe.util.CafeAccess;
import io.github.chakyl.cozycafe.util.CafeLookup;
import io.github.chakyl.cozycafe.util.CafeRole;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.UUID;

public class Stage1GameTests implements FabricGameTest {

    private static CafeManagerBlockEntity placeManager(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, CozyRegistry.BlockRegistry.CAFE_MANAGER.get());
        return (CafeManagerBlockEntity) helper.getBlockEntity(rel);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void ownerAndWaitersPersist(GameTestHelper helper) {
        CafeManagerBlockEntity cafe = placeManager(helper, new BlockPos(1, 1, 1));
        UUID owner = UUID.randomUUID();
        UUID waiter = UUID.randomUUID();
        cafe.setOwner(owner, "Owner");
        cafe.addWaiter(waiter, "Waiter");
        cafe.addWaiter(waiter, "Waiter");
        helper.assertTrue(cafe.getWaiters().size() == 1, "duplicate waiter must be ignored");

        CompoundTag tag = cafe.saveWithoutMetadata();
        CafeManagerBlockEntity copy = new CafeManagerBlockEntity(cafe.getBlockPos(), cafe.getBlockState());
        copy.load(tag);
        helper.assertTrue(copy.isOwner(owner) && "Owner".equals(copy.getOwnerName()), "owner must survive save/load");
        helper.assertTrue(copy.isWaiter(waiter), "waiter must survive save/load");
        helper.assertTrue(copy.removeWaiter(waiter) && !copy.isWaiter(waiter), "waiter removal");

        helper.assertTrue(CafeIndex.get(helper.getLevel().getServer()).getCafes(helper.getLevel()).contains(cafe.getBlockPos()), "placed cafe must be in the index");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void onlyOwnerManages(GameTestHelper helper) {
        CafeManagerBlockEntity cafe = placeManager(helper, new BlockPos(1, 1, 1));
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        cafe.setOwner(UUID.randomUUID(), "Someone");
        helper.assertTrue(!CafeAccess.canManage(stranger, cafe), "a stranger must not manage the cafe");
        cafe.setOwner(stranger.getUUID(), stranger.getGameProfile().getName());
        helper.assertTrue(CafeAccess.canManage(stranger, cafe), "the owner must manage the cafe");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void managerDropsItself(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        CafeManagerBlockEntity cafe = placeManager(helper, rel);
        BlockPos abs = helper.absolutePos(rel);
        List<ItemStack> drops = Block.getDrops(cafe.getBlockState(), helper.getLevel(), abs, cafe);
        helper.assertTrue(drops.stream().anyMatch(s -> s.is(CozyRegistry.BlockRegistry.CAFE_MANAGER.get().asItem())), "cafe manager must drop itself (loot table), got " + drops);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apronRules(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack apron = new ItemStack(CozyRegistry.ItemRegistry.APRON.get());

        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        helper.assertTrue(!ApronRules.canPutOnApron(player), "apron needs all armor removed");
        // Apron forced on top of a helmet: the apron is the newcomer and comes off.
        player.setItemSlot(EquipmentSlot.CHEST, apron.copy());
        ApronRules.enforce(player);
        helper.assertTrue(!ApronRules.isWearingApron(player), "apron put on over armor must be taken off");
        helper.assertTrue(player.getInventory().contains(apron), "removed apron goes back to the inventory");

        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.getInventory().clearContent();
        helper.assertTrue(ApronRules.canPutOnApron(player), "apron allowed with no armor");
        player.setItemSlot(EquipmentSlot.CHEST, apron.copy());
        ApronRules.enforce(player);
        helper.assertTrue(ApronRules.isDressedAsWaiter(player), "apron alone = dressed as waiter");
        // Boots put on over the apron: the boots come off.
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        ApronRules.enforce(player);
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.FEET).isEmpty() && ApronRules.isWearingApron(player), "armor over the apron must come off");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.LEATHER_BOOTS)), "removed boots go back to the inventory");
        ApronRules.forget(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "privitClaimDefinesCafeAndRoles")
    public void privitClaimDefinesCafeAndRoles(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        CafeManagerBlockEntity cafe = placeManager(helper, rel);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer waiter = helper.makeMockServerPlayerInLevel();
        cafe.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        cafe.addWaiter(waiter.getUUID(), waiter.getGameProfile().getName());

        BlockPos abs = helper.absolutePos(rel);
        Region region = TestClaims.create(helper, rel, 3, owner);

        helper.assertTrue(CafeLookup.cafeAt(helper.getLevel(), abs.above()) == cafe, "position inside the claim must resolve to the cafe");
        helper.assertTrue(CafeLookup.cafeAt(helper.getLevel(), abs.offset(10, 0, 10)) == null, "position outside the claim has no cafe");

        owner.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5);
        waiter.setPos(abs.getX() + 1.5, abs.getY() + 1, abs.getZ() + 0.5);
        helper.assertTrue(RoleTracker.computeRole(owner) == null, "no label while the cafe is closed");
        cafe.setOpen(true, false);
        helper.assertTrue(RoleTracker.computeRole(owner) == CafeRole.OWNER, "owner label inside an open cafe");
        helper.assertTrue(RoleTracker.computeRole(waiter) == null, "a listed waiter without the apron gets no label");
        waiter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CozyRegistry.ItemRegistry.APRON.get()));
        helper.assertTrue(RoleTracker.computeRole(waiter) == CafeRole.WAITER, "listed waiter in an apron gets the waiter label");
        waiter.setPos(abs.getX() + 20.5, abs.getY() + 1, abs.getZ() + 0.5);
        helper.assertTrue(RoleTracker.computeRole(waiter) == null, "no label outside the claim");
        cafe.setOpen(false, false);
        TestClaims.delete(helper, region, owner);
        helper.succeed();
    }
}
