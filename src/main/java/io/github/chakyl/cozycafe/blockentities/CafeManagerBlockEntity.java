package io.github.chakyl.cozycafe.blockentities;

import io.github.chakyl.cozycafe.CozyCafe;
import io.github.chakyl.cozycafe.compat.PrivitCompat;
import io.github.chakyl.cozycafe.data.CafeIndex;
import io.github.chakyl.cozycafe.data.CafeMenuEntry;
import io.github.chakyl.cozycafe.data.CafeRevenue;
import io.github.chakyl.cozycafe.gui.CafeManagerMenu;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import io.github.chakyl.cozycafe.util.CafeLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import io.github.chakyl.cozycafe.util.CafeAccess;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CafeManagerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_MENU_SIZE = 50;
    public static final int MIN_MENU_SIZE_TO_OPEN = 4;
    /** Most positions one order (and one plate) can hold. */
    public static final int MAX_ORDER_SIZE = 6;
    /** NPC customers order 1 to this many positions. */
    public static final int MAX_NPC_ORDER_SIZE = 3;
    /** A bill has this many payment slots, so one position never costs more than that many full stacks. */
    public static final int BILL_SLOTS = 5;
    public static final int MAX_WAITERS = 32;
    private static final int MAX_GROUP_SIZE = 4;

    private boolean open = false;
    private String cafeName = "Кафе";
    private final List<CafeMenuEntry> menu = new ArrayList<>();
    /** The item guests pay with. Empty until the owner picks one; the cafe can't open or edit its menu without it. */
    private ItemStack currency = ItemStack.EMPTY;
    /** "НПС и игроки" when true, "Игроки" when false. */
    private boolean npcCustomers = true;
    // Rating: every cafe starts with one 5-star rating.
    private long ratingSum = 5;
    private int ratingCount = 1;
    /** Ratings that came from guests (the starting 5 stars above is not one). */
    private int guestRatings = 0;
    /** Stays with the cafe through moves and renames: players' waypoints are tied to it. */
    private UUID cafeId = UUID.randomUUID();
    /** The cafe was opened at least once: from then on it is announced and on everyone's map. */
    private boolean everOpened = false;
    private final CafeRevenue revenue = new CafeRevenue();
    // Owner = the player who placed the manager. Only the owner and operators may manage the cafe.
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private final List<Waiter> waiters = new ArrayList<>();
    private final io.github.chakyl.cozycafe.data.CafeDebts debts = new io.github.chakyl.cozycafe.data.CafeDebts();
    /** The running shift (since the cafe opened); reported to the owner when it closes. */
    private final io.github.chakyl.cozycafe.data.CafeShift shift = new io.github.chakyl.cozycafe.data.CafeShift();
    /** Optional decorative sign that shows whether the cafe is open. */
    @Nullable
    private BlockPos linkedSign;

    public record Waiter(UUID id, String name) {
    }

    public CafeManagerBlockEntity(BlockPos pos, BlockState state) {
        super(CozyRegistry.BlockEntityRegistry.CAFE_MANAGER.get(), pos, state);
    }

    // ---- Ticking and NPC customers ----

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !this.open) return;
        if (level.getGameTime() % 20 == 0 && this.openError() != null) {
            // The claim was removed or now holds a second cafe: close the shift.
            this.setOpen(false, true);
            return;
        }
        if (this.npcCustomers && level.getGameTime() % this.getSpawnIntervalTicks() == 0) {
            this.assignCustomers(serverLevel);
        }
    }

    /** Seconds between NPC groups: the configured interval at 5 stars, one more interval for every missing star. */
    public long getSpawnIntervalTicks() {
        return 20L * CozyCafe.CONFIG.customerSpawnInterval.get() * (1 + (5 - this.getStars()));
    }

    public void assignCustomers(ServerLevel level) {
        Optional<PrivitCompat.ClaimInfo> claim = CafeLookup.claimOf(this);
        if (claim.isEmpty() || this.menu.isEmpty()) return;
        List<CafeMenuBlockEntity> tables = new ArrayList<>();
        for (CafeMenuBlockEntity table : CafeLookup.tablesInClaim(level, claim.get())) {
            if (table.canReceiveNewCustomer()) tables.add(table);
        }
        if (tables.isEmpty()) return;
        Collections.shuffle(tables);
        BlockPos spawn = CafeLookup.findBorderSpawn(level, claim.get().bounds(), tables.get(0).getBlockPos(), level.random);
        int spawned = 0;
        for (CafeMenuBlockEntity table : tables) {
            table.startVisit(this.worldPosition, this.rollCustomerSkin(), spawn);
            spawned++;
            if (spawned >= MAX_GROUP_SIZE || level.random.nextFloat() >= CozyCafe.CONFIG.groupCustomerChance.get()) break;
        }
    }

    private String rollCustomerSkin() {
        List<? extends String> usernames = CozyCafe.CONFIG.customerUsernames.get();
        if (!usernames.isEmpty() && this.level != null) {
            return usernames.get(this.level.getRandom().nextInt(usernames.size()));
        }
        return "MHF_Steve";
    }

    /** What an NPC customer orders: 1 to 3 different positions from the menu. */
    public List<ItemStack> rollNpcOrder() {
        List<CafeMenuEntry> choices = new ArrayList<>(this.menu);
        if (choices.isEmpty() || this.level == null) return List.of();
        Collections.shuffle(choices, new java.util.Random(this.level.random.nextLong()));
        int size = 1 + this.level.random.nextInt(Math.min(MAX_NPC_ORDER_SIZE, choices.size()));
        List<ItemStack> order = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            order.add(choices.get(i).item().copy());
        }
        return order;
    }

    /** Sends every customer of this cafe home (used when the cafe closes). */
    public void sendCustomersHome() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        for (BlockPos tablePos : List.copyOf(CafeIndex.tables(serverLevel.getServer()).getCafes(serverLevel))) {
            if (serverLevel.isLoaded(tablePos) && serverLevel.getBlockEntity(tablePos) instanceof CafeMenuBlockEntity table
                    && this.worldPosition.equals(table.getCafeManager())) {
                table.sendCustomerHome();
            }
        }
    }

    // ---- Opening ----

    /** Why the cafe can't open right now, or null if it can. */
    @Nullable
    public Component openError() {
        if (!(this.level instanceof ServerLevel serverLevel)) return null;
        Optional<PrivitCompat.ClaimInfo> claim = CafeLookup.claimOf(this);
        if (claim.isEmpty()) {
            return Component.translatable("block.cozyrestaurant.cafe_manager.not_in_claim");
        }
        if (CafeLookup.hasOtherManager(serverLevel, claim.get(), this.worldPosition)) {
            return Component.translatable("block.cozyrestaurant.cafe_manager.second_in_claim");
        }
        if (this.currency.isEmpty()) {
            return Component.translatable("block.cozyrestaurant.cafe_manager.no_currency");
        }
        if (this.menu.size() < MIN_MENU_SIZE_TO_OPEN) {
            return Component.translatable("block.cozyrestaurant.cafe_manager.menu_too_small", MIN_MENU_SIZE_TO_OPEN);
        }
        return null;
    }

    /** Toggles the shift from the GUI; returns the reason when the cafe can't open. */
    @Nullable
    public Component toggleOpenFromMenu() {
        if (!this.open) {
            Component error = this.openError();
            if (error != null) return error;
        }
        this.setOpen(!this.open, true);
        return null;
    }

    public boolean isOpen() {
        return this.open;
    }

    public void setOpen(boolean open, boolean sendCustomersHome) {
        boolean wasOpen = this.open;
        this.open = open;
        if (open && !wasOpen) this.shift.reset();
        boolean firstOpening = open && !this.everOpened && this.level instanceof ServerLevel;
        if (firstOpening) this.everOpened = true;
        this.markUpdated();
        if (this.level == null || this.level.isClientSide()) return;
        if (firstOpening) this.announceNewCafe((ServerLevel) this.level);
        if (!open && sendCustomersHome) this.sendCustomersHome();
        if (!open && wasOpen) this.reportShift();
        if (this.linkedSign != null && this.level.isLoaded(this.linkedSign) && this.level.getBlockEntity(this.linkedSign) instanceof CafeSignBlockEntity sign) {
            sign.setOpen(open);
        }
    }

    /** The shift is over (closed, or the manager was broken): the owner gets the report, now or when they join. */
    public void reportShift() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.owner == null) return;
        io.github.chakyl.cozycafe.data.CafeReports.deliver(serverLevel.getServer(), this.owner, this.shift.report(this.cafeName));
        this.shift.reset();
        this.setChanged();
    }

    public io.github.chakyl.cozycafe.data.CafeShift getShift() {
        return this.shift;
    }

    public void recordGuestServed() {
        this.shift.guestServed();
        this.setChanged();
    }

    public void recordBillPaid(ItemStack currency, int amount) {
        if (amount <= 0 || currency.isEmpty()) return;
        this.shift.billPaid(currency, amount);
        this.setChanged();
    }

    /** The first opening of a cafe is told to everyone on the server. */
    private void announceNewCafe(ServerLevel level) {
        BlockPos pos = this.worldPosition;
        net.minecraft.network.chat.MutableComponent where = Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(net.minecraft.ChatFormatting.GOLD);
        if (level.dimension() != Level.OVERWORLD) where.append(Component.literal(" (" + level.dimension().location().getPath() + ")").withStyle(net.minecraft.ChatFormatting.GRAY));
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.cozyrestaurant.cafe.new", Component.literal(this.cafeName).withStyle(net.minecraft.ChatFormatting.YELLOW), where)
                        .withStyle(net.minecraft.ChatFormatting.WHITE), false);
    }

    public UUID getCafeId() {
        return this.cafeId;
    }

    public boolean hasEverOpened() {
        return this.everOpened;
    }

    public boolean hasGuestRatings() {
        return this.guestRatings > 0;
    }

    /** Draws the border of the cafe's claim with particles at the manager's height. */
    public Component showCafeArea() {
        if (!(this.level instanceof ServerLevel serverLevel)) return null;
        Optional<PrivitCompat.ClaimInfo> claim = CafeLookup.claimOf(this);
        if (claim.isEmpty()) {
            return Component.translatable("block.cozyrestaurant.cafe_manager.not_in_claim").withStyle(ChatFormatting.RED);
        }
        BoundingBox b = claim.get().bounds();
        int perimeter = 2 * ((b.maxX() - b.minX() + 1) + (b.maxZ() - b.minZ() + 1));
        int step = Math.max(1, perimeter / 400);
        double y = this.worldPosition.getY() + 0.5;
        for (int x = b.minX(); x <= b.maxX() + 1; x += step) {
            particle(serverLevel, x, y, b.minZ());
            particle(serverLevel, x, y, b.maxZ() + 1);
        }
        for (int z = b.minZ(); z <= b.maxZ() + 1; z += step) {
            particle(serverLevel, b.minX(), y, z);
            particle(serverLevel, b.maxX() + 1, y, z);
        }
        return Component.translatable("block.cozyrestaurant.cafe_manager.area_shown", claim.get().name()).withStyle(ChatFormatting.GREEN);
    }

    private static void particle(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0, 0, 0, 0.0);
    }

    // ---- Menu, currency and prices ----

    public enum AddResult { ADDED, FULL, DUPLICATE, EMPTY }

    public List<CafeMenuEntry> getMenu() {
        return Collections.unmodifiableList(this.menu);
    }

    public AddResult addToMenu(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return AddResult.EMPTY;
        if (this.menu.size() >= MAX_MENU_SIZE) return AddResult.FULL;
        ItemStack item = stack.getItem().getDefaultInstance();
        for (CafeMenuEntry entry : this.menu) {
            if (entry.item().is(item.getItem())) return AddResult.DUPLICATE;
        }
        this.menu.add(new CafeMenuEntry(item, CafeMenuEntry.MIN_PRICE));
        this.markUpdated();
        return AddResult.ADDED;
    }

    public void removeFromMenu(int index) {
        if (index >= 0 && index < this.menu.size()) {
            this.menu.remove(index);
            this.markUpdated();
        }
    }

    public void setPrice(int index, int price) {
        if (index >= 0 && index < this.menu.size()) {
            this.menu.set(index, this.menu.get(index).withPrice(Mth.clamp(price, CafeMenuEntry.MIN_PRICE, this.getMaxPrice())));
            this.markUpdated();
        }
    }

    /** Price of an item on this cafe's menu, or -1 if it isn't on it. */
    public int priceOf(ItemStack stack) {
        for (CafeMenuEntry entry : this.menu) {
            if (entry.item().is(stack.getItem())) return entry.price();
        }
        return -1;
    }

    /** The highest price of one position: what fits into the bill's payment slots. */
    public int getMaxPrice() {
        return BILL_SLOTS * (this.currency.isEmpty() ? 64 : this.currency.getMaxStackSize());
    }

    public ItemStack getCurrency() {
        return this.currency;
    }

    public boolean hasCurrency() {
        return !this.currency.isEmpty();
    }

    public void setCurrency(ItemStack stack) {
        if (stack.isEmpty()) return;
        this.currency = stack.copyWithCount(1);
        int max = this.getMaxPrice();
        for (int i = 0; i < this.menu.size(); i++) {
            if (this.menu.get(i).price() > max) this.menu.set(i, this.menu.get(i).withPrice(max));
        }
        this.markUpdated();
    }

    public boolean hasNpcCustomers() {
        return this.npcCustomers;
    }

    public void setNpcCustomers(boolean npcCustomers) {
        this.npcCustomers = npcCustomers;
        this.markUpdated();
    }

    public String getCafeName() {
        return this.cafeName;
    }

    public void setCafeName(String cafeName) {
        this.cafeName = cafeName;
        this.markUpdated();
    }

    /** Resets the name, menu and currency. Owner, waiters, rating and stored money are kept. */
    public void clearCafeData() {
        if (this.level == null || this.level.isClientSide) return;
        this.setOpen(false, true);
        this.cafeName = "Кафе";
        this.menu.clear();
        this.currency = ItemStack.EMPTY;
        this.markUpdated();
    }

    // ---- Rating ----

    /** Adds a 0-5 star rating. */
    public void addRating(int stars) {
        this.ratingSum += Mth.clamp(stars, 0, 5);
        this.ratingCount++;
        this.guestRatings++;
        this.markUpdated();
    }

    public double getAverageRating() {
        return this.ratingCount == 0 ? 0 : (double) this.ratingSum / this.ratingCount;
    }

    public int getRatingCount() {
        return this.ratingCount;
    }

    /** The average rating rounded to whole stars (0-5). */
    public int getStars() {
        return Mth.clamp((int) Math.round(this.getAverageRating()), 0, 5);
    }

    // ---- Stored money ----

    /**
     * Cashes a collected bill: the earnings go into the cash register and the guest's rating (if any) counts.
     * Staff only; the bill must belong to this cafe. The bill item is used up.
     */
    public boolean cashBill(Player player, ItemStack billStack) {
        if (!io.github.chakyl.cozycafe.item.BillItem.isCollected(billStack) || this.level == null) return false;
        if (!CafeAccess.isStaff(player, this) && !CafeAccess.isOperator(player)) {
            player.displayClientMessage(CafeAccess.deniedMessage(this), true);
            return false;
        }
        if (!this.worldPosition.equals(io.github.chakyl.cozycafe.item.BillItem.managerPos(billStack))
                || !this.level.dimension().equals(io.github.chakyl.cozycafe.item.BillItem.dimension(billStack))) {
            player.displayClientMessage(Component.translatable("message.cozyrestaurant.bill.other_cafe").withStyle(ChatFormatting.RED), true);
            return false;
        }
        ItemStack earnings = io.github.chakyl.cozycafe.item.BillItem.earnings(billStack);
        int rating = io.github.chakyl.cozycafe.item.BillItem.rating(billStack);
        if (!earnings.isEmpty()) this.revenue.deposit(earnings);
        if (rating > 0) {
            this.ratingSum += Mth.clamp(rating, 1, 5);
            this.ratingCount++;
            this.guestRatings++;
        }
        billStack.shrink(1);
        this.markUpdated();
        player.displayClientMessage(Component.translatable("message.cozyrestaurant.bill.cashed", earnings.getCount(), earnings.getHoverName()).withStyle(ChatFormatting.GREEN), true);
        this.level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.2F);
        return true;
    }

    // ---- Debts ----

    public boolean owes(UUID player) {
        return this.debts.owes(player);
    }

    @Nullable
    public ItemStack debtOf(UUID player) {
        return this.debts.firstAmount(player);
    }

    public void addDebt(UUID player, String name, ItemStack currency, int count) {
        this.debts.add(player, name, currency, count);
        if (count > 0 && !currency.isEmpty()) this.shift.debtLeft(currency, count);
        this.markUpdated();
    }

    public void payDebt(UUID player, ItemStack currency, int count) {
        this.debts.pay(player, currency, count);
        this.markUpdated();
    }

    public void forgiveDebt(UUID player) {
        this.debts.forgive(player);
        this.markUpdated();
    }

    public io.github.chakyl.cozycafe.data.CafeDebts getDebts() {
        return this.debts;
    }

    public CafeRevenue getRevenue() {
        return this.revenue;
    }

    public void deposit(ItemStack stack) {
        this.revenue.deposit(stack);
        this.markUpdated();
    }

    public long withdrawRevenue(Player player) {
        long given = this.revenue.withdrawInto(player);
        if (given > 0) this.markUpdated();
        return given;
    }

    // ---- Owner and waiters ----

    public boolean hasOwner() {
        return this.owner != null;
    }

    @Nullable
    public UUID getOwner() {
        return this.owner;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public boolean isOwner(UUID playerId) {
        return playerId.equals(this.owner);
    }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.waiters.removeIf(w -> w.id().equals(owner));
        this.markUpdated();
    }

    public List<Waiter> getWaiters() {
        return Collections.unmodifiableList(this.waiters);
    }

    public boolean isWaiter(UUID playerId) {
        for (Waiter waiter : this.waiters) {
            if (waiter.id().equals(playerId)) return true;
        }
        return false;
    }

    public void addWaiter(UUID id, String name) {
        if (isWaiter(id)) return;
        this.waiters.add(new Waiter(id, name));
        this.markUpdated();
    }

    public boolean removeWaiter(UUID id) {
        boolean removed = this.waiters.removeIf(w -> w.id().equals(id));
        if (removed) this.markUpdated();
        return removed;
    }

    @Nullable
    public BlockPos getLinkedSign() {
        return this.linkedSign;
    }

    public void setLinkedSign(@Nullable BlockPos linkedSign) {
        this.linkedSign = linkedSign;
        this.markUpdated();
    }

    // ---- Plumbing ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cozyrestaurant.cafe_manager");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CafeManagerMenu(containerId, inventory, this);
    }

    public void markUpdated() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            io.github.chakyl.cozycafe.data.CafeDirectory.update(this);
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            CafeIndex.get(serverLevel.getServer()).add(serverLevel, this.worldPosition);
            // Placed again after a move: back on the map at the new spot.
            serverLevel.getServer().execute(() -> io.github.chakyl.cozycafe.data.CafeDirectory.update(this));
        }
    }

    /**
     * Everything that travels with the manager item when it is broken: name, menu, currency, settings, rating,
     * waiters and stored money. The owner is whoever places it next; the cafe is always closed after a move.
     */
    public void saveCafeData(CompoundTag nbt) {
        ListTag waiterList = new ListTag();
        for (Waiter waiter : this.waiters) {
            CompoundTag waiterTag = new CompoundTag();
            waiterTag.putUUID("id", waiter.id());
            waiterTag.putString("name", waiter.name());
            waiterList.add(waiterTag);
        }
        nbt.put("waiters", waiterList);
        nbt.putString("cafeName", this.cafeName);
        ListTag menuList = new ListTag();
        for (CafeMenuEntry entry : this.menu) {
            menuList.add(entry.save());
        }
        nbt.put("menu", menuList);
        if (!this.currency.isEmpty()) {
            nbt.put("currency", this.currency.save(new CompoundTag()));
        }
        nbt.putBoolean("npcCustomers", this.npcCustomers);
        nbt.putLong("ratingSum", this.ratingSum);
        nbt.putInt("ratingCount", this.ratingCount);
        nbt.putInt("guestRatings", this.guestRatings);
        nbt.putUUID("cafeId", this.cafeId);
        nbt.putBoolean("everOpened", this.everOpened);
        nbt.put("revenue", this.revenue.save());
        nbt.put("debts", this.debts.save());
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        this.saveCafeData(nbt);
        if (this.owner != null) {
            nbt.putUUID("owner", this.owner);
            nbt.putString("ownerName", this.ownerName);
        }
        nbt.putBoolean("open", this.open);
        nbt.put("shift", this.shift.save());
        if (this.linkedSign != null) {
            nbt.put("LinkedSign", NbtUtils.writeBlockPos(this.linkedSign));
        }
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.owner = nbt.hasUUID("owner") ? nbt.getUUID("owner") : null;
        this.ownerName = nbt.getString("ownerName");
        this.waiters.clear();
        for (Tag tag : nbt.getList("waiters", Tag.TAG_COMPOUND)) {
            CompoundTag waiterTag = (CompoundTag) tag;
            if (waiterTag.hasUUID("id")) {
                this.waiters.add(new Waiter(waiterTag.getUUID("id"), waiterTag.getString("name")));
            }
        }
        this.open = nbt.getBoolean("open");
        this.shift.load(nbt.getCompound("shift"));
        if (nbt.contains("cafeName", Tag.TAG_STRING)) {
            this.cafeName = nbt.getString("cafeName");
        }
        this.menu.clear();
        for (Tag tag : nbt.getList("menu", Tag.TAG_COMPOUND)) {
            CafeMenuEntry entry = CafeMenuEntry.load((CompoundTag) tag);
            if (!entry.item().isEmpty() && this.menu.size() < MAX_MENU_SIZE) this.menu.add(entry);
        }
        this.currency = nbt.contains("currency", Tag.TAG_COMPOUND) ? ItemStack.of(nbt.getCompound("currency")) : ItemStack.EMPTY;
        this.npcCustomers = !nbt.contains("npcCustomers") || nbt.getBoolean("npcCustomers");
        if (nbt.contains("ratingCount")) {
            this.ratingSum = nbt.getLong("ratingSum");
            this.ratingCount = nbt.getInt("ratingCount");
        } else {
            this.ratingSum = 5;
            this.ratingCount = 1;
        }
        // Older saves had no counter: everything past the starting 5 stars came from guests.
        this.guestRatings = nbt.contains("guestRatings") ? nbt.getInt("guestRatings") : Math.max(0, this.ratingCount - 1);
        if (nbt.hasUUID("cafeId")) this.cafeId = nbt.getUUID("cafeId");
        this.everOpened = nbt.getBoolean("everOpened") || this.open;
        this.revenue.load(nbt.getList("revenue", Tag.TAG_COMPOUND));
        this.debts.load(nbt.getList("debts", Tag.TAG_COMPOUND));
        this.linkedSign = nbt.contains("LinkedSign") ? NbtUtils.readBlockPos(nbt.getCompound("LinkedSign")) : null;
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

    /** Sends a reason (why something was refused) to a player's open cafe screen, or their action bar. */
    public static void feedback(ServerPlayer player, Component message, boolean success) {
        io.github.chakyl.cozycafe.network.ServerBoundAddWaiterPacket.feedback(player, message, success);
    }
}
