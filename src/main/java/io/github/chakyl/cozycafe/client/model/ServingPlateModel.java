package io.github.chakyl.cozycafe.client.model;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.chakyl.cozycafe.util.GeneralUtils;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class ServingPlateModel implements BakedModel {
    private static final int STRIDE = DefaultVertexFormat.BLOCK.getIntegerSize();
    private static final int NORMAL_OFFSET = 7;
    private final BakedModel plateModel;
    private final List<ItemStack> foods;

    public ServingPlateModel(BakedModel plateModel, List<ItemStack> foods) {
        this.plateModel = plateModel;
        this.foods = foods;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        List<BakedQuad> quads = new ArrayList<>(plateModel.getQuads(state, side, rand));
        float[][] layout = GeneralUtils.plateLayout(this.foods.size());
        for (int n = 0; n < layout.length; n++) {
            ItemStack food = this.foods.get(n);
            BakedModel foodModel = GeneralUtils.getFoodModel(food);
            if (foodModel instanceof ServingPlateModel) continue;
            boolean block = food.getItem() instanceof BlockItem;
            float scale = layout[n][2];
            PoseStack poseStack = new PoseStack();
            poseStack.translate(0.5 + layout[n][0], 3f / 16.0f, 0.5 + layout[n][1]);
            poseStack.scale(scale, scale, scale);
            if (!block) poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.translate(-0.5, block ? -0.1 : -0.5, -0.5);
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            for (BakedQuad quad : foodModel.getQuads(state, side, rand)) {
                quads.add(transform(quad, pose, normal));
            }
        }
        return quads;
    }

    /**
     * Equivalent of Forge's QuadTransformers.applying(transformation): moves positions and rotates normals.
     */
    private static BakedQuad transform(BakedQuad quad, Matrix4f pose, Matrix3f normalMatrix) {
        int[] vertices = quad.getVertices().clone();
        Vector4f pos = new Vector4f();
        Vector3f normal = new Vector3f();
        for (int i = 0; i < 4; i++) {
            int base = i * STRIDE;
            pos.set(Float.intBitsToFloat(vertices[base]), Float.intBitsToFloat(vertices[base + 1]), Float.intBitsToFloat(vertices[base + 2]), 1.0F);
            pose.transform(pos);
            vertices[base] = Float.floatToRawIntBits(pos.x());
            vertices[base + 1] = Float.floatToRawIntBits(pos.y());
            vertices[base + 2] = Float.floatToRawIntBits(pos.z());

            int packed = vertices[base + NORMAL_OFFSET];
            if ((packed & 0xFFFFFF) != 0) {
                normal.set(((byte) packed) / 127.0F, ((byte) (packed >> 8)) / 127.0F, ((byte) (packed >> 16)) / 127.0F);
                normalMatrix.transform(normal);
                normal.normalize();
                vertices[base + NORMAL_OFFSET] = (packed & 0xFF000000)
                        | ((((byte) (normal.x() * 127.0F)) & 0xFF))
                        | ((((byte) (normal.y() * 127.0F)) & 0xFF) << 8)
                        | ((((byte) (normal.z() * 127.0F)) & 0xFF) << 16);
            }
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return plateModel.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return plateModel.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return plateModel.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return plateModel.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return plateModel.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
