package io.github.chakyl.cozycafe.blockentities.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.chakyl.cozycafe.blockentities.PlatingStationBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import io.github.chakyl.cozycafe.item.ServingPlateItem;
import io.github.chakyl.cozycafe.util.GeneralUtils;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class PlatingStationBlockEntityRenderer implements BlockEntityRenderer<PlatingStationBlockEntity> {
    private final ItemRenderer itemRenderer;

    public PlatingStationBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    private void renderPlate(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay) {
        poseStack.pushPose();
        // FIXED renders the model centred on the origin: lift it by half a block so the plate lies on the station's top.
        poseStack.translate(0.5D, 1.5D, 0.5D);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, poseStack, bufferSource, null, 0);
        poseStack.popPose();
    }


    @Override
    public void render(PlatingStationBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        ItemStack stationItem = pBlockEntity.getPlateItem();
        if (stationItem == null || stationItem.isEmpty()) return;
        renderPlate(new ItemStack(stationItem.getItem()), pPoseStack, pBuffer, pPackedLight, pPackedOverlay);
        List<ItemStack> items = ServingPlateItem.getItems(stationItem);
        float[][] layout = GeneralUtils.plateLayout(items.size());
        for (int i = 0; i < layout.length; i++) {
            pPoseStack.pushPose();
            pPoseStack.translate(0.5f, 1.13f, 0.5f);
            pPoseStack.translate(layout[i][0], 0, layout[i][1]);
            pPoseStack.scale(layout[i][2], layout[i][2], layout[i][2]);
            GeneralUtils.renderFood(items.get(i), pPoseStack, pBuffer, pBlockEntity, pPackedLight, pPackedOverlay, layout[i][2]);
            pPoseStack.popPose();
        }
    }

    /**
     * The plate and its food stand above the station's own block; without this they vanish (look cut off) whenever
     * the station's chunk section is out of view, e.g. looking down at a counter from above.
     */
    @Override
    public boolean shouldRenderOffScreen(PlatingStationBlockEntity blockEntity) {
        ItemStack plate = blockEntity.getPlateItem();
        return plate != null && !plate.isEmpty();
    }
}
