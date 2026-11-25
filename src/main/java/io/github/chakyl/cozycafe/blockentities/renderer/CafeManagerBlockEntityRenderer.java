package io.github.chakyl.cozycafe.blockentities.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.client.ClientHandovers;
import io.github.chakyl.cozycafe.data.CafeHandovers;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/** A red plate over the manager for a player who still owes this cafe bills: "you have not handed in N bills". */
@Environment(EnvType.CLIENT)
public class CafeManagerBlockEntityRenderer implements BlockEntityRenderer<CafeManagerBlockEntity> {
    private static final double MAX_DISTANCE_SQR = 24 * 24;
    private static final int BACKGROUND = 0xC0A01818;
    private final Font font;

    public CafeManagerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(CafeManagerBlockEntity cafe, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (cafe.getLevel() == null) return;
        CafeHandovers.Summary owed = ClientHandovers.at(cafe.getLevel().dimension().location(), cafe.getBlockPos());
        if (owed == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.distanceToSqr(cafe.getBlockPos().getCenter()) > MAX_DISTANCE_SQR) return;

        Component[] lines = {
                Component.translatable("label.cozyrestaurant.handover.line1"),
                Component.translatable("label.cozyrestaurant.handover.line2", owed.count(), owed.describeAmounts())
        };
        poseStack.pushPose();
        poseStack.translate(0.5, 1.55, 0.5);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.022F, -0.022F, 0.022F);
        Matrix4f matrix = poseStack.last().pose();
        for (int i = 0; i < lines.length; i++) {
            FormattedCharSequence text = lines[i].getVisualOrderText();
            float x = -this.font.width(text) / 2.0F;
            float y = (i - lines.length) * 11;
            this.font.drawInBatch(text, x, y, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, BACKGROUND, 0xF000F0);
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CafeManagerBlockEntity blockEntity) {
        return true;
    }
}
