package io.github.chakyl.cozycafe.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.ItemBlockRenderTypes;

import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GeneralUtils {

    public static int getDay(Level level) {
        return (int) (Math.floor((double) level.dayTime() / 24000) + 1);
    }

    public static String formatPrice(int number) {
        return formatPrice(String.valueOf(number), true);
    }

    public static String formatPrice(String number, boolean truncateMillionsBillions) {
        if (truncateMillionsBillions) {
            if (number.length() < 4) return number;
            if (number.length() > 9) return number.charAt(0) + "." + number.charAt(1) + "B";
            if (number.length() > 6) {
                StringBuilder out = new StringBuilder(3);
                for (int i = 0; i < number.length() - 6; i++) {
                    out.append(number.charAt(i));
                }
                if (number.length() == 7) {
                    out.append('.');
                    out.append(number.charAt(1));
                }
                out.append("M");
                return out.toString();
            }
        }
        int start = number.length() % 3;
        StringBuilder out = new StringBuilder(number.length() + (number.length() / 3));
        out.append(number, 0, start);
        for (int i = 0; i < number.length() / 3; i++) {
            if (i != 0 || start != 0) out.append(",");
            out.append(number, i * 3 + start, i * 3 + start + 3);
        }
        return out.toString();
    }

    /**
     * Where the items of a plate lie: {x offset, z offset, scale} per item, offsets in blocks from the plate centre.
     */
    public static float[][] plateLayout(int count) {
        return switch (count) {
            case 0 -> new float[0][];
            case 1 -> new float[][]{{0, 0, 1.0F}};
            case 2 -> new float[][]{{-0.17F, 0, 0.55F}, {0.17F, 0, 0.55F}};
            case 3 -> new float[][]{{-0.17F, -0.12F, 0.5F}, {0.17F, -0.12F, 0.5F}, {0, 0.15F, 0.5F}};
            case 4 -> new float[][]{{-0.16F, -0.14F, 0.45F}, {0.16F, -0.14F, 0.45F}, {-0.16F, 0.14F, 0.45F}, {0.16F, 0.14F, 0.45F}};
            case 5 -> new float[][]{{-0.21F, -0.13F, 0.38F}, {0, -0.13F, 0.38F}, {0.21F, -0.13F, 0.38F}, {-0.11F, 0.14F, 0.38F}, {0.11F, 0.14F, 0.38F}};
            default -> new float[][]{{-0.21F, -0.13F, 0.38F}, {0, -0.13F, 0.38F}, {0.21F, -0.13F, 0.38F}, {-0.21F, 0.14F, 0.38F}, {0, 0.14F, 0.38F}, {0.21F, 0.14F, 0.38F}};
        };
    }

    // TODO: Find a way to merge renderFood and getFoodModel into just one public method?

    // Nullability here is a bit scuffed
    /** Largest a block on a plate may be, in blocks: as wide as its place on the plate (at slot scale 1), this high. */
    private static final float PLATE_BLOCK_WIDTH = 0.55F;
    private static final float PLATE_BLOCK_HEIGHT = 0.4F;

    public static void renderFood(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, @Nullable BlockEntity blockEnt, int light, int overlay) {
        renderFood(stack, poseStack, buffer, blockEnt, light, overlay, 1.0F);
    }

    /**
     * @param slotScale the scale the caller already applied for this place on the plate; a block is fitted into
     *                  {@link #PLATE_BLOCK_WIDTH} by {@link #PLATE_BLOCK_HEIGHT} real blocks whatever it is.
     */
    public static void renderFood(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, @Nullable BlockEntity blockEnt, int light, int overlay, float slotScale) {
        Minecraft minecraft = Minecraft.getInstance();

        if (stack.getItem() instanceof BlockItem blockItem) {
            if (blockEnt == null || blockEnt.getLevel() == null) {
                return;
            }

            poseStack.pushPose();

            BlockState blockState = blockItem.getBlock().defaultBlockState();
            BakedModel model = minecraft.getBlockRenderer().getBlockModel(blockState);

            RandomSource random = RandomSource.create();
            RenderType renderType = ItemBlockRenderTypes.getChunkRenderType(blockState);

            // Fit the block's real shape into its place: a cake stays big, a full block becomes a small cube.
            AABB box = shapeBounds(blockState);
            float width = (float) Math.max(box.getXsize(), box.getZsize());
            float height = (float) box.getYsize();
            float fit = Math.min(1.0F, Math.min(PLATE_BLOCK_WIDTH / width, PLATE_BLOCK_HEIGHT / Math.max(slotScale, 0.01F) / height));
            poseStack.scale(fit, fit, fit);
            // Block models have block coordinates: stand the shape's centre on the origin, its bottom on the plate.
            poseStack.translate(-(box.minX + box.maxX) / 2.0D, -box.minY, -(box.minZ + box.maxZ) / 2.0D);

            VertexConsumer consumer = buffer.getBuffer(renderType);
            minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(
                    blockEnt.getLevel(),
                    model,
                    blockState,
                    blockEnt.getBlockPos(),
                    poseStack,
                    consumer,
                    false,
                    random,
                    42L,
                    overlay
            );

            poseStack.popPose();

        } else {
            poseStack.pushPose();

            poseStack.translate(0f, 0.05f, 0f);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));

            minecraft.getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    light,
                    overlay,
                    poseStack,
                    buffer,
                    null,
                    0
            );

            poseStack.popPose();
        }
    }

    /** The block's outline; a block without one (a flower, say) counts as a whole block. */
    private static AABB shapeBounds(BlockState state) {
        try {
            VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (!shape.isEmpty()) {
                AABB box = shape.bounds();
                if (box.getXsize() > 0.01 && box.getYsize() > 0.01 && box.getZsize() > 0.01) return box;
            }
        } catch (RuntimeException ignored) {
            // Some modded blocks need a real level for their shape.
        }
        return new AABB(0, 0, 0, 1, 1, 1);
    }

    public static BakedModel getFoodModel(ItemStack foodItem){
        if (foodItem.getItem() instanceof BlockItem blockItem) {
            BlockState foodBlockState = blockItem.getBlock().defaultBlockState();
            return Minecraft.getInstance().getBlockRenderer().getBlockModel(foodBlockState);
        }

        return Minecraft.getInstance().getItemRenderer().getModel(foodItem, null, null, 0);
    }
}
