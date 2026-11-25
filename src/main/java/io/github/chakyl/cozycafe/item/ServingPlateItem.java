package io.github.chakyl.cozycafe.item;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A plate holding a whole order: up to {@link #CAPACITY} items, added one by one at the Plating Station.
 * Plates are single-use: after the meal it becomes a dirty plate that can't be washed.
 */
public class ServingPlateItem extends Item {
    public static final int CAPACITY = CafeManagerBlockEntity.MAX_ORDER_SIZE;
    private static final String ITEMS = "platedItems";
    /** Tag of the original mod (one item per plate); still read so old plates keep their food. */
    private static final String LEGACY_FOOD = "platedFood";

    public ServingPlateItem(Properties properties) {
        super(properties);
    }

    public static List<ItemStack> getItems(ItemStack plate) {
        List<ItemStack> items = new ArrayList<>();
        CompoundTag tag = plate.getTag();
        if (tag == null) return items;
        for (Tag entry : tag.getList(ITEMS, Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) entry);
            if (!stack.isEmpty()) items.add(stack);
        }
        if (items.isEmpty() && tag.contains(LEGACY_FOOD)) {
            ItemStack legacy = ItemStack.of(tag.getCompound(LEGACY_FOOD));
            if (!legacy.isEmpty()) items.add(legacy);
        }
        return items;
    }

    public static ItemStack withItems(ItemStack plate, List<ItemStack> items) {
        ItemStack result = plate.copyWithCount(1);
        CompoundTag tag = result.getOrCreateTag();
        tag.remove(LEGACY_FOOD);
        if (items.isEmpty()) {
            tag.remove(ITEMS);
            if (tag.isEmpty()) result.setTag(null);
        } else {
            ListTag list = new ListTag();
            for (ItemStack item : items) list.add(item.copyWithCount(1).save(new CompoundTag()));
            tag.put(ITEMS, list);
        }
        return result;
    }

    /** True when the plate holds exactly the ordered positions (order and NBT don't matter). */
    public static boolean matchesOrder(List<ItemStack> plate, List<ItemStack> order) {
        if (plate.size() != order.size()) return false;
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemStack stack : order) counts.merge(stack.getItem(), 1, Integer::sum);
        for (ItemStack stack : plate) {
            Integer left = counts.get(stack.getItem());
            if (left == null || left == 0) return false;
            counts.put(stack.getItem(), left - 1);
        }
        return true;
    }

    /** Shift + right click in the air takes the last item back off the plate. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack plate = player.getItemInHand(hand);
        List<ItemStack> items = getItems(plate);
        if (!player.isCrouching() || items.isEmpty() || plate.getCount() != 1) return super.use(level, player, hand);
        if (!level.isClientSide) {
            ItemStack removed = items.remove(items.size() - 1);
            player.setItemInHand(hand, withItems(plate, items));
            if (!player.getInventory().add(removed)) player.drop(removed, false);
        }
        player.playSound(SoundEvents.ITEM_PICKUP, 0.2F, 1.0F);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        int size = getItems(stack).size();
        if (size > 0) return Component.translatable("item.cozyrestaurant.serving_plate.served", size, CAPACITY);
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (ItemStack item : getItems(stack)) {
            tooltip.add(Component.literal("• ").append(item.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }
}
