package io.github.chakyl.cozycafe.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Shared drawing helpers of the cafe screens. */
@Environment(EnvType.CLIENT)
public final class CafeUi {
    /** Size of the currency icon drawn next to a price. */
    public static final int ICON = 10;

    private CafeUi() {
    }

    /** Width of {@link #drawPrice} for this price. */
    public static int priceWidth(Font font, int price) {
        return font.width(String.valueOf(price)) + 1 + ICON;
    }

    /** Draws a price the way guests see it: the number, then the currency item's icon to its right. */
    public static void drawPrice(GuiGraphics gfx, Font font, int price, ItemStack currency, int x, int y, int color, boolean shadow) {
        String text = String.valueOf(price);
        gfx.drawString(font, text, x, y, color, shadow);
        drawIcon(gfx, currency, x + font.width(text) + 1, y - 1, ICON);
    }

    /** Draws an item scaled to {@code size} pixels. */
    public static void drawIcon(GuiGraphics gfx, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) return;
        gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0);
        float scale = size / 16.0F;
        gfx.pose().scale(scale, scale, 1.0F);
        gfx.renderFakeItem(stack, 0, 0);
        gfx.pose().popPose();
    }

    /** "1 234 567" style grouping for big amounts in the cash register. */
    public static String formatAmount(long amount) {
        String digits = Long.toString(amount);
        StringBuilder out = new StringBuilder();
        int lead = digits.length() % 3;
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (i - lead) % 3 == 0) out.append(' ');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }
}
