package io.github.chakyl.cozycafe.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.chakyl.cozycafe.client.ClientHooks;
import io.github.chakyl.cozycafe.util.CafeRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the cafe role ("Владелец" / "Официант") as a second line under the player's name tag.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void cozyrestaurant$renderRole(AbstractClientPlayer player, Component name, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        CafeRole role = ClientHooks.getRole(player.getUUID());
        if (role == null || this.entityRenderDispatcher.distanceToSqr(player) > 4096.0) return;

        Component label = Component.translatable(role.translationKey);
        boolean visible = !player.isDiscrete();
        poseStack.pushPose();
        poseStack.translate(0.0F, player.getNameTagOffsetY(), 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        Font font = this.getFont();
        float x = -font.width(label) / 2.0F;
        float y = 10.0F; // one line under the name
        int seeThroughColor = (visible ? 0x20000000 : 0x20000000) | role.color;
        font.drawInBatch(label, x, y, seeThroughColor, false, matrix, buffer,
                visible ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, background, packedLight);
        if (visible) {
            font.drawInBatch(label, x, y, 0xFF000000 | role.color, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, packedLight);
        }
        poseStack.popPose();
    }
}
