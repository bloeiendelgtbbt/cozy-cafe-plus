package io.github.chakyl.cozycafe.blockentities;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import com.mojang.authlib.GameProfile;
import io.github.chakyl.cozycafe.CozyCafe;
import io.github.chakyl.cozycafe.blocks.CafeMenuBlock;
import io.github.chakyl.cozycafe.data.CafeBill;
import io.github.chakyl.cozycafe.data.CafeIndex;
import io.github.chakyl.cozycafe.data.CafeMenuEntry;
import io.github.chakyl.cozycafe.entities.CustomerEntity;
import io.github.chakyl.cozycafe.gui.BillMenu;
import io.github.chakyl.cozycafe.item.BillItem;
import io.github.chakyl.cozycafe.item.ServingPlateItem;
import io.github.chakyl.cozycafe.network.ClientBoundOpenOrderPacket;
import io.github.chakyl.cozycafe.network.EvilPacketsIHateThem;
import io.github.chakyl.cozycafe.network.MenuOpener;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import io.github.chakyl.cozycafe.util.CafeAccess;
import io.github.chakyl.cozycafe.util.CafeLookup;
import io.github.chakyl.cozycafe.util.CustomerEntityUtils;
import io.github.chakyl.cozycafe.util.CustomerTarget;
import io.netty.util.internal.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A cafe table (the Cafe Menu block). One guest at a time: either an NPC (drawn by the block renderer) or a player
 * sitting within two blocks. A visit goes through {@link Phase}s: the order, one plate with the whole order, the meal,
 * then the bill. The bill stays on the table until a waiter picks it up, even after the guest has gone.
 */
public class CafeMenuBlockEntity extends BlockEntity {
    public static final int MAX_TRAVEL_TIME = 600;
    public static final int MAX_ORDER_MINUTES = 900;
    /** A player guest counts as seated within this many blocks of the table. */
    public static final int SEAT_RANGE = 2;
    /** A player guest farther away than this (or offline) has left the table. */
    private static final double LEFT_DISTANCE_SQR = 8.0 * 8.0;

    public enum Phase {
        /** No visit. */
        NONE,
        /** An NPC walks from the claim border to the table. */
        TRAVEL,
        /** An NPC reads the menu. */
        CHOOSING,
        /** The order is placed; the guest waits for the plate until the deadline. */
        WAITING,
        /** The plate is on the table: an NPC eats it, a player takes the food. */
        EATING,
        /** Finished; the guest waits for the bill (an NPC only until its patience runs out). */
        AFTER_MEAL,
        /** A player guest has the bill and pays it. */
        BILL
    }

    private Phase phase = Phase.NONE;
    /** An NPC sits at the table (the renderer draws it). */
    private boolean hasCustomer = false;
    private int customerTravelTime = -1;
    /** The seated player guest, or null. */
    @Nullable
    private UUID guestPlayer;
    private String guestName = "";
    @Nullable
    private BlockPos cafeManager;
    /** Where the NPC came from and walks back to. Null if it appeared at the table directly. */
    @Nullable
    private BlockPos exitPos;
    /** Ticks an NPC spent choosing the order. */
    private int orderTime = -1;
    private final List<ItemStack> order = new ArrayList<>();
    /** A player's order at the prices it was placed at (they go on the bill). */
    private final List<CafeMenuEntry> orderLines = new ArrayList<>();
    private ItemStack orderCurrency = ItemStack.EMPTY;
    /** Game time when the guest gives up waiting for the plate (WAITING) or for the bill (NPC in AFTER_MEAL). */
    private long orderDeadline = -1;
    private int orderDuration = 0;
    /** What lies (or lay) on the plate of this visit. */
    private final List<ItemStack> eating = new ArrayList<>();
    /** An NPC's eating progress; -1 for a player's plate waiting to be taken. */
    private int eatTime = -1;
    @Nullable
    private CafeBill bill;
    private String customerSkin = "";
    @Nullable
    private GameProfile gameProfile;
    /** The seated player is checked every 10 ticks. */
    private int guestCheckTimer = 0;
    /** Test hook: treat the guest as disconnected. */
    private boolean forceGuestOffline = false;
    /** A player's order lines already served (they go on the bill), at the prices of the order. */
    private final List<CafeMenuEntry> servedLines = new ArrayList<>();
    /** How many ticks the player guest said they would wait; the bill must come within as long after the meal. */
    private int guestWaitTicks = 0;
    /** How long the guest has been outside the cafe's claim (or offline) while owing, in ticks. */
    private int awayTicks = 0;
    /** Game time when food served to a player guest is thrown away; -1 while no food waits. */
    private long foodDeadline = -1;
    /** Served food was thrown away untouched: it still has to be paid. */
    private boolean foodWasted = false;
    /** The guest left owing: what lies on the table can be cleared away (red dot) until it is. */
    private boolean leftOwing = false;

    public CafeMenuBlockEntity(BlockPos pos, BlockState state) {
        super(CozyRegistry.BlockEntityRegistry.CAFE_MENU.get(), pos, state);
    }

