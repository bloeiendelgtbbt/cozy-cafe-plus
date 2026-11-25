package io.github.chakyl.cozycafe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.chakyl.cozycafe.CozyCafe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the apron snugly over the body and upper legs (uses the thin leggings-layer model).
 * Does not touch the player's skin.
 */
@Environment(EnvType.CLIENT)
public class ApronArmorRenderer implements ArmorRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(CozyCafe.MODID, "textures/models/armor/apron.png");
    private HumanoidModel<LivingEntity> model;

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, ItemStack stack, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel<LivingEntity> contextModel) {
        if (this.model == null) {
            this.model = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        }
        contextModel.copyPropertiesTo(this.model);
        this.model.setAllVisible(false);
        this.model.body.visible = true;
        this.model.rightLeg.visible = true;
        this.model.leftLeg.visible = true;
        ArmorRenderer.renderPart(matrices, vertexConsumers, light, stack, this.model, TEXTURE);
    }
}
