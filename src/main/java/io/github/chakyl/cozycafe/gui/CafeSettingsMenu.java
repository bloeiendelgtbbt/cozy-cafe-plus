package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.network.MenuOpener;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** "Настройки кафе": the payment currency (any item, put into the slot) and who the guests are. */
public class CafeSettingsMenu extends GhostSlotMenu {
    public CafeSettingsMenu(int pContainerId, Inventory pPlayerInventory, FriendlyByteBuf buf) {
        this(pContainerId, pPlayerInventory, pPlayerInventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public CafeSettingsMenu(int pContainerId, Inventory pPlayerInventory, BlockEntity entity) {
        super(CozyRegistry.MenuRegistry.CAFE_SETTINGS.get(), pContainerId, pPlayerInventory, entity);
        this.ghost.setItem(0, this.blockEntity.getCurrency().copy());
    }

    public static void open(ServerPlayer player, CafeManagerBlockEntity cafe) {
        MenuOpener.openCafe(player, cafe, "container.cozyrestaurant.cafe_settings", (id, inv, p) -> new CafeSettingsMenu(id, inv, cafe));
    }

    @Override
    protected void offer(ItemStack stack) {
        if (this.blockEntity.isOpen()) {
            // Prices are counted in the currency: changing it mid-shift would change every price at once.
            if (this.player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity.feedback(serverPlayer,
                        net.minecraft.network.chat.Component.translatable("gui.cozyrestaurant.settings.currency_locked"), false);
            }
            return;
        }
        this.blockEntity.setCurrency(stack);
        this.ghost.setItem(0, this.blockEntity.getCurrency().copy());
        this.broadcastChanges();
    }
}
