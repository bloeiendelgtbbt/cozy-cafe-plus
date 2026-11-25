package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.network.MenuOpener;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CafeManagerMenu extends AbstractContainerMenu {
    public final CafeManagerBlockEntity blockEntity;
    private final Level level;

    public CafeManagerMenu(int pContainerId, Inventory pPlayerInventory, FriendlyByteBuf buf) {
        this(pContainerId, pPlayerInventory, pPlayerInventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public CafeManagerMenu(int pContainerId, Inventory pPlayerInventory, BlockEntity entity) {
        super(CozyRegistry.MenuRegistry.CAFE_MANAGER.get(), pContainerId);
        this.level = pPlayerInventory.player.level();
        this.blockEntity = (CafeManagerBlockEntity) entity;
    }

    public static void open(ServerPlayer player, CafeManagerBlockEntity cafe) {
        MenuOpener.openCafe(player, cafe, "container.cozyrestaurant.cafe_manager", (id, inv, p) -> new CafeManagerMenu(id, inv, cafe));
    }

    public boolean getIsCafeOpen() {
        return this.blockEntity.isOpen();
    }

    public String getCafeName() {
        return this.blockEntity.getCafeName();
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()), pPlayer, CozyRegistry.BlockRegistry.CAFE_MANAGER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        return ItemStack.EMPTY;
    }
}
