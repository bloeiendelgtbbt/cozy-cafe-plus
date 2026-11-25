package io.github.chakyl.cozycafe.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * "Счёт". A blank bill (crafted) is handed to a guest by a waiter; once the guest paid, the waiter picks the bill up
 * from the table and it becomes a collected bill carrying the earnings, the rating and the cafe it belongs to.
 * Right-clicking that cafe's manager with it puts the money into the cash register.
 */
public class BillItem extends Item {
    private static final String TAG = "cafeBill";

    public BillItem(Properties properties) {
        super(properties);
    }

    /** A collected bill of the cafe at {@code managerPos}. {@code rating} 0 = no rating (NPC guest). */
    public static ItemStack collected(ItemStack blank, Level level, BlockPos managerPos, ItemStack earnings, int rating, String guestName) {
        ItemStack stack = blank.copyWithCount(1);
        CompoundTag tag = new CompoundTag();
        tag.put("manager", NbtUtils.writeBlockPos(managerPos));
        tag.putString("dimension", level.dimension().location().toString());
        tag.put("earnings", earnings.save(new CompoundTag()));
        tag.putInt("rating", rating);
        tag.putString("guest", guestName);
        stack.getOrCreateTag().put(TAG, tag);
        return stack;
    }

    public static boolean isCollected(ItemStack stack) {
        return stack.getItem() instanceof BillItem && stack.getTag() != null && stack.getTag().contains(TAG);
    }

    public static boolean isBlank(ItemStack stack) {
        return stack.getItem() instanceof BillItem && !isCollected(stack);
    }

    @Nullable
    private static CompoundTag data(ItemStack stack) {
        return isCollected(stack) ? stack.getTag().getCompound(TAG) : null;
    }

    @Nullable
    public static BlockPos managerPos(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag == null ? null : NbtUtils.readBlockPos(tag.getCompound("manager"));
    }

    @Nullable
    public static ResourceKey<Level> dimension(ItemStack stack) {
        CompoundTag tag = data(stack);
        if (tag == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("dimension"));
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    public static ItemStack earnings(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag == null ? ItemStack.EMPTY : ItemStack.of(tag.getCompound("earnings"));
    }

    public static int rating(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag == null ? 0 : tag.getInt("rating");
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(isCollected(stack) ? "item.cozyrestaurant.bill.collected" : "item.cozyrestaurant.bill");
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isCollected(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = data(stack);
        if (tag == null) {
            tooltip.add(Component.translatable("tooltip.cozyrestaurant.bill.blank.0").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.cozyrestaurant.bill.blank.1").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!tag.getString("guest").isEmpty()) {
            tooltip.add(Component.translatable("tooltip.cozyrestaurant.bill.guest", tag.getString("guest")).withStyle(ChatFormatting.GRAY));
        }
        ItemStack earnings = earnings(stack);
        tooltip.add(Component.translatable("tooltip.cozyrestaurant.bill.earnings", earnings.getCount(), earnings.getHoverName()).withStyle(ChatFormatting.GOLD));
        int rating = tag.getInt("rating");
        if (rating > 0) {
            tooltip.add(Component.literal("★".repeat(rating) + "☆".repeat(5 - rating)).withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable("tooltip.cozyrestaurant.bill.cash_in").withStyle(ChatFormatting.GRAY));
    }
}
