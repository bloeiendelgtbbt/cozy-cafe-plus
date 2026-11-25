package io.github.chakyl.cozycafe.event;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeSignBlockEntity;
import io.github.chakyl.cozycafe.util.ApronRules;
import io.github.chakyl.cozycafe.util.CafeAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import io.github.chakyl.cozycafe.data.CafeIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;


public class CommonEvents {

    public static void register() {
        DevCommands.register();
        // Port of BreakEvents: an open cafe's blocks can't be broken.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            // Only the owner (or an operator) may break a Cafe Manager.
            if (blockEntity instanceof CafeManagerBlockEntity cafeManager && !CafeAccess.canManage(player, cafeManager)) {
                player.displayClientMessage(cafeManager.hasOwner()
                        ? Component.translatable("block.cozyrestaurant.cafe_manager.cannot_break_not_owner", cafeManager.getOwnerName()).withStyle(ChatFormatting.RED)
                        : CafeAccess.deniedMessage(cafeManager), true);
                return false;
            }
            if (shouldCancelBreak(level, blockEntity)) {
                player.sendSystemMessage(Component.translatable("block.cozyrestaurant.any.cannot_break").withStyle(ChatFormatting.RED));
                return false;
            }
            return true;
        });

        // Apron rules and role labels.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ApronRules.enforce(player);
            }
            RoleTracker.tick(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            RoleTracker.sendTo(handler.getPlayer());
            io.github.chakyl.cozycafe.data.CafeDirectory.get(server).sendTo(handler.getPlayer());
            io.github.chakyl.cozycafe.data.CafeReports.get(server).sendPending(handler.getPlayer());
            io.github.chakyl.cozycafe.data.CafeNews.playerJoined(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ApronRules.forget(handler.getPlayer().getUUID());
            io.github.chakyl.cozycafe.data.CafeNews.playerLeft(server, handler.getPlayer().getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RoleTracker.reset());
        // Waiters may also serve a seated player guest, or give them the bill, by right-clicking the guest.
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !(entity instanceof ServerPlayer guest) || !(player instanceof ServerPlayer waiter)) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(io.github.chakyl.cozycafe.registry.CozyRegistry.ItemRegistry.SERVING_PLATE.get())
                    && !io.github.chakyl.cozycafe.item.BillItem.isBlank(stack)) {
                return InteractionResult.PASS;
            }
            CafeMenuBlockEntity table = tableOf(guest);
            if (table == null) return InteractionResult.PASS;
            io.github.chakyl.cozycafe.blocks.CafeMenuBlock.handleClick(table, waiter, stack);
            return InteractionResult.SUCCESS;
        });

        // Right-clicking armor to wear it is not allowed while in the apron.
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            Equipable equipable = Equipable.get(stack);
            if (equipable != null && equipable.getEquipmentSlot().getType() == EquipmentSlot.Type.ARMOR
                    && !ApronRules.isApron(stack) && ApronRules.isWearingApron(player)) {
                if (!level.isClientSide) ApronRules.tellNoArmorWithApron(player);
                return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(ItemStack.EMPTY);
        });

    }

    /** The table where this player is the current guest, if any. */
    @org.jetbrains.annotations.Nullable
    private static CafeMenuBlockEntity tableOf(ServerPlayer guest) {
        ServerLevel level = guest.serverLevel();
        for (BlockPos pos : List.copyOf(CafeIndex.tables(level.getServer()).getCafes(level))) {
            if (pos.distSqr(guest.blockPosition()) > 16 * 16 || !level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof CafeMenuBlockEntity table && guest.getUUID().equals(table.getGuestPlayer())) {
                return table;
            }
        }
        return null;
    }

    private static boolean shouldCancelBreak(Level level, BlockEntity blockEntity) {
        if (blockEntity instanceof CafeManagerBlockEntity cafeManager) {
            return cafeManager.isOpen();
        } else if (blockEntity instanceof CafeSignBlockEntity cafeSign) {
            return cafeSign.getLinkedManager() != null && level.getBlockEntity(cafeSign.getLinkedManager()) instanceof CafeManagerBlockEntity linkedManager && linkedManager.isOpen();
        } else if (blockEntity instanceof CafeMenuBlockEntity cafeMenu) {
            return cafeMenu.getCafeManager() != null && level.getBlockEntity(cafeMenu.getCafeManager()) instanceof CafeManagerBlockEntity linkedManager && linkedManager.isOpen();
        }
        return false;
    }
}
