package io.github.chakyl.cozycafe.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * One position of a cafe menu: any item or block, and its price in the cafe's currency (a number of currency items).
 */
public record CafeMenuEntry(ItemStack item, int price) {
    public static final int MIN_PRICE = 1;

    public CafeMenuEntry {
        item = item.copyWithCount(1);
        price = Math.max(MIN_PRICE, price);
    }

    public CafeMenuEntry withPrice(int newPrice) {
        return new CafeMenuEntry(this.item, newPrice);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        this.item.save(tag);
        tag.putInt("price", this.price);
        return tag;
    }

    /** Old menus (without a price) load with the minimum price. */
    public static CafeMenuEntry load(CompoundTag tag) {
        return new CafeMenuEntry(ItemStack.of(tag), tag.contains("price") ? tag.getInt("price") : MIN_PRICE);
    }
}
