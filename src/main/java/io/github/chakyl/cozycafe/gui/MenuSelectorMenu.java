package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.network.MenuOpener;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The menu editor: any item put into the slot becomes a menu position. */
public class MenuSelectorMenu extends GhostSlotMenu {
    /**
     * Low 3 bits: 0 = nothing tried yet, otherwise 1 + {@link CafeManagerBlockEntity.AddResult#ordinal()} of the last try.
     * Higher bits count the tries, so the screen notices the same result twice in a row. (Data slots sync as shorts.)
     */
    private final DataSlot lastResult = DataSlot.standalone();
    private int attempts = 0;

    public MenuSelectorMenu(int pContainerId, Inventory pPlayerInventory, FriendlyByteBuf buf) {
        this(pContainerId, pPlayerInventory, pPlayerInventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public MenuSelectorMenu(int pContainerId, Inventory pPlayerInventory, BlockEntity entity) {
        super(CozyRegistry.MenuRegistry.MENU_SELECTOR.get(), pContainerId, pPlayerInventory, entity);
        this.addDataSlot(this.lastResult);
    }

    public static void open(ServerPlayer player, CafeManagerBlockEntity cafe) {
        MenuOpener.openCafe(player, cafe, "container.cozyrestaurant.menu_selector", (id, inv, p) -> new MenuSelectorMenu(id, inv, cafe));
    }

    @Override
    protected void offer(ItemStack stack) {
        if (this.blockEntity.isOpen() || !this.blockEntity.hasCurrency()) return;
        this.attempts = (this.attempts + 1) & 0xFFF;
        this.lastResult.set((this.attempts << 3) | (1 + this.blockEntity.addToMenu(stack).ordinal()));
        this.broadcastChanges();
    }

    /** Result of the last attempt to add an item, or null before the first one. */
    public CafeManagerBlockEntity.AddResult getLastResult() {
        int code = this.lastResult.get() & 7;
        CafeManagerBlockEntity.AddResult[] values = CafeManagerBlockEntity.AddResult.values();
        return code <= 0 || code > values.length ? null : values[code - 1];
    }

    public int getResultCounter() {
        return this.lastResult.get();
    }
}
