package io.github.chakyl.cozycafe.compat;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Trinkets ("accessories") access. Only loaded when Trinkets is installed, see {@link io.github.chakyl.cozycafe.util.ApronRules}.
 */
public final class TrinketsCompat {
    private TrinketsCompat() {
    }

    public static boolean hasAnyTrinket(LivingEntity entity) {
        return TrinketsApi.getTrinketComponent(entity).map(component -> component.isEquipped(stack -> !stack.isEmpty())).orElse(false);
    }

    /** Takes off every equipped trinket and returns the removed stacks. */
    public static List<ItemStack> removeAllTrinkets(LivingEntity entity) {
        List<ItemStack> removed = new ArrayList<>();
        TrinketsApi.getTrinketComponent(entity).ifPresent(component -> {
            for (Tuple<SlotReference, ItemStack> equipped : component.getAllEquipped()) {
                if (equipped.getB().isEmpty()) continue;
                SlotReference slot = equipped.getA();
                removed.add(slot.inventory().getItem(slot.index()).copy());
                slot.inventory().setItem(slot.index(), ItemStack.EMPTY);
            }
        });
        return removed;
    }
}
