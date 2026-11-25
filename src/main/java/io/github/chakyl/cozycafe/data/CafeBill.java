package io.github.chakyl.cozycafe.data;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A bill lying on a cafe table. It lists what was ordered and what it costs; the guest puts the payment into its
 * five slots (like a small chest) and rates the cafe. The bill counts as paid once it holds at least the sum in the
 * cafe's currency and (for a player guest) a rating was chosen. NPC guests pay one netherite scrap and never rate.
 */
public class CafeBill {
    public static final int SLOTS = 5;

    /** The player who has to pay, or null for an NPC guest. */
    @Nullable
    private UUID guest;
    private String guestName = "";
    private final List<CafeMenuEntry> lines = new ArrayList<>();
    private ItemStack currency = ItemStack.EMPTY;
    private int sum;
    /** 0 = not rated yet, otherwise 1-5. */
    private int rating;
    private final SimpleContainer payment = new SimpleContainer(SLOTS);
    /** Paying off an old debt: no rating. */
    private boolean debt;
    /** The guest left without paying: whatever lies in it is all the cafe gets, no rating. */
    private boolean closed;
    /** The payment already went into the shift report. */
    private boolean counted;

    private CafeBill() {
    }

    /** The bill for a player guest: the ordered positions at their prices, payable in the cafe currency. */
    public static CafeBill forPlayer(UUID guest, String guestName, List<CafeMenuEntry> lines, ItemStack currency) {
        CafeBill bill = new CafeBill();
        bill.guest = guest;
        bill.guestName = guestName;
        bill.lines.addAll(lines);
        bill.currency = currency.copyWithCount(1);
        bill.sum = lines.stream().mapToInt(CafeMenuEntry::price).sum();
        return bill;
    }

    /** A debt bill: what the guest still owes from an earlier visit. Paid without a rating. */
    public static CafeBill forDebt(UUID guest, String guestName, ItemStack debt) {
        CafeBill bill = new CafeBill();
        bill.guest = guest;
        bill.guestName = guestName;
        bill.currency = debt.copyWithCount(1);
        bill.sum = debt.getCount();
        bill.debt = true;
        return bill;
    }

    /** The bill for an NPC: one netherite scrap, put in at once. */
    public static CafeBill forNpc(List<ItemStack> eaten) {
        CafeBill bill = new CafeBill();
        for (ItemStack item : eaten) bill.lines.add(new CafeMenuEntry(item.copyWithCount(1), 0));
        bill.currency = new ItemStack(Items.NETHERITE_SCRAP);
        bill.sum = 1;
        bill.payment.setItem(0, new ItemStack(Items.NETHERITE_SCRAP));
        return bill;
    }

    public boolean isDebt() {
        return this.debt;
    }

    public boolean isClosed() {
        return this.closed;
    }

    /** Only a player paying for a regular visit rates the cafe. */
    public boolean needsRating() {
        return !this.isNpc() && !this.debt && !this.closed;
    }

    /**
     * The guest left without paying in full. The bill keeps what was paid (up to the sum) as the earnings and drops
     * the rating. Returns how much is still owed.
     */
    public int close() {
        int paid = Math.min(this.currencyPaid(), this.sum);
        int owed = this.sum - paid;
        this.sum = paid;
        this.rating = 0;
        this.closed = true;
        return owed;
    }

    public boolean isNpc() {
        return this.guest == null;
    }

    @Nullable
    public UUID getGuest() {
        return this.guest;
    }

    public String getGuestName() {
        return this.guestName;
    }

    public List<CafeMenuEntry> getLines() {
        return Collections.unmodifiableList(this.lines);
    }

    public ItemStack getCurrency() {
        return this.currency;
    }

    public int getSum() {
        return this.sum;
    }

    public int getRating() {
        return this.rating;
    }

    public void setRating(int rating) {
        if (!this.needsRating()) return;
        this.rating = Math.max(0, Math.min(5, rating));
    }

    public SimpleContainer getPayment() {
        return this.payment;
    }

    /** How much of the cafe currency lies in the bill. */
    public int currencyPaid() {
        int count = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = this.payment.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, this.currency)) count += stack.getCount();
        }
        return count;
    }

    public boolean hasEnoughMoney() {
        return this.currencyPaid() >= this.sum;
    }

    /** Paid = enough money, and (for a regular player visit) a rating was chosen. */
    public boolean isPaid() {
        return this.hasEnoughMoney() && (!this.needsRating() || this.rating > 0);
    }

    /**
     * Empties the bill: returns exactly the sum in currency (what the cafe earns), everything else lands in
     * {@code tips}. Only call this on a paid bill.
     */
    public ItemStack takeEarnings(List<ItemStack> tips) {
        int needed = this.sum;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = this.payment.getItem(i);
            if (stack.isEmpty()) continue;
            if (needed > 0 && ItemStack.isSameItemSameTags(stack, this.currency)) {
                int taken = Math.min(needed, stack.getCount());
                needed -= taken;
                stack.shrink(taken);
            }
            if (!stack.isEmpty()) tips.add(stack.copy());
            this.payment.setItem(i, ItemStack.EMPTY);
        }
        return this.currency.copyWithCount(this.sum - needed);
    }

    /** Empties the bill completely (an unpaid bill being cleared away). */
    public List<ItemStack> takeAll() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = this.payment.removeItemNoUpdate(i);
            if (!stack.isEmpty()) all.add(stack);
        }
        return all;
    }

    public boolean isCounted() {
        return this.counted;
    }

    public void markCounted() {
        this.counted = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (this.guest != null) tag.putUUID("guest", this.guest);
        tag.putString("guestName", this.guestName);
        ListTag lineList = new ListTag();
        for (CafeMenuEntry line : this.lines) lineList.add(line.save());
        tag.put("lines", lineList);
        tag.put("currency", this.currency.save(new CompoundTag()));
        tag.putInt("sum", this.sum);
        tag.putInt("rating", this.rating);
        tag.putBoolean("debt", this.debt);
        tag.putBoolean("closed", this.closed);
        tag.putBoolean("counted", this.counted);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) items.set(i, this.payment.getItem(i));
        tag.put("payment", ContainerHelper.saveAllItems(new CompoundTag(), items));
        return tag;
    }

    public static CafeBill load(CompoundTag tag) {
        CafeBill bill = new CafeBill();
        bill.guest = tag.hasUUID("guest") ? tag.getUUID("guest") : null;
        bill.guestName = tag.getString("guestName");
        for (Tag line : tag.getList("lines", Tag.TAG_COMPOUND)) bill.lines.add(CafeMenuEntry.load((CompoundTag) line));
        bill.currency = ItemStack.of(tag.getCompound("currency"));
        bill.sum = tag.getInt("sum");
        bill.rating = tag.getInt("rating");
        bill.debt = tag.getBoolean("debt");
        bill.closed = tag.getBoolean("closed");
        bill.counted = tag.getBoolean("counted");
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("payment"), items);
        for (int i = 0; i < SLOTS; i++) bill.payment.setItem(i, items.get(i));
        return bill;
    }
}
