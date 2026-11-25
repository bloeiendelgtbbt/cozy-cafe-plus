package io.github.chakyl.cozycafe.registry;

import io.github.chakyl.cozycafe.CozyCafe;
import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeSignBlockEntity;
import io.github.chakyl.cozycafe.blockentities.PlatingStationBlockEntity;
import io.github.chakyl.cozycafe.blocks.CafeManagerBlock;
import io.github.chakyl.cozycafe.blocks.CafeMenuBlock;
import io.github.chakyl.cozycafe.blocks.CafeSignBlock;
import io.github.chakyl.cozycafe.blocks.PlatingStationBlock;
import io.github.chakyl.cozycafe.entities.CustomerEntity;
import io.github.chakyl.cozycafe.gui.CafeManagerMenu;
import io.github.chakyl.cozycafe.gui.MenuSelectorMenu;
import io.github.chakyl.cozycafe.item.ApronItem;
import io.github.chakyl.cozycafe.item.CafeManagerItem;
import io.github.chakyl.cozycafe.item.CafeSignItem;
import io.github.chakyl.cozycafe.item.DirtyServingPlateItem;
import io.github.chakyl.cozycafe.item.ServingPlateItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class CozyRegistry {

    private static final String MODID = CozyCafe.MODID;

    private static ResourceLocation id(String name) {
        return new ResourceLocation(MODID, name);
    }

    /**
     * Registration order matters on Fabric because everything is registered immediately:
     * blocks (and their items) first, then block entities, entities, menus, the rest of the items and the tab.
     */
    public static void register() {
        BlockRegistry.init();
        BlockEntityRegistry.init();
        EntityRegistry.init();
        MenuRegistry.init();
        ItemRegistry.init();
        CreativeTabReg.init();
    }

    public static final class BlockRegistry {

        public static final RegistryObject<Block> CAFE_MANAGER = registerWithItem("cafe_manager", () -> new CafeManagerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).noOcclusion().strength(1.5F, 6.0F)), (blockObj) -> ItemRegistry.register("cafe_manager", () -> new CafeManagerItem(blockObj.get(), new Item.Properties())));
        public static final RegistryObject<Block> CAFE_SIGN = registerWithItem("cafe_sign", () -> new CafeSignBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).noOcclusion().strength(1.5F, 6.0F)), (blockObj) -> ItemRegistry.register("cafe_sign", () -> new CafeSignItem(blockObj.get(), new Item.Properties())));
        public static final RegistryObject<Block> CAFE_MENU = registerWithItem("cafe_menu", () -> new CafeMenuBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).noOcclusion().strength(1.5F, 6.0F)));
        public static final RegistryObject<Block> PLATING_STATION = registerWithItem("plating_station", () -> new PlatingStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).noOcclusion().strength(1.5F, 6.0F)));

        private static void init() {
        }

        private static RegistryObject<Block> registerWithItem(final String name, final Supplier<Block> supplier) {
            return registerWithItem(name, supplier, ItemRegistry::registerBlockItem);
        }

        private static RegistryObject<Block> registerWithItem(final String name, final Supplier<Block> blockSupplier, final Function<RegistryObject<Block>, RegistryObject<Item>> itemSupplier) {
            final RegistryObject<Block> block = RegistryObject.register(BuiltInRegistries.BLOCK, id(name), blockSupplier);
            itemSupplier.apply(block);
            return block;
        }
    }

    public static final class BlockEntityRegistry {
        public static final RegistryObject<BlockEntityType<CafeManagerBlockEntity>> CAFE_MANAGER = register("cafe_manager", () -> BlockEntityType.Builder.of(CafeManagerBlockEntity::new, BlockRegistry.CAFE_MANAGER.get()).build(null));
        public static final RegistryObject<BlockEntityType<CafeSignBlockEntity>> CAFE_SIGN = register("cafe_sign", () -> BlockEntityType.Builder.of(CafeSignBlockEntity::new, BlockRegistry.CAFE_SIGN.get()).build(null));
        public static final RegistryObject<BlockEntityType<CafeMenuBlockEntity>> CAFE_MENU = register("cafe_menu", () -> BlockEntityType.Builder.of(CafeMenuBlockEntity::new, BlockRegistry.CAFE_MENU.get()).build(null));
        public static final RegistryObject<BlockEntityType<PlatingStationBlockEntity>> PLATING_STATION = register("plating_station", () -> BlockEntityType.Builder.of(PlatingStationBlockEntity::new, BlockRegistry.PLATING_STATION.get()).build(null));

        private static void init() {
        }

        private static <T extends BlockEntityType<?>> RegistryObject<T> register(String name, Supplier<T> supplier) {
            return RegistryObject.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id(name), supplier);
        }
    }

    public static final class EntityRegistry {
        public static final RegistryObject<EntityType<CustomerEntity>> CUSTOMER = RegistryObject.register(BuiltInRegistries.ENTITY_TYPE, id("customer"), () -> EntityType.Builder.of(CustomerEntity::new, MobCategory.CREATURE).sized(0.6F, 1.8F).build("customer"));

        private static void init() {
            FabricDefaultAttributeRegistry.register(CUSTOMER.get(), CustomerEntity.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.MOVEMENT_SPEED, 0.25D).add(Attributes.FOLLOW_RANGE, 32.0D));
        }
    }

    public static final class ItemRegistry {

        private static final List<RegistryObject<Item>> ALL_ITEMS = new ArrayList<>();

        public static final RegistryObject<Item> SERVING_PLATE = register("serving_plate", () -> new ServingPlateItem(new Item.Properties().stacksTo(8)));
        public static final RegistryObject<Item> DIRTY_SERVING_PLATE = register("dirty_serving_plate", () -> new DirtyServingPlateItem(new Item.Properties().stacksTo(8)));
        public static final RegistryObject<Item> APRON = register("apron", () -> new ApronItem(new Item.Properties()));
        public static final RegistryObject<Item> BILL = register("bill", () -> new io.github.chakyl.cozycafe.item.BillItem(new Item.Properties().stacksTo(16)));

        private static void init() {
        }

        private static RegistryObject<Item> registerBlockItem(final RegistryObject<Block> block) {
            return register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
        }

        private static RegistryObject<Item> register(final String name, final Supplier<Item> supplier) {
            final RegistryObject<Item> item = RegistryObject.register(BuiltInRegistries.ITEM, id(name), supplier);
            ALL_ITEMS.add(item);
            return item;
        }
    }

    public static final class MenuRegistry {
        public static final RegistryObject<MenuType<CafeManagerMenu>> CAFE_MANAGER = RegistryObject.register(BuiltInRegistries.MENU, id("cafe_manager"), () -> new ExtendedScreenHandlerType<>(CafeManagerMenu::new));
        public static final RegistryObject<MenuType<MenuSelectorMenu>> MENU_SELECTOR = RegistryObject.register(BuiltInRegistries.MENU, id("menu_selector"), () -> new ExtendedScreenHandlerType<>(MenuSelectorMenu::new));
        public static final RegistryObject<MenuType<io.github.chakyl.cozycafe.gui.BillMenu>> BILL = RegistryObject.register(BuiltInRegistries.MENU, id("bill"), () -> new ExtendedScreenHandlerType<>(io.github.chakyl.cozycafe.gui.BillMenu::new));
        public static final RegistryObject<MenuType<io.github.chakyl.cozycafe.gui.CafeSettingsMenu>> CAFE_SETTINGS = RegistryObject.register(BuiltInRegistries.MENU, id("cafe_settings"), () -> new ExtendedScreenHandlerType<>(io.github.chakyl.cozycafe.gui.CafeSettingsMenu::new));

        private static void init() {
        }
    }

    public static final class CreativeTabReg {

        public static final RegistryObject<CreativeModeTab> TAB = RegistryObject.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("tab"), () ->
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(BlockRegistry.CAFE_MANAGER.get()))
                        .title(Component.translatable("itemGroup." + CozyCafe.MODID))
                        .displayItems((parameters, output) ->
                                output.acceptAll(ItemRegistry.ALL_ITEMS
                                        .stream()
                                        .map(o -> new ItemStack(o.get()))
                                        .toList())
                        )
                        .build()
        );

        private static void init() {
        }
    }

}
