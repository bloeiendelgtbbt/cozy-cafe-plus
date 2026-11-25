package io.github.chakyl.cozycafe.data;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * One shift of a cafe, from opening to closing: guests served (NPCs and players who got at least one dish),
 * bills paid (per currency) and debts left (how many bills, how much per currency).
 */
public class CafeShift {
    private int guests;
    private final CafeRevenue earned = new CafeRevenue();
    private int debtBills;
    private final CafeRevenue debts = new CafeRevenue();

    public void reset() {
        this.guests = 0;
        this.earned.load(new net.minecraft.nbt.ListTag());
        this.debtBills = 0;
        this.debts.load(new net.minecraft.nbt.ListTag());
    }

    public void guestServed() {
        this.guests++;
    }

    public void billPaid(ItemStack currency, int amount) {
        this.earned.deposit(currency, amount);
    }

    public void debtLeft(ItemStack currency, int amount) {
        this.debtBills++;
        this.debts.deposit(currency, amount);
    }

    public int guests() {
        return this.guests;
    }

    public int debtBills() {
        return this.debtBills;
    }

    public CafeRevenue earned() {
        return this.earned;
    }

    public CafeRevenue debts() {
        return this.debts;
    }

    /** The report sent to the owner when the shift closes. */
    public Component report(String cafeName) {
        MutableComponent text = Component.translatable("message.cozyrestaurant.shift.closed", Component.literal(cafeName).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GOLD);
        text.append("\n").append(Component.translatable("message.cozyrestaurant.shift.guests", Component.literal(String.valueOf(this.guests)).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        text.append("\n").append(Component.translatable("message.cozyrestaurant.shift.earned",
                this.earned.isEmpty() ? Component.translatable("message.cozyrestaurant.shift.nothing") : describe(this.earned, ChatFormatting.GREEN)).withStyle(ChatFormatting.GRAY));
        Component debtText = this.debtBills == 0 ? Component.translatable("message.cozyrestaurant.shift.no_debts")
                : Component.translatable("message.cozyrestaurant.shift.debts_value", this.debtBills, describe(this.debts, ChatFormatting.RED));
        text.append("\n").append(Component.translatable("message.cozyrestaurant.shift.debts", debtText).withStyle(ChatFormatting.GRAY));
        return text;
    }

    private static MutableComponent describe(CafeRevenue amounts, ChatFormatting color) {
        MutableComponent text = Component.empty();
        boolean first = true;
        for (CafeRevenue.Entry entry : amounts.entries()) {
            if (!first) text.append(", ");
            first = false;
            text.append(Component.literal(entry.count() + " × ").withStyle(color)).append(entry.template().getHoverName().copy().withStyle(color));
        }
        return text;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("guests", this.guests);
        tag.put("earned", this.earned.save());
        tag.putInt("debtBills", this.debtBills);
        tag.put("debts", this.debts.save());
        return tag;
    }

    public void load(CompoundTag tag) {
        this.guests = tag.getInt("guests");
        this.earned.load(tag.getList("earned", Tag.TAG_COMPOUND));
        this.debtBills = tag.getInt("debtBills");
        this.debts.load(tag.getList("debts", Tag.TAG_COMPOUND));
    }
}
