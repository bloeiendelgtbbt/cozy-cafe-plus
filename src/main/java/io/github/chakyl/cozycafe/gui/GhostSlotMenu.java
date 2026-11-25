package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A cafe screen with one "ghost" slot above the player's inventory: putting an item there only reads it
 * (the item stays with the player). Clicking the slot with an item, or shift-clicking an inventory item, offers it.
 */
public abstract class GhostSlotMenu extends AbstractContainerMenu {
    public final CafeManagerBlockEntity blockEntity;
    protected final Player player;
    protected final Level level;
    protected final SimpleContainer ghost = new SimpleContainer(1);

    protected GhostSlotMenu(MenuType<?> type, int containerId, Inventory playerInventory, BlockEntity entity) {
        super(type, containerId);
        this.player = playerInventory.player;
        this.level = playerInventory.player.level();
        this.blockEntity = (CafeManagerBlockEntity) entity;
        this.addSlot(new Slot(this.ghost, 0, 22, 113) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player playerIn) {
                return false;
            }
        });
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 12 + j * 17, 140 + i * 17));
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 12 + i * 17, 196));
        }
    }

    /** Server side: the player offered this item through the ghost slot. */
    protected abstract void offer(ItemStack stack);

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == 0) {
            ItemStack carried = this.getCarried();
            if (!this.level.isClientSide() && !carried.isEmpty()) this.offer(carried);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        if (pIndex > 0 && !this.level.isClientSide()) {
            Slot slot = this.slots.get(pIndex);
            if (slot.hasItem()) this.offer(slot.getItem());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(ContainerLevelAccess.create(this.level, this.blockEntity.getBlockPos()), pPlayer, CozyRegistry.BlockRegistry.CAFE_MANAGER.get());
    }
}
