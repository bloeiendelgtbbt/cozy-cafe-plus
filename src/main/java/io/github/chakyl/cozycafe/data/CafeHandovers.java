package io.github.chakyl.cozycafe.data;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bills that staff took from the tables but have not cashed at their cafe's manager yet. Every collected bill gets an
 * id; cashing it (or paying its amount out of pocket, or the owner forgiving it) removes it from here. When a shift
 * closes, everyone who still holds bills of that cafe from it is told once in chat (on joining, if offline).
 */
public class CafeHandovers extends SavedData {
    /** One bill still to be handed in. {@code remaining} is what it is still worth (less after out-of-pocket payments). */
    public static final class Pending {
        final UUID cafeId;
        String cafeName;
        ResourceLocation dimension;
        BlockPos pos;
        final UUID holder;
        final String holderName;
        ItemStack remaining;
        final long time;
        /** The holder was already told about it (at the close of the shift it was taken in). */
        boolean notified;

        Pending(UUID cafeId, String cafeName, ResourceLocation dimension, BlockPos pos, UUID holder, String holderName, ItemStack remaining, long time, boolean notified) {
            this.cafeId = cafeId;
            this.cafeName = cafeName;
            this.dimension = dimension;
            this.pos = pos;
            this.holder = holder;
            this.holderName = holderName;
            this.remaining = remaining;
            this.time = time;
            this.notified = notified;
        }

        CompoundTag save(UUID id) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putUUID("cafe", this.cafeId);
            tag.putString("cafeName", this.cafeName);
            tag.putString("dimension", this.dimension.toString());
            tag.put("pos", NbtUtils.writeBlockPos(this.pos));
            tag.putUUID("holder", this.holder);
            tag.putString("holderName", this.holderName);
            tag.put("remaining", this.remaining.save(new CompoundTag()));
            tag.putLong("time", this.time);
            tag.putBoolean("notified", this.notified);
            return tag;
        }

