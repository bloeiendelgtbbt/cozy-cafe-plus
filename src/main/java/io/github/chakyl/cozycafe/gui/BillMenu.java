package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.data.CafeBill;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The guest's bill: five slots for the payment (kept on the table like a chest) and the 1-5 star rating,
 * chosen with menu buttons 1-5.
 */
public class BillMenu extends AbstractContainerMenu {
    public static final int SLOT_X = 44;
    public static final int SLOT_Y = 126;
    public static final int INVENTORY_Y = 162;

    private final BlockPos tablePos;
    @Nullable
    private final CafeMenuBlockEntity table;
    /** The server's bill; the client reads its copy from the synced table. */
    @Nullable
    private final CafeBill bill;
    private final Container payment;

    public BillMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        super(CozyRegistry.MenuRegistry.BILL.get(), containerId);
        this.tablePos = buf.readBlockPos();
        this.table = inventory.player.level().getBlockEntity(this.tablePos) instanceof CafeMenuBlockEntity t ? t : null;
        this.bill = null;
        this.payment = new SimpleContainer(CafeBill.SLOTS);
        this.addSlots(inventory);
    }

    public BillMenu(int containerId, Inventory inventory, CafeMenuBlockEntity table) {
        super(CozyRegistry.MenuRegistry.BILL.get(), containerId);
        this.tablePos = table.getBlockPos();
        this.table = table;
        this.bill = table.getBill();
        this.payment = this.bill.getPayment();
        this.addSlots(inventory);
    }

    private void addSlots(Inventory inventory) {
        for (int i = 0; i < CafeBill.SLOTS; i++) {
            this.addSlot(new Slot(this.payment, i, SLOT_X + i * 18, SLOT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    /** The table's bill as this side sees it (on the client: the synced copy). */
    @Nullable
    public CafeBill bill() {
        if (this.bill != null) return this.bill;
        return this.table == null ? null : this.table.getBill();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 1 && id <= 5 && player instanceof ServerPlayer serverPlayer && this.table != null && this.stillValid(player)) {
            this.table.rateBill(serverPlayer, id);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < CafeBill.SLOTS) {
            if (!this.moveItemStackTo(stack, CafeBill.SLOTS, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(stack, 0, CafeBill.SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.table == null || this.table.isRemoved()) return false;
        // The bill was picked up (or replaced) while the screen was open.
        if (this.bill != null && this.table.getBill() != this.bill) return false;
        return player.distanceToSqr(this.tablePos.getCenter()) <= 8.0 * 8.0;
    }
}
