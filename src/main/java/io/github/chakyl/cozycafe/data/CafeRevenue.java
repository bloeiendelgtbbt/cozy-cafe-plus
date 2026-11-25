package io.github.chakyl.cozycafe.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Money stored in a Cafe Manager: any number of any items, kept until the owner withdraws them.
 * Counts are longs, so the storage is effectively unlimited.
 */
public class CafeRevenue {
    public static final class Entry {
        private final ItemStack template;
        private long count;

        Entry(ItemStack template, long count) {
            this.template = template.copyWithCount(1);
            this.count = count;
        }

        public ItemStack template() {
            return this.template;
        }

        public long count() {
            return this.count;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public List<Entry> entries() {
        return Collections.unmodifiableList(this.entries);
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public void deposit(ItemStack stack) {
        deposit(stack, stack.getCount());
    }

    public void deposit(ItemStack stack, long count) {
        if (stack.isEmpty() || count <= 0) return;
        for (Entry entry : this.entries) {
            if (ItemStack.isSameItemSameTags(entry.template, stack)) {
                entry.count += count;
                return;
            }
        }
        this.entries.add(new Entry(stack, count));
    }

    public long countOf(ItemStack stack) {
        for (Entry entry : this.entries) {
            if (ItemStack.isSameItemSameTags(entry.template, stack)) return entry.count;
        }
        return 0;
    }

    /**
     * Moves as much as fits into the player's main inventory. Whatever does not fit stays stored.
     * Slots are filled by hand: vanilla's Inventory.add deletes the overflow for players in creative mode.
     *
     * @return how many items were handed out
     */
    public long withdrawInto(Player player) {
        Inventory inventory = player.getInventory();
        long given = 0;
        for (Entry entry : this.entries) {
            int maxStack = Math.min(entry.template.getMaxStackSize(), inventory.getMaxStackSize());
            // Top up matching stacks first, then use empty slots.
            for (int pass = 0; pass < 2 && entry.count > 0; pass++) {
                for (int slot = 0; slot < inventory.items.size() && entry.count > 0; slot++) {
                    ItemStack existing = inventory.items.get(slot);
                    if (pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameTags(existing, entry.template) && existing.getCount() < maxStack) {
                        int moved = (int) Math.min(maxStack - existing.getCount(), entry.count);
                        existing.grow(moved);
                        entry.count -= moved;
                        given += moved;
                    } else if (pass == 1 && existing.isEmpty()) {
                        int moved = (int) Math.min(maxStack, entry.count);
                        inventory.items.set(slot, entry.template.copyWithCount(moved));
                        entry.count -= moved;
                        given += moved;
                    }
                }
            }
        }
        this.entries.removeIf(e -> e.count <= 0);
        if (given > 0) inventory.setChanged();
        return given;
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Entry entry : this.entries) {
            CompoundTag tag = new CompoundTag();
            entry.template.save(tag);
            tag.putLong("amount", entry.count);
            list.add(tag);
        }
        return list;
    }

    public void load(ListTag list) {
        this.entries.clear();
        for (Tag tag : list) {
            CompoundTag compound = (CompoundTag) tag;
            ItemStack template = ItemStack.of(compound);
            long amount = compound.getLong("amount");
            if (!template.isEmpty() && amount > 0) this.deposit(template, amount);
        }
    }
}
