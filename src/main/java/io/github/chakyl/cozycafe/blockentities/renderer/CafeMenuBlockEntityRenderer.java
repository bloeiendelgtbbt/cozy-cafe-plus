package io.github.chakyl.cozycafe.blockentities.renderer;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.chakyl.cozycafe.CozyCafe;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.blocks.CafeMenuBlock;
import io.github.chakyl.cozycafe.util.CustomerSkinInfo;
import io.github.chakyl.cozycafe.util.CustomerSkinUtils;
import io.github.chakyl.cozycafe.util.GeneralUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.Font;
import org.joml.Matrix4f;
import java.util.List;
import net.minecraft.world.item.ItemDisplayContext;
import io.github.chakyl.cozycafe.registry.CozyRegistry;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;


public class CafeMenuBlockEntityRenderer implements BlockEntityRenderer<CafeMenuBlockEntity> {
    private final ItemRenderer itemRenderer;

    private final PlayerModel<?> wideModel;
    private final PlayerModel<?> slimModel;

    public CafeMenuBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();

        this.wideModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(CafeMenuBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        float ticks = blockEntity.getLevel().getGameTime() + partialTick;
        List<ItemStack> order = blockEntity.getOrder();
        if (!order.isEmpty()) {
            EntityRenderDispatcher renderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            long remaining = Math.max(0, blockEntity.getOrderDeadline() - blockEntity.getLevel().getGameTime());
            float progress = blockEntity.getOrderDuration() > 0 ? 1.0F - (float) remaining / blockEntity.getOrderDuration() : 0.0F;
            // A player guest's order floats above their own head (over the nametag); an NPC's above the table.
            // Low over the menu sheet, so a ceiling right above the table (a second floor) does not hide it.
            double x = 0.5, y = 0.3, z = 0.5;
            double timerAbove = 0.75;
            boolean show = true;
            if (blockEntity.getGuestPlayer() != null) {
                Player guest = blockEntity.getLevel().getPlayerByUUID(blockEntity.getGuestPlayer());
                if (guest == null || (guest == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson())) {
                    show = false;
                } else {
                    Vec3 at = guest.getPosition(partialTick);
                    x = at.x - blockEntity.getBlockPos().getX();
                    y = at.y - blockEntity.getBlockPos().getY() + guest.getBbHeight() + 0.75;
                    timerAbove = 0.55;
                    z = at.z - blockEntity.getBlockPos().getZ();
                }
            }
            if (show) {
                poseStack.pushPose();
                poseStack.translate(x, y + (float) Math.sin(ticks * 0.06f) * 0.05f, z);
                poseStack.mulPose(Axis.YP.rotationDegrees(-renderDispatcher.camera.getYRot()));
                if (progress >= 0.5f) {
                    float speedMultiplier = progress >= 0.85f ? 3.5f : progress >= 0.7f ? 2.2f : 1.0f;
                    float amplitude = progress >= 0.85f ? 12.0f : progress >= 0.7f ? 8.0f : 4.0f;
                    poseStack.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(ticks * 0.2f * speedMultiplier) * amplitude));
                }
                float spacing = order.size() > 3 ? 0.32F : 0.42F;
                float scale = order.size() == 1 ? 1.5F : order.size() > 3 ? 0.85F : 1.1F;
                for (int i = 0; i < order.size(); i++) {
                    poseStack.pushPose();
                    poseStack.translate((i - (order.size() - 1) / 2.0F) * spacing, 0, 0);
                    poseStack.scale(scale, scale, scale);
                    this.itemRenderer.renderStatic(order.get(i), ItemDisplayContext.GROUND, packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getLevel(), (int) blockEntity.getBlockPos().asLong() + i);
                    poseStack.popPose();
                }
                poseStack.popPose();
                this.renderTimer(remaining, progress, x, y + timerAbove, z, poseStack, bufferSource, packedLight, renderDispatcher);
            }
        } else if (blockEntity.getPhase() == CafeMenuBlockEntity.Phase.AFTER_MEAL && blockEntity.getOrderDeadline() > 0) {
            // The guest (player or NPC) has eaten: how long the staff have left to bring the bill.
            long remaining = Math.max(0, blockEntity.getOrderDeadline() - blockEntity.getLevel().getGameTime());
            float progress = blockEntity.getOrderDuration() > 0 ? 1.0F - (float) remaining / blockEntity.getOrderDuration() : 0.0F;
            this.renderTimer(remaining, progress, 0.5, 0.9, 0.5, poseStack, bufferSource, packedLight, Minecraft.getInstance().getEntityRenderDispatcher());
        }
        // Food on the table (also a part served while the rest of the order is still coming).
        if (!blockEntity.getEating().isEmpty()) {
            List<ItemStack> eating = blockEntity.getEating();
            Direction facing = blockEntity.getBlockState().getValue(CafeMenuBlock.FACING).getOpposite();
            float[][] layout = GeneralUtils.plateLayout(eating.size());
            for (int i = 0; i < layout.length; i++) {
                poseStack.pushPose();
                poseStack.translate(0.5f, 0.1f, 0.5f);
                poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
                poseStack.translate(layout[i][0], 0, layout[i][1]);
                poseStack.scale(layout[i][2], layout[i][2], layout[i][2]);
                GeneralUtils.renderFood(eating.get(i), poseStack, bufferSource, blockEntity, packedLight, packedOverlay, layout[i][2]);
                poseStack.popPose();
            }
        }
        if (blockEntity.getBill() != null) {
            this.renderDot(blockEntity.getBill().isPaid() ? 0xFF55FF55 : 0xFFFFAA00, poseStack, bufferSource);
        } else if (blockEntity.isLeftOwing()) {
            // The guest left owing: what is on the table can be cleared away.
            this.renderDot(0xFFFF4444, poseStack, bufferSource);
        }
        CustomerSkinInfo customerSkin = blockEntity.getHasCustomer() ? CustomerSkinUtils.getCustomerSkinInfo(blockEntity.getGameProfile()) : null;
        // customerSkin is null while the skin texture is still loading: skip drawing the customer until then.
        if (customerSkin != null) {
            poseStack.pushPose();

            PlayerModel<?> playerModel = customerSkin.isSlim() ? slimModel : wideModel;

            Direction facing = blockEntity.getBlockState().getValue(CafeMenuBlock.FACING);
            poseStack.translate(0.5F + facing.getStepX(), 1.7F, 0.5F + facing.getStepZ());
            poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));

            float modelScale = 1.85F;
            poseStack.scale(modelScale, modelScale, modelScale);
            poseStack.scale(-1.0F, -1.0F, 1.0F);

            playerModel.head.xScale = 0.7F;
            playerModel.head.yScale = 0.7F;
            playerModel.head.zScale = 0.7F;


            playerModel.rightLeg.xRot = -1.5F;
            playerModel.rightLeg.yRot = 0.3F;
            playerModel.leftLeg.xRot = -1.5F;
            playerModel.leftLeg.yRot = -0.3F;

            float sway = (float) Math.cos(ticks * 0.09f) * 0.05f;
            playerModel.rightArm.xRot = -0.5f + sway;
            playerModel.rightArm.zRot = 0.05f + (sway * 0.2f);
            playerModel.leftArm.xRot = -0.5f + sway;
            playerModel.leftArm.zRot = -0.05f - (sway * 0.2f);

            playerModel.jacket.copyFrom(playerModel.body);
            playerModel.rightPants.copyFrom(playerModel.rightLeg);
            playerModel.leftPants.copyFrom(playerModel.leftLeg);
            playerModel.rightSleeve.copyFrom(playerModel.rightArm);
            playerModel.leftSleeve.copyFrom(playerModel.leftArm);

            ResourceLocation textureLocation = customerSkin.location();

            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(textureLocation));
            playerModel.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
            poseStack.popPose();
        }
    }

    /**
     * The bill itself is the block model (it replaces the menu book); this adds its glowing light at the far end:
     * orange until paid, green once paid.
     */
    /** A dot floats above the bill like a name tag: orange until paid (and rated), then green. */
    private void renderDot(int color, PoseStack poseStack, MultiBufferSource bufferSource) {
        String text = "●";
        Font font = Minecraft.getInstance().font;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        poseStack.pushPose();
        poseStack.translate(0.5, 0.75, 0.5);
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.scale(-0.035F, -0.035F, 0.035F);
        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(text) / 2.0F;
        // Like a name tag: a faint copy shows through blocks, the bright one on top.
        font.drawInBatch(text, x, 0, 0x20FFFFFF, false, matrix, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, x, 0, color, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CafeMenuBlockEntity blockEntity) {
        // The order of a player guest is drawn above their head, outside the table's own box.
        // Food and the order hang over the table's own block too.
        return blockEntity.getGuestPlayer() != null || !blockEntity.getEating().isEmpty() || !blockEntity.getOrder().isEmpty();
    }

    /** Time the customer still waits, above the ordered items; red in the last quarter. */
    private void renderTimer(long remainingTicks, float progress, double x, double y, double z, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, EntityRenderDispatcher dispatcher) {
        long seconds = (remainingTicks + 19) / 20;
        String text = seconds >= 3600
                ? (seconds / 3600) + ":" + String.format("%02d:%02d", (seconds / 60) % 60, seconds % 60)
                : (seconds / 60) + ":" + String.format("%02d", seconds % 60);
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        int color = progress >= 0.75F ? 0xFFFF5555 : 0xFFFFFFFF;
        float textX = -font.width(text) / 2.0F;
        // Like a name tag, but clearer: a see-through copy keeps it readable under a ceiling or a floor above.
        font.drawInBatch(text, textX, 0, (color & 0xFFFFFF) | 0x90000000, false, matrix, bufferSource, Font.DisplayMode.SEE_THROUGH, 0x40000000, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, textX, 0, color, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
