package io.github.chakyl.cozycafe.blocks;

import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.fabricmc.fabric.api.entity.FakePlayer;
import io.github.chakyl.cozycafe.data.CafeIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import io.github.chakyl.cozycafe.data.CafeBill;
import io.github.chakyl.cozycafe.item.BillItem;

import org.jetbrains.annotations.Nullable;

public class CafeMenuBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty DISH = BooleanProperty.create("dish");
    public static final BooleanProperty DIRTY = BooleanProperty.create("dirty");
    /** A bill lies on the table (shown instead of the menu book). */
    public static final BooleanProperty BILL = BooleanProperty.create("bill");
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public CafeMenuBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DISH, false).setValue(DIRTY, false).setValue(BILL, false));
    }

    @Nullable
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(BlockEntityType<A> serverType, BlockEntityType<E> clientType, BlockEntityTicker<? super E> ticker) {
        return (BlockEntityTicker<A>) ticker;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite()).setValue(DISH, false).setValue(DIRTY, false).setValue(BILL, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING, DISH, DIRTY, BILL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new CafeMenuBlockEntity(pPos, pState);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pHand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!pLevel.isClientSide && pPlayer instanceof ServerPlayer player && !(pPlayer instanceof FakePlayer)
                && pLevel.getBlockEntity(pPos) instanceof CafeMenuBlockEntity table) {
            handleClick(table, player, player.getItemInHand(pHand));
        }
        return InteractionResult.sidedSuccess(pLevel.isClientSide());
    }

    /**
     * Everything a click on a table can mean, in order: the bill (the guest pays, a waiter picks it up), a blank bill
     * for a guest who has eaten, serving the plate, the guest taking the food, clearing a plate, and finally a seated
     * player ordering. Also used when a waiter clicks the guest player instead of the table.
     */
    public static void handleClick(CafeMenuBlockEntity table, ServerPlayer player, ItemStack handStack) {
        CafeBill bill = table.getBill();
        if (bill != null) {
            if (player.getUUID().equals(bill.getGuest()) && !player.isShiftKeyDown()) {
                table.openBill(player);
            } else {
                table.handleCollectBill(player);
            }
            return;
        }
        if (BillItem.isBlank(handStack)) {
            table.handleIssueBill(player, handStack);
            return;
        }
        boolean guest = player.getUUID().equals(table.getGuestPlayer());
        if (guest && table.hasFoodFor(player)) {
            table.handleTakeFood(player);
            return;
        }
        if (table.hasOrder() && !handStack.isEmpty() && !guest) {
            player.swing(InteractionHand.MAIN_HAND, true);
            table.handleServe(player, handStack);
            return;
        }
        if (!guest && table.handleClearPlate(player)) return;
        if (guest) {
            // The guest's own table: "Мой заказ".
            table.openStatus(player);
        } else if (table.getGuestPlayer() == null && table.getPhase() == CafeMenuBlockEntity.Phase.NONE) {
            table.requestOrderScreen(player);
        }
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
        if (!pState.is(pNewState.getBlock()) && pLevel instanceof ServerLevel serverLevel) {
            if (serverLevel.getBlockEntity(pPos) instanceof CafeMenuBlockEntity table) {
                if (table.getHasCustomer()) table.onTableRemoved();
                // Money a guest already put into the bill must not vanish with the table.
                if (table.getBill() != null) net.minecraft.world.Containers.dropContents(serverLevel, pPos, table.getBill().getPayment());
            }
            CafeIndex.tables(serverLevel.getServer()).remove(serverLevel, pPos);
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        return createTickerHelper(pBlockEntityType, CozyRegistry.BlockEntityRegistry.CAFE_MENU.get(),
                (pLevel1, pPos, pState1, pBlockEntity) -> pBlockEntity.tick(pLevel1, pPos, pState1));
    }
}