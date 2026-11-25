package io.github.chakyl.cozycafe.blocks;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.gui.CafeManagerMenu;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import io.github.chakyl.cozycafe.data.CafeIndex;
import io.github.chakyl.cozycafe.util.CafeAccess;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;
import java.util.List;

public class CafeManagerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");


    public CafeManagerBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Nullable
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(BlockEntityType<A> serverType, BlockEntityType<E> clientType, BlockEntityTicker<? super E> ticker) {
        return (BlockEntityTicker<A>) ticker;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite()).setValue(OPEN, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING, OPEN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new CafeManagerBlockEntity(pPos, pState);
    }

    @Override
    public List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
        List<ItemStack> drops = super.getDrops(pState, pBuilder);

        BlockEntity blockEntity = pBuilder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof CafeManagerBlockEntity cafeManager) {
            for (ItemStack stack : drops) {
                if (stack.getItem() == this.asItem()) {
                    CompoundTag customTag = new CompoundTag();
                    cafeManager.saveCafeData(customTag);
                    stack.addTagElement("cafeData", customTag);
                }
            }
        }
        return drops;
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        if (pLevel.isClientSide) return;
        if (pStack.hasTag() && pStack.getTag().contains("cafeData")) {
            BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
            if (blockEntity instanceof CafeManagerBlockEntity cafeEntity) {
                CompoundTag blockEntityNbt = pStack.getTag().getCompound("cafeData");
                cafeEntity.load(blockEntityNbt);
                cafeEntity.setChanged();
            }
        }
        // Whoever places the manager owns the cafe (also after breaking and re-placing it).
        if (pPlacer instanceof Player player && pLevel.getBlockEntity(pPos) instanceof CafeManagerBlockEntity cafeEntity) {
            cafeEntity.setOwner(player.getUUID(), player.getGameProfile().getName());
        }
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
        if (!pState.is(pNewState.getBlock()) && pLevel instanceof ServerLevel serverLevel) {
            if (serverLevel.getBlockEntity(pPos) instanceof CafeManagerBlockEntity cafe) {
                if (cafe.isOpen()) {
                    cafe.sendCustomersHome();
                    cafe.reportShift();
                }
                // Broken: the cafe leaves everyone's map (it comes back where the manager is placed and opened again).
                io.github.chakyl.cozycafe.data.CafeDirectory.remove(serverLevel, cafe.getCafeId());
            }
            CafeIndex.get(serverLevel.getServer()).remove(serverLevel, pPos);
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (!pLevel.isClientSide) {
            if (pLevel.getBlockEntity(pPos) instanceof CafeManagerBlockEntity cafeManagerBlockEntity) {
                ItemStack held = pPlayer.getItemInHand(pHand);
                if (io.github.chakyl.cozycafe.item.BillItem.isCollected(held)) {
                    // Waiters may come to the manager with a collected bill; they still can't open it.
                    cafeManagerBlockEntity.cashBill(pPlayer, held);
                    return InteractionResult.CONSUME;
                }
                if (pHand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
                if (!CafeAccess.canManage(pPlayer, cafeManagerBlockEntity)) {
                    pPlayer.displayClientMessage(CafeAccess.deniedMessage(cafeManagerBlockEntity), true);
                    return InteractionResult.CONSUME;
                }
                CafeManagerMenu.open((ServerPlayer) pPlayer, cafeManagerBlockEntity);
            } else {
                throw new IllegalStateException("No Container Provider for Cafe Manager!");
            }
        }
        return InteractionResult.sidedSuccess(pLevel.isClientSide());
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        return createTickerHelper(pBlockEntityType, CozyRegistry.BlockEntityRegistry.CAFE_MANAGER.get(),
                (pLevel1, pPos, pState1, pBlockEntity) -> pBlockEntity.tick(pLevel1, pPos, pState1));
    }
}