package io.github.chakyl.cozycafe.network;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;

import java.util.function.Consumer;

/**
 * Replacement for Forge's NetworkHooks.openScreen(player, provider, extraDataWriter).
 */
public final class MenuOpener {
    private MenuOpener() {
    }

    public static void open(ServerPlayer player, Component title, MenuConstructor constructor, Consumer<FriendlyByteBuf> extraData) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer serverPlayer, FriendlyByteBuf buf) {
                extraData.accept(buf);
            }

            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return constructor.createMenu(containerId, inventory, p);
            }
        });
    }

    /** Opens one of the cafe screens; the client finds the cafe (and its synced data) by the manager position. */
    public static void openCafe(ServerPlayer player, CafeManagerBlockEntity cafe, String titleKey, MenuConstructor constructor) {
        open(player, Component.translatable(titleKey), constructor, buffer -> buffer.writeBlockPos(cafe.getBlockPos()));
    }
}
