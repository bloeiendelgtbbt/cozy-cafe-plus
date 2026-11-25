package io.github.chakyl.cozycafe.item;

import io.github.chakyl.cozycafe.util.CafeLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CafeManagerItem extends BlockItem {

    public CafeManagerItem(Block pBlock, Properties pProperties) {
        super(pBlock, pProperties);
    }

    /** A Cafe Manager only goes inside a Privit claim that has no cafe yet. */
    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level) {
            Component error = CafeLookup.placementError(level, context.getClickedPos());
            if (error != null) {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    player.displayClientMessage(error, true);
                    // The client already predicted the placement: give it the real inventory back.
                    player.inventoryMenu.sendAllDataToRemote();
                    player.containerMenu.sendAllDataToRemote();
                }
                return InteractionResult.FAIL;
            }
        }
        return super.place(context);
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltip, TooltipFlag pFlag) {
        super.appendHoverText(pStack, pLevel, pTooltip, pFlag);
        CompoundTag tag = pStack.getTag();
        if (tag != null && tag.contains("cafeData")) {
            CompoundTag cafe = tag.getCompound("cafeData");
            if (cafe.contains("cafeName")) {
                pTooltip.add(Component.translatable("tooltip.cozyrestaurant.cafe_manager.cafe_name", cafe.getString("cafeName")).withStyle(ChatFormatting.AQUA));
            }
            if (cafe.contains("ratingCount") && cafe.getInt("ratingCount") > 0) {
                double average = (double) cafe.getLong("ratingSum") / cafe.getInt("ratingCount");
                int stars = Mth.clamp((int) Math.round(average), 0, 5);
                pTooltip.add(Component.translatable("tooltip.cozyrestaurant.cafe_manager.rating", "★".repeat(stars) + "☆".repeat(5 - stars)).withStyle(ChatFormatting.GOLD));
            }
        } else {
            pTooltip.add(Component.translatable("tooltip.cozyrestaurant.cafe_manager").withStyle(ChatFormatting.GRAY));
        }
    }
}
