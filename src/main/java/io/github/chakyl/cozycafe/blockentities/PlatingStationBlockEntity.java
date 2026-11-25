package io.github.chakyl.cozycafe.blockentities;

import io.github.chakyl.cozycafe.item.ServingPlateItem;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PlatingStationBlockEntity extends BlockEntity {
    private ItemStack plateItem = ItemStack.EMPTY;

    public PlatingStationBlockEntity(BlockPos pos, BlockState state) {
        super(CozyRegistry.BlockEntityRegistry.PLATING_STATION.get(), pos, state);
    }

    public ItemStack getPlateItem() {
        return this.plateItem;
    }

    /**
     * Put a clean plate on the station, then add the order to it one item per click (any item, up to 6).
     * Empty hand: take the plate. Shift + empty hand: take the last item back off the plate.
     */
    public InteractionResult handlePlating(Level level, Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);
        // Inside a cafe only its staff (and operators) may put, fill or take plates here.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity cafe = io.github.chakyl.cozycafe.util.CafeLookup.cafeAt(serverLevel, this.worldPosition);
            if (cafe != null && !io.github.chakyl.cozycafe.util.CafeAccess.canHandlePlates(player, cafe)) {
                player.displayClientMessage(io.github.chakyl.cozycafe.util.CafeAccess.platesStaffOnlyMessage(), true);
                return InteractionResult.SUCCESS;
            }
        }
        if (this.plateItem.isEmpty()) {
            if (heldItem.is(CozyRegistry.ItemRegistry.SERVING_PLATE.get())) {
                if (!level.isClientSide) {
                    this.plateItem = heldItem.split(1);
                    this.setChangedForRender();
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        List<ItemStack> items = ServingPlateItem.getItems(this.plateItem);
        if (heldItem.isEmpty()) {
            if (player.isShiftKeyDown() && !items.isEmpty()) {
                ItemStack removed = items.remove(items.size() - 1);
                this.plateItem = ServingPlateItem.withItems(this.plateItem, items);
                player.setItemInHand(hand, removed);
            } else {
                player.setItemInHand(hand, this.plateItem);
                this.plateItem = ItemStack.EMPTY;
            }
            this.setChangedForRender();
            return InteractionResult.SUCCESS;
        }
        if (heldItem.is(CozyRegistry.ItemRegistry.SERVING_PLATE.get()) || heldItem.is(CozyRegistry.ItemRegistry.DIRTY_SERVING_PLATE.get())) {
            return InteractionResult.SUCCESS;
        }
        if (items.size() >= ServingPlateItem.CAPACITY) {
            player.displayClientMessage(Component.translatable("block.cozyrestaurant.plating_station.full", ServingPlateItem.CAPACITY).withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        items.add(heldItem.split(1));
        this.plateItem = ServingPlateItem.withItems(this.plateItem, items);
        level.playSound(null, this.worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.2F);
        this.setChangedForRender();
        return InteractionResult.SUCCESS;
    }

    private void setChangedForRender() {
        this.setChanged();
        this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("plateItem", this.plateItem.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.plateItem = ItemStack.of(tag.getCompound("plateItem"));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag);
        return tag;
    }

    // Forge-only handleUpdateTag override removed: vanilla calls load() with the chunk update tag.
}