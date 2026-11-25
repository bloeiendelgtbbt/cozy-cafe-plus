package io.github.chakyl.cozycafe.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guests who ate and left without paying. A debt is owed to one cafe, in the currency of the order (amounts in
 * different currencies are kept apart). While a guest owes anything, they can't order in that cafe.
 */
public class CafeDebts {
    public static final class Debt {
        private String name;
        /** One stack per currency; the count can exceed the stack size. */
        private final List<ItemStack> amounts = new ArrayList<>();

        private Debt(String name) {
            this.name = name;
        }

        public String name() {
            return this.name;
        }

        public List<ItemStack> amounts() {
            return Collections.unmodifiableList(this.amounts);
        }
    }

    private final Map<UUID, Debt> debts = new LinkedHashMap<>();

    public void add(UUID player, String name, ItemStack currency, int count) {
        if (count <= 0 || currency.isEmpty()) return;
        Debt debt = this.debts.computeIfAbsent(player, id -> new Debt(name));
        if (!name.isEmpty()) debt.name = name;
        for (ItemStack amount : debt.amounts) {
            if (ItemStack.isSameItemSameTags(amount, currency)) {
                amount.setCount(amount.getCount() + count);
                return;
            }
        }
        debt.amounts.add(currency.copyWithCount(count));
    }

    public boolean owes(UUID player) {
        return this.debts.containsKey(player);
    }

    /** The first amount the player still owes, or null. */
    @Nullable
    public ItemStack firstAmount(UUID player) {
        Debt debt = this.debts.get(player);
        return debt == null || debt.amounts.isEmpty() ? null : debt.amounts.get(0).copy();
    }

    /** Takes {@code count} of {@code currency} off the player's debt. */
    public void pay(UUID player, ItemStack currency, int count) {
        Debt debt = this.debts.get(player);
        if (debt == null) return;
        debt.amounts.removeIf(amount -> {
            if (!ItemStack.isSameItemSameTags(amount, currency)) return false;
            amount.setCount(amount.getCount() - count);
            return amount.getCount() <= 0;
        });
        if (debt.amounts.isEmpty()) this.debts.remove(player);
    }

    public void forgive(UUID player) {
        this.debts.remove(player);
    }

    public Map<UUID, Debt> all() {
        return Collections.unmodifiableMap(this.debts);
    }

    public ListTag save() {
        ListTag list = new ListTag();
        this.debts.forEach((id, debt) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("name", debt.name);
            ListTag amounts = new ListTag();
            for (ItemStack amount : debt.amounts) {
                CompoundTag amountTag = amount.copyWithCount(1).save(new CompoundTag());
                amountTag.putInt("amount", amount.getCount());
                amounts.add(amountTag);
            }
            tag.put("amounts", amounts);
            list.add(tag);
        });
        return list;
    }

    public void load(ListTag list) {
        this.debts.clear();
        for (Tag entry : list) {
            CompoundTag tag = (CompoundTag) entry;
            if (!tag.hasUUID("id")) continue;
            for (Tag amountEntry : tag.getList("amounts", Tag.TAG_COMPOUND)) {
                CompoundTag amountTag = (CompoundTag) amountEntry;
                this.add(tag.getUUID("id"), tag.getString("name"), ItemStack.of(amountTag), amountTag.getInt("amount"));
            }
        }
    }
}
