package io.github.chakyl.cozycafe.blockentities;

import io.github.chakyl.cozycafe.registry.CozyRegistry;
import io.github.chakyl.cozycafe.util.CafeLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import static io.github.chakyl.cozycafe.blocks.CafeSignBlock.OPEN;

public class CafeSignBlockEntity extends BlockEntity {
    private BlockPos linkedManager;
    /** Whether the sign belongs to a cafe (its claim has one, or it was linked); a sign of no cafe says nothing. */
    private boolean inCafe;

    public CafeSignBlockEntity(BlockPos pos, BlockState state) {
        super(CozyRegistry.BlockEntityRegistry.CAFE_SIGN.get(), pos, state);
    }

    public BlockPos getLinkedManager() {
        return linkedManager;
    }

    public void setLinkedManager(BlockPos linkedManager) {
        this.linkedManager = linkedManager;
    }

    public boolean isInCafe() {
        return this.inCafe;
    }

    /** Once a second: the cafe of the claim the sign hangs in (or the linked one) decides OPEN or CLOSED. */
    public void serverTick(ServerLevel level) {
        if ((level.getGameTime() + this.worldPosition.asLong()) % 20 != 0) return;
        CafeManagerBlockEntity cafe = null;
        if (this.linkedManager != null && level.isLoaded(this.linkedManager)
                && level.getBlockEntity(this.linkedManager) instanceof CafeManagerBlockEntity linked) {
            cafe = linked;
        }
        if (cafe == null) cafe = CafeLookup.cafeAt(level, this.worldPosition);
        boolean nowInCafe = cafe != null;
        boolean open = nowInCafe && cafe.isOpen();
        BlockState state = this.getBlockState();
        boolean stateChanged = state.hasProperty(OPEN) && state.getValue(OPEN) != open;
        if (nowInCafe == this.inCafe && !stateChanged) return;
        this.inCafe = nowInCafe;
        if (stateChanged) {
            level.setBlock(this.worldPosition, state.setValue(OPEN, open), 3);
        }
        this.setChanged();
        level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    public void setOpen(boolean open) {
        BlockState currentState = this.level.getBlockState(this.getBlockPos());
        if (currentState.hasProperty(OPEN)) {
            this.level.setBlock(this.getBlockPos(), currentState.setValue(OPEN, open), 3);
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
            this.setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (this.linkedManager != null) {
            nbt.put("linkedManager", NbtUtils.writeBlockPos(this.linkedManager));
        }
        nbt.putBoolean("inCafe", this.inCafe);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("linkedManager")) {
            this.linkedManager = NbtUtils.readBlockPos(nbt.getCompound("linkedManager"));
        }
        this.inCafe = nbt.getBoolean("inCafe");
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

    // Forge-only handleUpdateTag override removed: vanilla calls load() with the chunk update tag.
}