    // ---- Ticking ----

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;
        if (this.guestPlayer != null) {
            if (++this.guestCheckTimer >= 10) {
                this.guestCheckTimer = 0;
                this.tickPlayerGuest((ServerLevel) level);
            }
            return;
        }
        switch (this.phase) {
            case TRAVEL -> {
                if (++this.customerTravelTime >= MAX_TRAVEL_TIME) this.onCustomerArrived(null);
                this.setChanged();
            }
            case CHOOSING -> {
                if (++this.orderTime >= CozyCafe.CONFIG.customerOrderTime.get()) {
                    CafeManagerBlockEntity cafe = this.getCafeManager(level);
                    List<ItemStack> rolled = cafe == null || !cafe.isOpen() ? List.<ItemStack>of() : cafe.rollNpcOrder();
                    if (rolled.isEmpty()) {
                        this.sendCustomerHome();
                        return;
                    }
                    this.order.addAll(rolled);
                    this.orderTime = -1;
                    this.startDeadline(CozyCafe.CONFIG.customerWaitTime.get() + CozyCafe.CONFIG.customerWaitTimePerExtraItem.get() * (rolled.size() - 1));
                    this.phase = Phase.WAITING;
                    this.setChangedForRender();
                } else {
                    this.setChanged();
                }
            }
            // Nothing happens to the cafe if an NPC leaves unserved or without a bill.
            case WAITING, AFTER_MEAL -> {
                if (this.hasCustomer && level.getGameTime() >= this.orderDeadline) this.sendCustomerHome();
            }
            case EATING -> {
                if (this.hasCustomer && ++this.eatTime >= CozyCafe.CONFIG.customerEatTime.get()) {
                    this.setPlate(false, true);
                    this.phase = Phase.AFTER_MEAL;
                    this.eatTime = -1;
                    this.startDeadline(CozyCafe.CONFIG.customerBillWaitTime.get());
                    this.setChangedForRender();
                } else {
                    this.setChanged();
                }
            }
            default -> {
            }
        }
    }

    private void tickPlayerGuest(ServerLevel level) {
        ServerPlayer guest = this.forceGuestOffline ? null : level.getServer().getPlayerList().getPlayer(this.guestPlayer);
        long now = level.getGameTime();
        // Served food waits a limited time, then it is thrown away (and still has to be paid).
        if (this.foodDeadline > 0 && now >= this.foodDeadline) this.wasteFood(guest);
        switch (this.phase) {
            case WAITING -> {
                if (now >= this.orderDeadline) {
                    this.orderTimedOut(level, guest);
                    return;
                }
            }
            case AFTER_MEAL -> {
                if (this.orderDeadline > 0 && now >= this.orderDeadline) {
                    // Nobody brought the bill in time: the meal is on the house.
                    CafeManagerBlockEntity cafe = this.getCafeManager(level);
                    if (guest != null) guest.displayClientMessage(Component.translatable("message.cozyrestaurant.bill.on_the_house", cafe == null ? "" : cafe.getCafeName()).withStyle(ChatFormatting.GREEN), false);
                    this.endPlayerVisit();
                    return;
                }
            }
            case BILL -> {
                if (this.bill != null && this.bill.isDebt()) {
                    // An old debt: nothing new can be owed, the bill is just left when the guest walks off.
                    if (!this.isAround(guest)) this.leaveBill(level, guest);
                    return;
                }
            }
            default -> {
            }
        }
        // Getting up or walking around the cafe is fine; being outside its claim too long once something was served is leaving.
        if (this.owesNow() && !this.isInCafe(level, guest)) {
            this.awayTicks += 10;
            if (this.awayTicks >= CozyCafe.CONFIG.guestAwayLimit.get()) this.leftWithoutPaying(level, guest);
        } else {
            this.awayTicks = 0;
        }
    }

    /** Something was served and isn't settled: leaving now means a debt. */
    private boolean owesNow() {
        return switch (this.phase) {
            case WAITING, EATING -> !this.servedLines.isEmpty();
            case AFTER_MEAL -> this.foodWasted;
            case BILL -> this.bill != null && !this.bill.isPaid();
            default -> false;
        };
    }

    /** The guest is online and inside the cafe's claim (without a claim: within 32 blocks of the table). */
    private boolean isInCafe(ServerLevel level, @Nullable ServerPlayer guest) {
        if (guest == null || guest.level() != level) return false;
        java.util.Optional<io.github.chakyl.cozycafe.compat.PrivitCompat.ClaimInfo> claim = io.github.chakyl.cozycafe.compat.PrivitCompat.claimAt(level, this.worldPosition);
        if (claim.isPresent()) return claim.get().bounds().isInside(guest.blockPosition());
        return guest.distanceToSqr(this.worldPosition.getCenter()) <= 32.0 * 32.0;
    }

    /** Gone for too long while owing: what was served (or the unpaid rest of the bill) becomes a debt. */
    private void leftWithoutPaying(ServerLevel level, @Nullable ServerPlayer guest) {
        if (this.phase == Phase.BILL && this.bill != null) {
            this.leaveBill(level, guest);
            return;
        }
        int sum = this.servedLines.stream().mapToInt(CafeMenuEntry::price).sum();
        this.leaveOwing(level, guest, sum, this.orderCurrency);
        BlockState state = this.getBlockState();
        this.leftOwing = state.getValue(CafeMenuBlock.DISH) || state.getValue(CafeMenuBlock.DIRTY);
        this.endPlayerVisit();
    }

    /** The guest walks off from the bill: the missing part becomes a debt, the bill stays for the waiter. */
    private void leaveBill(ServerLevel level, @Nullable ServerPlayer guest) {
        if (this.bill != null && !this.bill.isPaid()) {
            int owed = this.bill.close();
            this.countPayment();
            if (!this.bill.isDebt()) this.leaveOwing(level, guest, owed, this.bill.getCurrency());
            else if (guest != null) this.tellDebt(guest, level);
        }
        this.endPlayerVisit();
    }

    /** The served food waited too long: thrown away, the dirty plate stays. It still has to be paid. */
    private void wasteFood(@Nullable ServerPlayer guest) {
        this.foodDeadline = -1;
        if (!this.getBlockState().getValue(CafeMenuBlock.DISH)) return;
        this.eating.clear();
        this.setPlate(false, true);
        this.foodWasted = true;
        if (guest != null) guest.displayClientMessage(Component.translatable("message.cozyrestaurant.order.food_wasted").withStyle(ChatFormatting.RED), false);
        if (this.phase == Phase.EATING) this.startBillWait();
        this.setChangedForRender();
    }

    /** The guest's own waiting time ran out before everything was served. */
    private void orderTimedOut(ServerLevel level, @Nullable ServerPlayer guest) {
        if (this.servedLines.isEmpty()) {
            // Nothing at all: the order is cancelled, the guest gets up and the cafe gets 0 stars for it.
            CafeManagerBlockEntity cafe = this.getCafeManager(level);
            if (cafe != null) cafe.addRating(0);
            if (guest != null) {
                guest.stopRiding();
                guest.displayClientMessage(Component.translatable("message.cozyrestaurant.order.timed_out").withStyle(ChatFormatting.RED), false);
            }
            this.endPlayerVisit();
            return;
        }
        // Part of it came: the rest is dropped without a penalty, the served part is paid as usual.
        this.order.clear();
        this.orderLines.clear();
        if (guest != null) guest.displayClientMessage(Component.translatable("message.cozyrestaurant.order.rest_timed_out").withStyle(ChatFormatting.YELLOW), false);
        this.nothingPending(guest);
    }

    /** Nothing more is coming: with food on the table the guest is to take it, otherwise the bill is awaited. */
    private void nothingPending(@Nullable ServerPlayer guest) {
        this.orderDeadline = -1;
        if (this.getBlockState().getValue(CafeMenuBlock.DISH)) {
            this.phase = Phase.EATING;
            if (guest != null) this.tellReady(guest);
        } else {
            this.startBillWait();
        }
        this.setChangedForRender();
    }

    private void tellReady(ServerPlayer guest) {
        CafeManagerBlockEntity cafe = this.getCafeManager(this.level);
        int minutes = Math.max(1, CozyCafe.CONFIG.servedFoodKeepTime.get() / 1200);
        guest.displayClientMessage(Component.translatable("message.cozyrestaurant.order.ready", cafe == null ? "" : cafe.getCafeName(), minutes).withStyle(ChatFormatting.GREEN), false);
    }

    /** After the meal the bill has to come within the guest's own waiting time, or the meal is on the house. */
    private void startBillWait() {
        this.phase = Phase.AFTER_MEAL;
        this.startDeadline(this.guestWaitTicks > 0 ? this.guestWaitTicks : CozyCafe.CONFIG.customerBillWaitTime.get());
    }

    /** The guest cancels what hasn't been served yet, without any penalty. */
    public void cancelOrder(ServerPlayer player) {
        if (this.phase != Phase.WAITING || !player.getUUID().equals(this.guestPlayer)) return;
        this.order.clear();
        this.orderLines.clear();
        if (this.servedLines.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.cozyrestaurant.order.cancelled").withStyle(ChatFormatting.YELLOW), false);
            this.endPlayerVisit();
            return;
        }
        player.displayClientMessage(Component.translatable("message.cozyrestaurant.order.rest_cancelled").withStyle(ChatFormatting.YELLOW), false);
        this.nothingPending(player);
    }

    /** Test hook: the guest's connection drops (true) or comes back (false). */
    public void setGuestOfflineForTest(boolean offline) {
        this.forceGuestOffline = offline;
    }

    /** Test hook: the served food has waited long enough to be thrown away. */
    public void expireFoodForTest() {
        if (this.foodDeadline > 0 && this.level != null) this.foodDeadline = this.level.getGameTime();
    }

    /** Debug/test hook: the guest's patience runs out now. */
    public void expireDeadline() {
        if (this.orderDeadline > 0 && this.level != null) this.orderDeadline = this.level.getGameTime();
    }

    /** The guest is online, in this dimension and near the table. */
    private boolean isAround(@Nullable ServerPlayer guest) {
        return guest != null && guest.level() == this.level && guest.distanceToSqr(this.worldPosition.getCenter()) <= LEFT_DISTANCE_SQR;
    }

    private void leaveOwing(ServerLevel level, @Nullable ServerPlayer guest, int amount, ItemStack currency) {
        CafeManagerBlockEntity cafe = this.getCafeManager(level);
        if (cafe == null || amount <= 0 || this.guestPlayer == null) return;
        cafe.addDebt(this.guestPlayer, this.guestName, currency, amount);
        if (guest != null) this.tellDebt(guest, level);
    }

    private void tellDebt(ServerPlayer guest, ServerLevel level) {
        CafeManagerBlockEntity cafe = this.getCafeManager(level);
        ItemStack debt = cafe == null ? null : cafe.debtOf(guest.getUUID());
        if (debt == null) return;
        guest.displayClientMessage(Component.translatable("message.cozyrestaurant.debt.left", cafe.getCafeName(), debt.getCount(), debt.getHoverName()).withStyle(ChatFormatting.RED), false);
    }

    private void startDeadline(int ticks) {
        this.orderDuration = ticks;
        this.orderDeadline = this.level.getGameTime() + ticks;
    }

    // ---- NPC visits ----

    @Nullable
    public CafeManagerBlockEntity getCafeManager(Level level) {
        if (this.cafeManager != null && level.isLoaded(this.cafeManager) && level.getBlockEntity(this.cafeManager) instanceof CafeManagerBlockEntity cafe) {
            return cafe;
        }
        return null;
    }

    /** An NPC customer of the cafe at {@code managerPos} heads for this table, starting at {@code spawn} (or appearing here). */
    public void startVisit(BlockPos managerPos, String skin, @Nullable BlockPos spawn) {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        this.cafeManager = managerPos;
        this.exitPos = spawn;
        this.setCustomerSkinFromUsername(skin);
        if (spawn == null) {
            this.customerTravelTime = -1;
            this.arrive();
            return;
        }
        this.customerTravelTime = 0;
        this.phase = Phase.TRAVEL;
        CustomerEntity customer = CozyRegistry.EntityRegistry.CUSTOMER.get().create(serverLevel);
        if (customer != null) {
            CustomerEntityUtils.spawnCustomerAndTarget(spawn, serverLevel, skin, this.worldPosition, customer, CustomerTarget.MENU);
        }
        this.setChanged();
    }

    public void onCustomerArrived(@Nullable PathfinderMob customer) {
        if (this.level == null || this.level.isClientSide() || this.hasCustomer || this.phase != Phase.TRAVEL) return;
        if (customer instanceof CustomerEntity customerEntity) {
            customerEntity.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        }
        this.customerTravelTime = -1;
        if (this.getCafeManager(this.level) == null || this.guestPlayer != null) {
            this.reset();
            return;
        }
        this.arrive();
    }

    private void arrive() {
        this.hasCustomer = true;
        this.phase = Phase.CHOOSING;
        this.orderTime = 0;
        this.setChangedForRender();
    }

    // ---- Player guests ----

    /** A player sits (rides anything) within {@link #SEAT_RANGE} blocks of this table. */
    public boolean isSeatedHere(Player player) {
        if (!player.isPassenger() || player.level() != this.level) return false;
        BlockPos at = player.blockPosition();
        return Math.abs(at.getX() - this.worldPosition.getX()) <= SEAT_RANGE
                && Math.abs(at.getZ() - this.worldPosition.getZ()) <= SEAT_RANGE
                && Math.abs(at.getY() - this.worldPosition.getY()) <= SEAT_RANGE;
    }

    /** No guest, plate or bill: a new visit can start. */
    public boolean isFree() {
        BlockState state = this.getBlockState();
        return this.phase == Phase.NONE && !this.hasCustomer && this.customerTravelTime == -1 && this.guestPlayer == null
                && this.bill == null && !state.getValue(CafeMenuBlock.DIRTY) && !state.getValue(CafeMenuBlock.DISH);
    }

    /** Why this player can't order here right now, or null if they can. */
    @Nullable
    public Component orderError(ServerPlayer player) {
        if (!(this.level instanceof ServerLevel serverLevel)) return Component.empty();
        CafeManagerBlockEntity cafe = CafeLookup.cafeAt(serverLevel, this.worldPosition);
        if (cafe == null) return Component.translatable("message.cozyrestaurant.order.no_cafe");
        if (!cafe.isOpen()) return Component.translatable("message.cozyrestaurant.order.closed", cafe.getCafeName());
        if (!this.isSeatedHere(player)) return Component.translatable("message.cozyrestaurant.order.sit_down");
        if (!this.isFree()) return Component.translatable("message.cozyrestaurant.order.table_busy");
        return null;
    }

    /** RMB by a player who might want to order: opens the order screen or says why not. */
    public void requestOrderScreen(ServerPlayer player) {
        Component error = this.orderError(player);
        if (error != null) {
            player.displayClientMessage(error.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        CafeManagerBlockEntity cafe = CafeLookup.cafeAt((ServerLevel) this.level, this.worldPosition);
        ItemStack debt = cafe.debtOf(player.getUUID());
        if (debt != null) {
            // A debtor gets the old bill instead of the menu.
            this.guestPlayer = player.getUUID();
            this.guestName = player.getGameProfile().getName();
            this.cafeManager = cafe.getBlockPos();
            this.bill = CafeBill.forDebt(player.getUUID(), this.guestName, debt);
            this.attachBillListener();
            this.phase = Phase.BILL;
            this.setChangedForRender();
            player.displayClientMessage(Component.translatable("message.cozyrestaurant.debt.pay_first", debt.getCount(), debt.getHoverName()).withStyle(ChatFormatting.GOLD), false);
            this.openBill(player);
            return;
        }
        EvilPacketsIHateThem.sendToPlayer(new ClientBoundOpenOrderPacket(this.worldPosition, cafe.getCafeName(), cafe.getCurrency(), cafe.getMenu()), player);
    }

    /**
     * Places a player's order: the chosen menu positions (1 to {@link CafeManagerBlockEntity#MAX_ORDER_SIZE}, no
     * repeats) and how long they will wait, in minutes. Returns why it failed, or null.
     */
    @Nullable
    public Component placeOrder(ServerPlayer player, List<ItemStack> wanted, int minutes) {
        Component error = this.orderError(player);
        if (error != null) return error;
        CafeManagerBlockEntity cafe = CafeLookup.cafeAt((ServerLevel) this.level, this.worldPosition);
        if (cafe.owes(player.getUUID())) return Component.translatable("message.cozyrestaurant.debt.cannot_order");
        if (wanted.isEmpty() || wanted.size() > CafeManagerBlockEntity.MAX_ORDER_SIZE) {
            return Component.translatable("message.cozyrestaurant.order.bad_size", CafeManagerBlockEntity.MAX_ORDER_SIZE);
        }
        if (minutes < 1 || minutes > MAX_ORDER_MINUTES) {
            return Component.translatable("message.cozyrestaurant.order.bad_time", MAX_ORDER_MINUTES);
        }
        List<CafeMenuEntry> lines = new ArrayList<>();
        for (ItemStack item : wanted) {
            CafeMenuEntry entry = cafe.getMenu().stream().filter(e -> ItemStack.isSameItemSameTags(e.item(), item)).findFirst().orElse(null);
            if (entry == null || lines.stream().anyMatch(l -> ItemStack.isSameItemSameTags(l.item(), item))) {
                // The menu changed while the screen was open, or the same position was sent twice.
                return Component.translatable("message.cozyrestaurant.order.menu_changed");
            }
            lines.add(entry);
        }
        this.guestPlayer = player.getUUID();
        this.guestName = player.getGameProfile().getName();
        this.cafeManager = cafe.getBlockPos();
        this.orderLines.clear();
        this.orderLines.addAll(lines);
        this.order.clear();
        for (CafeMenuEntry line : lines) this.order.add(line.item().copyWithCount(1));
        this.orderCurrency = cafe.getCurrency().copyWithCount(1);
        this.servedLines.clear();
        this.awayTicks = 0;
        this.foodWasted = false;
        this.leftOwing = false;
        this.guestWaitTicks = minutes * 1200;
        this.startDeadline(this.guestWaitTicks);
        this.phase = Phase.WAITING;
        this.setChangedForRender();
        return null;
    }

    /** Served food lies on the table for this player. */
    public boolean hasFoodFor(Player player) {
        return this.guestPlayer != null && player.getUUID().equals(this.guestPlayer) && this.getBlockState().getValue(CafeMenuBlock.DISH)
                && (this.phase == Phase.WAITING || this.phase == Phase.EATING);
    }

    /** The guest takes the food off the served plate; the dirty plate stays on the table for the waiter. */
    public void handleTakeFood(ServerPlayer player) {
        if (!this.hasFoodFor(player)) return;
        for (ItemStack food : this.eating) giveOrDrop(player, food.copy());
        this.eating.clear();
        this.setPlate(false, true);
        this.foodDeadline = -1;
        if (this.phase == Phase.EATING) this.startBillWait();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
        this.setChangedForRender();
    }

    private void endPlayerVisit() {
        this.guestPlayer = null;
        this.forceGuestOffline = false;
        this.servedLines.clear();
        this.guestWaitTicks = 0;
        this.awayTicks = 0;
        this.foodDeadline = -1;
        this.foodWasted = false;
        this.guestName = "";
        this.phase = Phase.NONE;
        this.order.clear();
        this.orderLines.clear();
        this.orderCurrency = ItemStack.EMPTY;
        this.orderDeadline = -1;
        this.orderDuration = 0;
        this.eatTime = -1;
        this.setChangedForRender();
    }

    @Nullable
    private ServerPlayer onlineGuest() {
        if (this.guestPlayer == null || !(this.level instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getServer().getPlayerList().getPlayer(this.guestPlayer);
    }

    /** The bill's player is still around the table (to pay it). */
    private boolean billGuestPresent() {
        if (this.bill == null || this.bill.isNpc() || !(this.level instanceof ServerLevel serverLevel)) return false;
        ServerPlayer guest = serverLevel.getServer().getPlayerList().getPlayer(this.bill.getGuest());
        return guest != null && guest.level() == this.level && guest.distanceToSqr(this.worldPosition.getCenter()) <= LEFT_DISTANCE_SQR;
    }

    // ---- Serving ----

    /** A staff member serves the plate in their hand. The plate has to hold exactly what was ordered. */
    public void handleServe(Player player, ItemStack handStack) {
        if (this.level == null || this.phase != Phase.WAITING) return;
        CafeManagerBlockEntity cafe = this.getCafeManager(this.level);
        if (cafe == null) {
            this.sendCustomerHome();
            return;
        }
        if (!CafeAccess.canServe(player, cafe)) {
            player.displayClientMessage(CafeAccess.cannotServeMessage(), true);
            return;
        }
        if (!handStack.is(CozyRegistry.ItemRegistry.SERVING_PLATE.get())) {
            player.displayClientMessage(Component.translatable("block.cozyrestaurant.cafe_menu.needs_plate").withStyle(ChatFormatting.RED), true);
            fail(player);
            return;
        }
        if (this.guestPlayer != null) {
            this.servePlayerGuest(player, handStack, cafe);
            return;
        }
        if (!ServingPlateItem.matchesOrder(ServingPlateItem.getItems(handStack), this.order)) {
            player.displayClientMessage(Component.translatable("block.cozyrestaurant.cafe_menu.wrong_order").withStyle(ChatFormatting.RED), true);
            fail(player);
            return;
        }
        // Plates are single-use: the served plate stays on the table and later becomes a dirty plate.
        if (!player.isCreative()) handStack.shrink(1);
        this.eating.clear();
        for (ItemStack item : this.order) this.eating.add(item.copy());
        this.order.clear();
        this.orderDeadline = -1;
        this.phase = Phase.EATING;
        this.eatTime = 0;
        cafe.recordGuestServed();
        this.setPlate(true, false);
        ((ServerLevel) this.level).sendParticles(ParticleTypes.HAPPY_VILLAGER,
                this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5, 5, 0.5, 0.5, 0.5, 1.0);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        this.setChangedForRender();
    }

    /**
     * A player's order may come in parts: the plate holds any of the dishes still to be served. A new part needs a
     * clean table. When the last part comes, the guest is told the order is ready.
     */
    private void servePlayerGuest(Player player, ItemStack handStack, CafeManagerBlockEntity cafe) {
        BlockState state = this.getBlockState();
        if (state.getValue(CafeMenuBlock.DISH) || state.getValue(CafeMenuBlock.DIRTY)) {
            player.displayClientMessage(Component.translatable("block.cozyrestaurant.cafe_menu.clear_first").withStyle(ChatFormatting.RED), true);
            fail(player);
            return;
        }
        List<ItemStack> plate = ServingPlateItem.getItems(handStack);
        List<Integer> matched = new ArrayList<>();
        for (ItemStack food : plate) {
            int found = -1;
            for (int i = 0; i < this.order.size(); i++) {
                if (!matched.contains(i) && this.order.get(i).is(food.getItem())) {
                    found = i;
                    break;
                }
            }
            if (found < 0) break;
            matched.add(found);
        }
        if (plate.isEmpty() || matched.size() != plate.size()) {
            player.displayClientMessage(Component.translatable("block.cozyrestaurant.cafe_menu.wrong_part").withStyle(ChatFormatting.RED), true);
            fail(player);
            return;
        }
        if (!player.isCreative()) handStack.shrink(1);
        if (this.servedLines.isEmpty()) cafe.recordGuestServed();
        matched.sort(java.util.Comparator.reverseOrder());
        List<CafeMenuEntry> served = new ArrayList<>();
        for (int index : matched) {
            if (index < this.orderLines.size()) served.add(0, this.orderLines.remove(index));
            this.order.remove(index);
        }
        this.servedLines.addAll(served);
        this.eating.clear();
        for (ItemStack food : plate) this.eating.add(food.copyWithCount(1));
        this.setPlate(true, false);
        this.foodDeadline = this.level.getGameTime() + CozyCafe.CONFIG.servedFoodKeepTime.get();
        ((ServerLevel) this.level).sendParticles(ParticleTypes.HAPPY_VILLAGER,
                this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5, 5, 0.5, 0.5, 0.5, 1.0);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (this.order.isEmpty()) {
            this.nothingPending(this.onlineGuest());
        } else {
            this.setChangedForRender();
        }
    }

    private static void fail(Player player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * Staff clear the table: a dirty plate (single-use, it can't be washed), or a full plate a player guest left
     * behind (it comes back as it was served).
     */
    public boolean handleClearPlate(Player player) {
        if (this.level == null) return false;
        BlockState state = this.getBlockState();
        boolean dirty = state.getValue(CafeMenuBlock.DIRTY);
        // Food still meant for the guest (an NPC eating, or a player's served part) stays.
        boolean forGuest = this.phase == Phase.EATING || (this.guestPlayer != null && this.phase == Phase.WAITING);
        boolean abandoned = state.getValue(CafeMenuBlock.DISH) && !forGuest;
        if (!dirty && !abandoned) return false;
        CafeManagerBlockEntity cafe = this.getCafeManager(this.level);
        if (cafe != null && !CafeAccess.canServe(player, cafe) && !CafeAccess.isOperator(player)) {
            player.displayClientMessage(CafeAccess.platesStaffOnlyMessage(), true);
            return true;
        }
        ItemStack plate = dirty ? CozyRegistry.ItemRegistry.DIRTY_SERVING_PLATE.get().getDefaultInstance()
                : ServingPlateItem.withItems(CozyRegistry.ItemRegistry.SERVING_PLATE.get().getDefaultInstance(), this.eating);
        giveOrDrop(player, plate);
        this.setPlate(false, false);
        if (this.leftOwing) {
            this.leftOwing = false;
            this.setChangedForRender();
        }
        return true;
    }

    // ---- The bill ----

    /** A waiter puts a blank bill on the table of a guest who has finished eating. */
    public void handleIssueBill(ServerPlayer player, ItemStack blank) {
        if (this.level == null) return;
        CafeManagerBlockEntity cafe = this.getCafeManager(this.level);
        if (cafe == null) {
            player.displayClientMessage(CafeAccess.noCafeMessage(), true);
            return;
        }
        if (!CafeAccess.canServe(player, cafe)) {
            player.displayClientMessage(CafeAccess.cannotServeMessage(), true);
            return;
        }
        if (this.bill != null) {
            player.displayClientMessage(Component.translatable("message.cozyrestaurant.bill.already").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (this.phase != Phase.AFTER_MEAL) {
            player.displayClientMessage(Component.translatable("message.cozyrestaurant.bill.not_yet").withStyle(ChatFormatting.RED), true);
            fail(player);
            return;
        }
        BlockState state = this.getBlockState();
        if (state.getValue(CafeMenuBlock.DIRTY) || state.getValue(CafeMenuBlock.DISH)) {
            player.displayClientMessage(Component.translatable("message.cozyrestaurant.bill.plate_first").withStyle(ChatFormatting.RED), true);
            fail(player);
            return;
        }
        if (!player.isCreative()) blank.shrink(1);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (this.guestPlayer == null) {
            // An NPC pays at once, by how filling the food was, and leaves; the bill waits on the table.
            this.bill = CafeBill.forNpc(this.eating, npcCurrency(), npcPayment(this.eating, this.level.random));
            this.attachBillListener();
            this.countPayment();
            this.sendCustomerHome();
            return;
        }
        this.bill = CafeBill.forPlayer(this.guestPlayer, this.guestName, this.servedLines, this.orderCurrency);
        this.attachBillListener();
        this.phase = Phase.BILL;
        this.orderDeadline = -1;
        this.awayTicks = 0;
        ServerPlayer guest = this.onlineGuest();
        if (guest != null) {
            guest.displayClientMessage(Component.translatable("message.cozyrestaurant.bill.brought", this.bill.getSum(), this.bill.getCurrency().getHoverName()).withStyle(ChatFormatting.GOLD), false);
        }
        this.setChangedForRender();
    }

    /** The guest opens the bill to pay and rate. */
    public void openBill(ServerPlayer player) {
        if (this.bill == null || !player.getUUID().equals(this.bill.getGuest())) return;
        CafeManagerBlockEntity cafe = this.getCafeManager(this.level);
        if (this.bill.isDebt() && cafe != null && !cafe.owes(player.getUUID())) {
            // The owner forgave the debt meanwhile: the guest gets back what they put in and can order.
            for (ItemStack stack : this.bill.takeAll()) giveOrDrop(player, stack);
            this.bill = null;
            this.endPlayerVisit();
            this.requestOrderScreen(player);
            return;
        }
        MenuOpener.open(player, Component.translatable("container.cozyrestaurant.bill"),
                (id, inventory, p) -> new BillMenu(id, inventory, this), buffer -> buffer.writeBlockPos(this.worldPosition));
    }

    /**
     * A waiter picks the bill up. A paid bill turns into a collected bill with the earnings; everything else in it
     * is the waiter's tip. An unpaid bill can only be cleared away once its guest has gone.
     */
    public void handleCollectBill(ServerPlayer player) {
        if (this.bill == null || this.level == null) return;
        CafeManagerBlockEntity cafe = this.getCafeManager(this.level);
        if (cafe == null) {
            player.displayClientMessage(CafeAccess.noCafeMessage(), true);
            return;
        }
        if (!CafeAccess.canServe(player, cafe)) {
            player.displayClientMessage(CafeAccess.cannotServeMessage(), true);
            return;
        }
        List<ItemStack> tips = new ArrayList<>();
        if (this.bill.isPaid()) {
            ItemStack earnings = this.bill.takeEarnings(tips);
            if (this.bill.isDebt() && !earnings.isEmpty()) cafe.payDebt(this.bill.getGuest(), earnings, earnings.getCount());
            if (earnings.isEmpty() && this.bill.isNpc()) {
                // Nothing filling at all (grass, blocks) vs. real food that was just too little to pay for.
                double points = 0;
                for (CafeMenuEntry line : this.bill.getLines()) points += foodPoints(line.item());
                player.displayClientMessage(Component.translatable(points > 0
                        ? "message.cozyrestaurant.bill.npc_too_little" : "message.cozyrestaurant.bill.npc_nothing").withStyle(ChatFormatting.YELLOW), true);
            } else if (earnings.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.cozyrestaurant.bill.abandoned").withStyle(ChatFormatting.YELLOW), true);
            } else {
                ItemStack collected = BillItem.collected(CozyRegistry.ItemRegistry.BILL.get().getDefaultInstance(), this.level,
                        this.cafeManager, earnings, this.bill.needsRating() ? this.bill.getRating() : 0, this.bill.getGuestName());
                // From now on the cafe keeps track of the bill until it is cashed at the manager.
                BillItem.setId(collected, io.github.chakyl.cozycafe.data.CafeHandovers.billTaken(player, cafe, earnings));
                giveOrDrop(player, collected);
                player.displayClientMessage(Component.translatable(this.bill.isClosed()
                        ? "message.cozyrestaurant.bill.collected_partial" : "message.cozyrestaurant.bill.collected").withStyle(ChatFormatting.GREEN), true);
            }
        } else if (this.billGuestPresent()) {
            player.displayClientMessage(Component.translatable("message.cozyrestaurant.bill.unpaid", this.bill.getGuestName()).withStyle(ChatFormatting.RED), true);
            fail(player);
            return;
        } else {
            // The guest left without paying: nothing happens to the cafe, the bill is just cleared away.
            tips.addAll(this.bill.takeAll());
            player.displayClientMessage(Component.translatable("message.cozyrestaurant.bill.abandoned").withStyle(ChatFormatting.YELLOW), true);
        }
        if (!tips.isEmpty()) {
            // Describe the tips before handing them over: giving empties the stacks.
            MutableComponent description = describe(tips);
            for (ItemStack tip : tips) giveOrDrop(player, tip);
            player.displayClientMessage(Component.translatable("message.cozyrestaurant.bill.tips", description).withStyle(ChatFormatting.GOLD), false);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
        boolean playerVisit = !this.bill.isNpc() && this.bill.getGuest().equals(this.guestPlayer);
        if (this.bill.isDebt()) {
            ServerPlayer debtor = this.onlineGuest();
            CafeManagerBlockEntity c = this.getCafeManager(this.level);
            if (debtor != null && c != null && !c.owes(debtor.getUUID())) {
                debtor.displayClientMessage(Component.translatable("message.cozyrestaurant.debt.cleared", c.getCafeName()).withStyle(ChatFormatting.GREEN), false);
            }
        }
        this.bill = null;
        if (playerVisit) {
            this.endPlayerVisit();
        } else {
            this.setChangedForRender();
        }
    }

    /** "3 × Изумруд, 1 × Хлеб" */
    private static MutableComponent describe(List<ItemStack> stacks) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) text.append(", ");
            text.append(stacks.get(i).getCount() + " × ").append(stacks.get(i).getHoverName());
        }
        return text;
    }

    /** The guest chose a rating in the bill screen. */
    public void rateBill(ServerPlayer player, int stars) {
        if (this.bill == null || !player.getUUID().equals(this.bill.getGuest())) return;
        this.bill.setRating(stars);
        this.countPayment();
        this.setChangedForRender();
    }

    private void attachBillListener() {
        if (this.bill != null) this.bill.getPayment().addListener(container -> {
            this.countPayment();
            this.setChangedForRender();
        });
    }

    /** A bill that got paid (or closed with what was paid) goes into the shift report once. */
    /** What NPCs pay with (config), netherite scrap if the id is unknown. */
    public static ItemStack npcCurrency() {
        ResourceLocation id = ResourceLocation.tryParse(CozyCafe.CONFIG.npcPaymentItem.get());
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item == Items.AIR ? Items.NETHERITE_SCRAP : item);
    }

    /** How filling an item is: hunger + saturation it restores. Cake counts all its slices; non-food is 0. */
    public static double foodPoints(ItemStack stack) {
        FoodProperties food = stack.getItem().getFoodProperties();
        if (food != null) return food.getNutrition() + food.getNutrition() * food.getSaturationModifier() * 2.0;
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CakeBlock) {
            // 7 slices of 2 hunger, saturation modifier 0.1.
            return 7 * (2 + 2 * 0.1 * 2.0);
        }
        return 0;
    }

    /** One payment item per full {@code npc_food_points_per_payment} points; the rest is the chance of one more. */
    public static int npcPayment(List<ItemStack> eaten, RandomSource random) {
        double points = 0;
        for (ItemStack stack : eaten) points += foodPoints(stack);
        double per = CozyCafe.CONFIG.npcFoodPointsPerPayment.get();
        int whole = (int) Math.floor(points / per);
        double rest = points / per - whole;
        return whole + (random.nextDouble() < rest ? 1 : 0);
    }

    private void countPayment() {
        if (this.bill == null || this.bill.isCounted() || !(this.level instanceof ServerLevel)) return;
        if (!this.bill.isPaid() && !this.bill.isClosed()) return;
        CafeManagerBlockEntity cafe = this.getCafeManager(this.level);
        if (cafe == null) return;
        this.bill.markCounted();
        cafe.recordBillPaid(this.bill.getCurrency(), this.bill.getSum());
    }

    @Nullable
    public CafeBill getBill() {
        return this.bill;
    }

    // ---- Leaving ----

    /** The cafe closes or the NPC gives up: an NPC walks home; a player's open order is cancelled. */
    public void sendCustomerHome() {
        if (this.guestPlayer != null) {
            if (this.phase == Phase.WAITING) {
                ServerPlayer guest = this.onlineGuest();
                this.order.clear();
                this.orderLines.clear();
                if (this.servedLines.isEmpty()) {
                    if (guest != null) guest.displayClientMessage(Component.translatable("message.cozyrestaurant.order.cancelled_closed").withStyle(ChatFormatting.YELLOW), false);
                    this.endPlayerVisit();
                } else {
                    // What was served is still paid for.
                    if (guest != null) guest.displayClientMessage(Component.translatable("message.cozyrestaurant.order.rest_cancelled_closed").withStyle(ChatFormatting.YELLOW), false);
                    this.nothingPending(guest);
                }
            }
            return;
        }
        this.leave(true);
    }

    /** Called while the table block is being removed: the NPC leaves without touching the block. */
    public void onTableRemoved() {
        this.leave(false);
    }

    private void leave(boolean updateBlock) {
        if (this.level instanceof ServerLevel serverLevel && this.hasCustomer && this.exitPos != null) {
            CustomerEntity customer = CozyRegistry.EntityRegistry.CUSTOMER.get().create(serverLevel);
            if (customer != null) {
                BlockPos seat = this.worldPosition.relative(this.getBlockState().getValue(CafeMenuBlock.FACING));
                CustomerEntityUtils.spawnCustomerAndTarget(seat, serverLevel, this.customerSkin, this.exitPos, customer, CustomerTarget.SIGN);
            }
        }
        // An NPC that leaves mid-meal leaves its plate behind as a dirty one.
        if (updateBlock && this.level != null && this.hasCustomer && this.getBlockState().getValue(CafeMenuBlock.DISH)) {
            this.setPlate(false, true);
        }
        this.reset();
    }

    private void reset() {
        this.phase = Phase.NONE;
        this.hasCustomer = false;
        this.customerTravelTime = -1;
        this.orderTime = -1;
        this.order.clear();
        this.orderLines.clear();
        this.orderCurrency = ItemStack.EMPTY;
        this.orderDeadline = -1;
        this.orderDuration = 0;
        this.eatTime = -1;
        this.customerSkin = "";
        this.gameProfile = null;
        this.exitPos = null;
        if (this.level != null && !this.isRemoved()) this.setChangedForRender();
    }

    private void setPlate(boolean dish, boolean dirty) {
        if (this.level == null) return;
        BlockState state = this.getBlockState().setValue(CafeMenuBlock.DISH, dish).setValue(CafeMenuBlock.DIRTY, dirty);
        if (state != this.getBlockState()) this.level.setBlock(this.worldPosition, state, 3);
    }

    /** Puts the stack into the player's inventory; what does not fit drops at their feet. */
    public static void giveOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
    }

    // ---- Getters ----

    public boolean canReceiveNewCustomer() {
        return this.isFree();
    }

    public Phase getPhase() {
        return this.phase;
    }

    public boolean hasOrder() {
        return this.phase == Phase.WAITING && !this.order.isEmpty();
    }

    public List<ItemStack> getOrder() {
        return Collections.unmodifiableList(this.order);
    }

    /** The plate's food while it is on the table. */
    public List<ItemStack> getEating() {
        return this.getBlockState().getValue(CafeMenuBlock.DISH) ? Collections.unmodifiableList(this.eating) : List.of();
    }

    public boolean isLeftOwing() {
        return this.leftOwing;
    }

    public List<CafeMenuEntry> getServedLines() {
        return Collections.unmodifiableList(this.servedLines);
    }

    public List<CafeMenuEntry> getPendingLines() {
        return Collections.unmodifiableList(this.orderLines);
    }

    /** Game time the current countdown of a player visit ends (order, food on the table, bill), or -1. */
    public long getPlayerCountdownEnd() {
        if (this.phase == Phase.EATING && this.foodDeadline > 0) return this.foodDeadline;
        if (this.phase == Phase.WAITING || this.phase == Phase.AFTER_MEAL) return this.orderDeadline;
        return -1;
    }

    /** The guest asks about their order: what came, what is still coming, how long is left. */
    public void openStatus(ServerPlayer player) {
        if (this.level == null || !player.getUUID().equals(this.guestPlayer)) return;
        CafeManagerBlockEntity cafe = this.getCafeManager(this.level);
        long end = this.getPlayerCountdownEnd();
        int left = end < 0 ? -1 : (int) Math.max(0, end - this.level.getGameTime());
        EvilPacketsIHateThem.sendToPlayer(new io.github.chakyl.cozycafe.network.ClientBoundOrderStatusPacket(this.worldPosition,
                cafe == null ? "" : cafe.getCafeName(), this.orderCurrency, this.servedLines, this.orderLines, this.phase, left,
                this.getBlockState().getValue(CafeMenuBlock.DISH) && this.phase == Phase.WAITING), player);
    }

    public long getOrderDeadline() {
        return this.orderDeadline;
    }

    public int getOrderDuration() {
        return this.orderDuration;
    }

    public boolean getHasCustomer() {
        return this.hasCustomer;
    }

    @Nullable
    public UUID getGuestPlayer() {
        return this.guestPlayer;
    }

    @Nullable
    public BlockPos getCafeManager() {
        return this.cafeManager;
    }

    @Nullable
    public GameProfile getGameProfile() {
        return this.gameProfile;
    }

    private void setChangedForRender() {
        this.setChanged();
        // While a bill lies on the table the block shows the bill instead of the menu book.
        if (this.level != null && !this.level.isClientSide() && !this.isRemoved()) {
            BlockState state = this.getBlockState();
            if (state.hasProperty(CafeMenuBlock.BILL) && state.getValue(CafeMenuBlock.BILL) != (this.bill != null)) {
                this.level.setBlock(this.worldPosition, state.setValue(CafeMenuBlock.BILL, this.bill != null), 3);
            }
        }
        if (this.level != null) this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    public void setCustomerSkinFromUsername(String name) {
        if (StringUtil.isNullOrEmpty(name) || this.level == null) return;
        this.customerSkin = name;
        MinecraftServer server = this.level.getServer();
        if (server == null) return;
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            cache.getAsync(name, profileOpt -> {
                if (profileOpt != null && profileOpt.isPresent()) {
                    GameProfile filledProfile = server.getSessionService().fillProfileProperties(profileOpt.get(), true);
                    server.execute(() -> {
                        if (name.equals(this.customerSkin)) {
                            this.gameProfile = filledProfile;
                            this.setChangedForRender();
                        }
                    });
                }
            });
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            CafeIndex.tables(serverLevel.getServer()).add(serverLevel, this.worldPosition);
        }
    }

    // ---- Saving ----

    private static ListTag saveItems(List<ItemStack> items) {
        ListTag list = new ListTag();
        for (ItemStack item : items) list.add(item.save(new CompoundTag()));
        return list;
    }

    private static void loadItems(ListTag list, List<ItemStack> into) {
        into.clear();
        for (Tag tag : list) {
            ItemStack stack = ItemStack.of((CompoundTag) tag);
            if (!stack.isEmpty()) into.add(stack);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("phase", this.phase.name());
        tag.putBoolean("hasCustomer", this.hasCustomer);
        tag.putInt("customerTravelTime", this.customerTravelTime);
        if (this.guestPlayer != null) tag.putUUID("guestPlayer", this.guestPlayer);
        tag.putString("guestName", this.guestName);
        if (this.cafeManager != null) tag.put("cafeManager", NbtUtils.writeBlockPos(this.cafeManager));
        if (this.exitPos != null) tag.put("exitPos", NbtUtils.writeBlockPos(this.exitPos));
        tag.putInt("orderTime", this.orderTime);
        tag.put("order", saveItems(this.order));
        ListTag lines = new ListTag();
        for (CafeMenuEntry line : this.orderLines) lines.add(line.save());
        tag.put("orderLines", lines);
        if (!this.orderCurrency.isEmpty()) tag.put("orderCurrency", this.orderCurrency.save(new CompoundTag()));
        tag.putLong("orderDeadline", this.orderDeadline);
        tag.putInt("orderDuration", this.orderDuration);
        tag.put("eating", saveItems(this.eating));
        tag.putInt("eatTime", this.eatTime);
        ListTag served = new ListTag();
        for (CafeMenuEntry line : this.servedLines) served.add(line.save());
        tag.put("servedLines", served);
        tag.putInt("guestWaitTicks", this.guestWaitTicks);
        tag.putInt("awayTicks", this.awayTicks);
        tag.putLong("foodDeadline", this.foodDeadline);
        tag.putBoolean("foodWasted", this.foodWasted);
        tag.putBoolean("leftOwing", this.leftOwing);
        if (this.bill != null) tag.put("bill", this.bill.save());
        tag.putString("customerSkin", this.customerSkin);
        if (this.gameProfile != null) {
            tag.put("customerProfile", NbtUtils.writeGameProfile(new CompoundTag(), this.gameProfile));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.hasCustomer = tag.getBoolean("hasCustomer");
        this.customerTravelTime = tag.contains("customerTravelTime") ? tag.getInt("customerTravelTime") : -1;
        this.guestPlayer = tag.hasUUID("guestPlayer") ? tag.getUUID("guestPlayer") : null;
        this.guestName = tag.getString("guestName");
        this.cafeManager = tag.contains("cafeManager") ? NbtUtils.readBlockPos(tag.getCompound("cafeManager")) : null;
        this.exitPos = tag.contains("exitPos") ? NbtUtils.readBlockPos(tag.getCompound("exitPos")) : null;
        this.orderTime = tag.contains("orderTime") ? tag.getInt("orderTime") : -1;
        loadItems(tag.getList("order", Tag.TAG_COMPOUND), this.order);
        this.orderLines.clear();
        for (Tag line : tag.getList("orderLines", Tag.TAG_COMPOUND)) this.orderLines.add(CafeMenuEntry.load((CompoundTag) line));
        this.orderCurrency = tag.contains("orderCurrency") ? ItemStack.of(tag.getCompound("orderCurrency")) : ItemStack.EMPTY;
        this.orderDeadline = tag.contains("orderDeadline") ? tag.getLong("orderDeadline") : -1;
        this.orderDuration = tag.getInt("orderDuration");
        loadItems(tag.getList("eating", Tag.TAG_COMPOUND), this.eating);
        this.eatTime = tag.contains("eatTime") ? tag.getInt("eatTime") : -1;
        this.servedLines.clear();
        for (Tag line : tag.getList("servedLines", Tag.TAG_COMPOUND)) this.servedLines.add(CafeMenuEntry.load((CompoundTag) line));
        this.guestWaitTicks = tag.getInt("guestWaitTicks");
        this.awayTicks = tag.getInt("awayTicks");
        this.foodDeadline = tag.contains("foodDeadline") ? tag.getLong("foodDeadline") : -1;
        this.foodWasted = tag.getBoolean("foodWasted");
        this.leftOwing = tag.getBoolean("leftOwing");
        this.bill = tag.contains("bill") ? CafeBill.load(tag.getCompound("bill")) : null;
        this.attachBillListener();
        this.customerSkin = tag.getString("customerSkin");
        this.gameProfile = tag.contains("customerProfile") ? NbtUtils.readGameProfile(tag.getCompound("customerProfile")) : null;
        this.phase = loadPhase(tag);
    }

    /** Tables saved before phases existed derive theirs from the old fields. */
    private Phase loadPhase(CompoundTag tag) {
        if (tag.contains("phase")) {
            try {
                return Phase.valueOf(tag.getString("phase"));
            } catch (IllegalArgumentException ignored) {
                return Phase.NONE;
            }
        }
        if (this.customerTravelTime > -1) return Phase.TRAVEL;
        if (!this.hasCustomer) return Phase.NONE;
        if (!this.order.isEmpty()) return Phase.WAITING;
        if (this.eatTime > -1) return Phase.EATING;
        return Phase.CHOOSING;
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
}
