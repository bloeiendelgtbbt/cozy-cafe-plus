package io.github.chakyl.cozycafe.blockentities.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.chakyl.cozycafe.blockentities.CafeSignBlockEntity;
import io.github.chakyl.cozycafe.blocks.CafeSignBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/** "OPEN" or "CLOSED" painted on the sign's board: dark, calm colours that read well on the light wood. */
public class CafeSignBlockEntityRenderer implements BlockEntityRenderer<CafeSignBlockEntity> {
    private static final int OPEN_COLOR = 0xFF2F5A1F;
    private static final int CLOSED_COLOR = 0xFF7A2E16;
    /** The board (model element 2.1-8.1 high, 12 wide) has its front 1/16 off the wall. */
    private static final float BOARD_CENTER_Y = 5.1F / 16.0F;
    private static final float FRONT = 7.0F / 16.0F - 0.004F;
    private static final float BOARD_WIDTH = 10.0F / 16.0F;

    public CafeSignBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CafeSignBlockEntity sign, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!sign.isInCafe()) return;
        BlockState state = sign.getBlockState();
        if (!state.hasProperty(CafeSignBlock.OPEN) || !state.hasProperty(CafeSignBlock.FACING)) return;
        boolean open = state.getValue(CafeSignBlock.OPEN);
        String text = open ? "OPEN" : "CLOSED";
        Font font = Minecraft.getInstance().font;
        // The board hangs on the FACING side of the block and is read from the other side.
        Direction front = state.getValue(CafeSignBlock.FACING).getOpposite();
        float scale = BOARD_WIDTH / font.width("CLOSED");
        poseStack.pushPose();
        poseStack.translate(0.5, BOARD_CENTER_Y, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-front.toYRot()));
        poseStack.translate(0, 0, -FRONT);
        poseStack.scale(scale, -scale, scale);
        Matrix4f matrix = poseStack.last().pose();
        font.drawInBatch(text, -font.width(text) / 2.0F, -font.lineHeight / 2.0F + 1, open ? OPEN_COLOR : CLOSED_COLOR, false, matrix, buffer,
                Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        poseStack.popPose();
    }
}
