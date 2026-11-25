package io.github.chakyl.cozycafe;

import io.github.chakyl.cozycafe.blockentities.renderer.CafeMenuBlockEntityRenderer;
import io.github.chakyl.cozycafe.blockentities.renderer.PlatingStationBlockEntityRenderer;
import io.github.chakyl.cozycafe.client.model.DelegatingBakedModel;
import io.github.chakyl.cozycafe.client.model.ServingPlateItemOverrides;
import io.github.chakyl.cozycafe.entities.renderer.CustomerRenderer;
import io.github.chakyl.cozycafe.gui.CafeManagerScreen;
import io.github.chakyl.cozycafe.gui.MenuSelectorScreen;
import io.github.chakyl.cozycafe.network.EvilPacketsIHateThem;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import io.github.chakyl.cozycafe.client.ApronArmorRenderer;
import io.github.chakyl.cozycafe.client.ClientHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.model.ModelResourceLocation;

@Environment(EnvType.CLIENT)
public class CozyCafeClient implements ClientModInitializer {
    private static final ModelResourceLocation PLATE_LOCATION = new ModelResourceLocation(CozyCafe.MODID, "serving_plate", "inventory");

    @Override
    public void onInitializeClient() {
        EvilPacketsIHateThem.registerClientReceivers();
        // Forge read "render_type" from the model files; Fabric needs the cutout layer registered here,
        // otherwise transparent pixels (crumbs on a dirty plate, the sign, the manager's screen) render black.
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(net.minecraft.client.renderer.RenderType.cutout(),
                CozyRegistry.BlockRegistry.CAFE_MENU.get(), CozyRegistry.BlockRegistry.CAFE_MANAGER.get(),
                CozyRegistry.BlockRegistry.CAFE_SIGN.get(), CozyRegistry.BlockRegistry.PLATING_STATION.get());

        MenuScreens.register(CozyRegistry.MenuRegistry.CAFE_MANAGER.get(), CafeManagerScreen::new);
        MenuScreens.register(CozyRegistry.MenuRegistry.MENU_SELECTOR.get(), MenuSelectorScreen::new);
        MenuScreens.register(CozyRegistry.MenuRegistry.CAFE_SETTINGS.get(), io.github.chakyl.cozycafe.gui.CafeSettingsScreen::new);
        MenuScreens.register(CozyRegistry.MenuRegistry.BILL.get(), io.github.chakyl.cozycafe.gui.BillScreen::new);

        BlockEntityRenderers.register(CozyRegistry.BlockEntityRegistry.CAFE_MENU.get(), CafeMenuBlockEntityRenderer::new);
        BlockEntityRenderers.register(CozyRegistry.BlockEntityRegistry.PLATING_STATION.get(), PlatingStationBlockEntityRenderer::new);
        BlockEntityRenderers.register(CozyRegistry.BlockEntityRegistry.CAFE_SIGN.get(), io.github.chakyl.cozycafe.blockentities.renderer.CafeSignBlockEntityRenderer::new);
        EntityRendererRegistry.register(CozyRegistry.EntityRegistry.CUSTOMER.get(), CustomerRenderer::new);
        ArmorRenderer.register(new ApronArmorRenderer(), CozyRegistry.ItemRegistry.APRON.get());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientHooks.clearRoles();
            io.github.chakyl.cozycafe.client.CafeWaypoints.clear();
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(io.github.chakyl.cozycafe.client.CafeWaypoints::tick);
        // Brigadier allows only latin letters in command names, so this one can't be Russian.
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("cafes").then(
                        net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("restore").executes(context -> {
                            context.getSource().sendFeedback(io.github.chakyl.cozycafe.client.CafeWaypoints.restore());
                            return 1;
                        }))));

        // Replacement for libtooltips (Forge only): "tooltip.always.<mod>.<item>.N" lines are always shown,
        // "tooltip.<mod>.<item>.N" lines while Shift is held.
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!CozyCafe.MODID.equals(id.getNamespace())) return;
            addLines(lines, "tooltip.always." + CozyCafe.MODID + "." + id.getPath() + ".");
            String shiftPrefix = "tooltip." + CozyCafe.MODID + "." + id.getPath() + ".";
            if (I18n.exists(shiftPrefix + "0")) {
                if (Screen.hasShiftDown()) {
                    addLines(lines, shiftPrefix);
                } else {
                    lines.add(Component.translatable("tooltip." + CozyCafe.MODID + ".hold_shift"));
                }
            }
        });

        // Port of ModelEvent.ModifyBakingResult: the serving plate model shows the food stored on it.
        ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register((model, ctx) -> {
            if (model != null && PLATE_LOCATION.equals(ctx.id())) {
                ItemOverrides overrides = new ServingPlateItemOverrides(model);
                return new DelegatingBakedModel(model) {
                    @Override
                    public ItemOverrides getOverrides() {
                        return overrides;
                    }
                };
            }
            return model;
        }));
    }

    private static void addLines(java.util.List<Component> lines, String prefix) {
        for (int i = 0; I18n.exists(prefix + i); i++) {
            lines.add(Component.translatable(prefix + i));
        }
    }
}
