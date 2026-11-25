package io.github.chakyl.cozycafe.item;

import io.github.chakyl.cozycafe.util.ApronRules;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * Waiter's apron: worn in the chest slot, gives no protection and never breaks.
 */
public class ApronItem extends ArmorItem {
    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return 0;
        }

        @Override
        public int getDefenseForType(Type type) {
            return 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_LEATHER;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return "cozyrestaurant_apron";
        }

        @Override
        public float getToughness() {
            return 0;
        }

        @Override
        public float getKnockbackResistance() {
            return 0;
        }
    };

    public ApronItem(Properties properties) {
        super(MATERIAL, Type.CHESTPLATE, properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!ApronRules.canPutOnApron(player)) {
            if (!level.isClientSide) ApronRules.tellCannotPutOn(player);
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