        static Pending load(CompoundTag tag) {
            return new Pending(tag.getUUID("cafe"), tag.getString("cafeName"), new ResourceLocation(tag.getString("dimension")),
                    NbtUtils.readBlockPos(tag.getCompound("pos")), tag.getUUID("holder"), tag.getString("holderName"),
                    ItemStack.of(tag.getCompound("remaining")), tag.getLong("time"), tag.getBoolean("notified"));
        }
    }

    /** What one staff member owes one cafe: shown in the owner's tab and in the staff member's own tablet. */
    public record Summary(UUID cafeId, String cafeName, ResourceLocation dimension, BlockPos pos, UUID holder, String holderName,
                          int count, List<ItemStack> amounts) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(this.cafeId);
            buf.writeUtf(this.cafeName);
            buf.writeResourceLocation(this.dimension);
            buf.writeBlockPos(this.pos);
            buf.writeUUID(this.holder);
            buf.writeUtf(this.holderName);
            buf.writeVarInt(this.count);
            buf.writeVarInt(this.amounts.size());
            for (ItemStack amount : this.amounts) {
                buf.writeItem(amount.copyWithCount(1));
                buf.writeVarInt(amount.getCount());
            }
        }

        public static Summary read(FriendlyByteBuf buf) {
            UUID cafeId = buf.readUUID();
            String cafeName = buf.readUtf();
            ResourceLocation dimension = buf.readResourceLocation();
            BlockPos pos = buf.readBlockPos();
            UUID holder = buf.readUUID();
            String holderName = buf.readUtf();
            int count = buf.readVarInt();
            int size = buf.readVarInt();
            List<ItemStack> amounts = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ItemStack item = buf.readItem();
                amounts.add(item.copyWithCount(buf.readVarInt()));
            }
            return new Summary(cafeId, cafeName, dimension, pos, holder, holderName, count, amounts);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("holder", this.holder);
            tag.putString("holderName", this.holderName);
            tag.putInt("count", this.count);
            ListTag list = new ListTag();
            for (ItemStack amount : this.amounts) {
                CompoundTag item = amount.copyWithCount(1).save(new CompoundTag());
                item.putInt("amount", amount.getCount());
                list.add(item);
            }
            tag.put("amounts", list);
            return tag;
        }

        /** The owner's tab reads these from the manager's client copy; cafe fields are filled in from the manager. */
        public static Summary load(CompoundTag tag, UUID cafeId, String cafeName, ResourceLocation dimension, BlockPos pos) {
            List<ItemStack> amounts = new ArrayList<>();
            for (Tag t : tag.getList("amounts", Tag.TAG_COMPOUND)) {
                CompoundTag item = (CompoundTag) t;
                amounts.add(ItemStack.of(item).copyWithCount(item.getInt("amount")));
            }
            return new Summary(cafeId, cafeName, dimension, pos, tag.getUUID("holder"), tag.getString("holderName"), tag.getInt("count"), amounts);
        }

        /** "12 Emerald, 3 Diamond" */
        public MutableComponent describeAmounts() {
            return describe(this.amounts);
        }
    }

    public enum CashResult { UNKNOWN, VOID, OTHER_CAFE, OK }

    private final Map<UUID, Pending> bills = new LinkedHashMap<>();

    public static CafeHandovers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CafeHandovers::load, CafeHandovers::new, "cozyrestaurant_handovers");
    }

    // ---- Changes ----

    /** A staff member took a paid bill from a table: it has to reach the cash register now. Returns the bill's id. */
    public static UUID billTaken(ServerPlayer holder, CafeManagerBlockEntity cafe, ItemStack earnings) {
        UUID id = UUID.randomUUID();
        CafeHandovers handovers = get(holder.server);
        handovers.bills.put(id, new Pending(cafe.getCafeId(), cafe.getCafeName(), cafe.getLevel().dimension().location(), cafe.getBlockPos(),
                holder.getUUID(), holder.getGameProfile().getName(), earnings.copy(), System.currentTimeMillis(), false));
        handovers.setDirty();
        handovers.changed(holder.server, cafe.getCafeId(), holder.getUUID());
        return id;
    }

    /** Is this bill still to be handed in, and to this cafe? */
    public CashResult check(@Nullable UUID billId, UUID cafeId) {
        if (billId == null) return CashResult.UNKNOWN;
        Pending bill = this.bills.get(billId);
        if (bill == null) return CashResult.VOID;
        return bill.cafeId.equals(cafeId) ? CashResult.OK : CashResult.OTHER_CAFE;
    }

    @Nullable
    public UUID holderOf(UUID billId) {
        Pending bill = this.bills.get(billId);
        return bill == null ? null : bill.holder;
    }

    /** The bill was cashed: returns what it is still worth (less than printed if part was paid out of pocket). */
    public ItemStack cashed(MinecraftServer server, UUID billId) {
        Pending bill = this.bills.remove(billId);
        if (bill == null) return ItemStack.EMPTY;
        this.setDirty();
        this.changed(server, bill.cafeId, bill.holder);
        return bill.remaining.copy();
    }

    public boolean hasBills(UUID holder, UUID cafeId) {
        for (Pending bill : this.bills.values()) {
            if (bill.holder.equals(holder) && bill.cafeId.equals(cafeId)) return true;
        }
        return false;
    }

    /**
     * The holder pays for their bills at this cafe out of pocket with {@code money} (oldest bills first, only bills in
     * that currency). Returns how many items were used; bills paid in full are void from now on.
     */
    public int payFromPocket(MinecraftServer server, UUID holder, UUID cafeId, ItemStack money) {
        List<Map.Entry<UUID, Pending>> own = new ArrayList<>();
        for (Map.Entry<UUID, Pending> entry : this.bills.entrySet()) {
            Pending bill = entry.getValue();
            if (bill.holder.equals(holder) && bill.cafeId.equals(cafeId) && ItemStack.isSameItemSameTags(bill.remaining, money)) own.add(entry);
        }
        own.sort(Comparator.comparingLong(entry -> entry.getValue().time));
        int left = money.getCount();
        int used = 0;
        for (Map.Entry<UUID, Pending> entry : own) {
            if (left <= 0) break;
            Pending bill = entry.getValue();
            int pay = Math.min(left, bill.remaining.getCount());
            bill.remaining.shrink(pay);
            left -= pay;
            used += pay;
            if (bill.remaining.isEmpty()) this.bills.remove(entry.getKey());
        }
        if (used > 0) {
            this.setDirty();
            this.changed(server, cafeId, holder);
        }
        return used;
    }

    /** The owner forgives all of a staff member's bills at this cafe; those bills are void from now on. */
    public boolean forgive(MinecraftServer server, UUID cafeId, UUID holder) {
        boolean removed = this.bills.values().removeIf(bill -> bill.cafeId.equals(cafeId) && bill.holder.equals(holder));
        if (removed) {
            this.setDirty();
            this.changed(server, cafeId, holder);
        }
        return removed;
    }

    /**
     * A shift of the cafe closed. Everyone who took bills during it and has not handed them in is told once, with
     * everything they still owe this cafe; offline players are told when they join.
     */
    public static void shiftClosed(MinecraftServer server, CafeManagerBlockEntity cafe) {
        CafeHandovers handovers = get(server);
        Set<UUID> holders = new HashSet<>();
        for (Pending bill : handovers.bills.values()) {
            if (bill.cafeId.equals(cafe.getCafeId()) && !bill.notified) {
                holders.add(bill.holder);
                bill.notified = true;
            }
        }
        if (holders.isEmpty()) return;
        handovers.setDirty();
        for (UUID holder : holders) {
            Summary summary = handovers.summary(cafe.getCafeId(), holder);
            if (summary == null) continue;
            Component message = Component.translatable("message.cozyrestaurant.handover.shift_closed",
                            Component.literal(cafe.getCafeName()).withStyle(ChatFormatting.YELLOW),
                            Component.literal(String.valueOf(summary.count())).withStyle(ChatFormatting.GOLD),
                            summary.describeAmounts().withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.RED);
            CafeReports.deliver(server, holder, message);
        }
    }

    // ---- Reading ----

    @Nullable
    private Summary summary(UUID cafeId, UUID holder) {
        List<Summary> list = this.summaries(bill -> bill.cafeId.equals(cafeId) && bill.holder.equals(holder));
        return list.isEmpty() ? null : list.get(0);
    }

    /** Per staff member, for the owner's tab. */
    public List<Summary> forCafe(UUID cafeId) {
        return this.summaries(bill -> bill.cafeId.equals(cafeId));
    }

    /** Per cafe, for the staff member's own tablet and the red labels above managers. */
    public List<Summary> forHolder(UUID holder) {
        return this.summaries(bill -> bill.holder.equals(holder));
    }

    private List<Summary> summaries(java.util.function.Predicate<Pending> filter) {
        Map<String, List<Pending>> groups = new LinkedHashMap<>();
        for (Pending bill : this.bills.values()) {
            if (filter.test(bill)) groups.computeIfAbsent(bill.cafeId + "/" + bill.holder, k -> new ArrayList<>()).add(bill);
        }
        List<Summary> result = new ArrayList<>();
        for (List<Pending> group : groups.values()) {
            Pending first = group.get(0);
            List<ItemStack> amounts = new ArrayList<>();
            for (Pending bill : group) addAmount(amounts, bill.remaining);
            result.add(new Summary(first.cafeId, first.cafeName, first.dimension, first.pos, first.holder, first.holderName, group.size(), amounts));
        }
        return result;
    }

    private static void addAmount(List<ItemStack> amounts, ItemStack add) {
        if (add.isEmpty()) return;
        for (ItemStack amount : amounts) {
            if (ItemStack.isSameItemSameTags(amount, add)) {
                amount.grow(add.getCount());
                return;
            }
        }
        amounts.add(add.copy());
    }

    public static MutableComponent describe(List<ItemStack> amounts) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < amounts.size(); i++) {
            if (i > 0) text.append(", ");
            text.append(amounts.get(i).getCount() + " ").append(amounts.get(i).getHoverName());
        }
        return text;
    }

    /** The cafe moved or was renamed: its pending bills follow it. */
    public static void cafeUpdated(CafeManagerBlockEntity cafe) {
        if (!(cafe.getLevel() instanceof ServerLevel level)) return;
        CafeHandovers handovers = get(level.getServer());
        boolean dirty = false;
        for (Pending bill : handovers.bills.values()) {
            if (!bill.cafeId.equals(cafe.getCafeId())) continue;
            if (!bill.pos.equals(cafe.getBlockPos()) || !bill.cafeName.equals(cafe.getCafeName()) || !bill.dimension.equals(level.dimension().location())) {
                bill.pos = cafe.getBlockPos();
                bill.cafeName = cafe.getCafeName();
                bill.dimension = level.dimension().location();
                dirty = true;
            }
        }
        if (dirty) handovers.setDirty();
    }

    // ---- Telling the clients ----

    /** The owner's tab (through the manager's update) and the holder's own list are refreshed. */
    private void changed(MinecraftServer server, UUID cafeId, UUID holder) {
        CafeManagerBlockEntity cafe = this.findCafe(server, cafeId);
        if (cafe != null) cafe.markUpdated();
        ServerPlayer player = server.getPlayerList().getPlayer(holder);
        if (player != null) sendTo(player, false);
    }

    @Nullable
    private CafeManagerBlockEntity findCafe(MinecraftServer server, UUID cafeId) {
        for (Pending bill : this.bills.values()) {
            if (bill.cafeId.equals(cafeId)) return loadedCafe(server, bill.dimension, bill.pos, cafeId);
        }
        for (CafeDirectory.Entry entry : CafeDirectory.get(server).all()) {
            if (entry.id().equals(cafeId)) return loadedCafe(server, entry.dimension(), entry.pos(), cafeId);
        }
        return null;
    }

    @Nullable
    private static CafeManagerBlockEntity loadedCafe(MinecraftServer server, ResourceLocation dimension, BlockPos pos, UUID cafeId) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof CafeManagerBlockEntity cafe && cafe.getCafeId().equals(cafeId) ? cafe : null;
    }

    /** Sends the player their own bills; {@code open} also opens their personal tablet. */
    public static void sendTo(ServerPlayer player, boolean open) {
        io.github.chakyl.cozycafe.network.EvilPacketsIHateThem.sendToPlayer(
                new io.github.chakyl.cozycafe.network.ClientBoundHandoversPacket(get(player.server).forHolder(player.getUUID()), open), player);
    }

    // ---- Saving ----

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        this.bills.forEach((id, bill) -> list.add(bill.save(id)));
        nbt.put("bills", list);
        return nbt;
    }

    private static CafeHandovers load(CompoundTag nbt) {
        CafeHandovers handovers = new CafeHandovers();
        for (Tag t : nbt.getList("bills", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) t;
            handovers.bills.put(tag.getUUID("id"), Pending.load(tag));
        }
        return handovers;
    }

    /** For tests. */
    public int size() {
        return this.bills.size();
    }
}
