package io.github.chakyl.cozycafe.mixin;

import io.github.chakyl.cozycafe.util.ApronRules;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The armor slots of the player inventory (an anonymous Slot in InventoryMenu):
 * the apron only goes on with every other armor and accessory slot empty, and nothing goes on over the apron.
 */
@Mixin(targets = "net.minecraft.world.inventory.InventoryMenu$1")
public abstract class ArmorSlotMixin {
    /** Inventory index of the chest armor slot. */
    private static final int CHEST_SLOT = 38;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void cozyrestaurant$apronRules(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot) (Object) this;
        if (!(slot.container instanceof Inventory inventory)) return;
        boolean chestSlot = slot.getContainerSlot() == CHEST_SLOT;
        if (ApronRules.isApron(stack)) {
            if (!chestSlot || !ApronRules.canPutOnApron(inventory.player)) cir.setReturnValue(false);
        } else if (!chestSlot && ApronRules.isWearingApron(inventory.player)) {
            cir.setReturnValue(false);
        }
    }
}
