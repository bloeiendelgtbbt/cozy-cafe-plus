package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.CozyCafe;
import io.github.chakyl.cozycafe.client.ClientHandovers;
import io.github.chakyl.cozycafe.data.CafeHandovers;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A staff member's own tablet (right-click any cafe manager that isn't yours): the bills you still have to hand in,
 * in every cafe of every world. Information only; Esc closes it.
 */
@Environment(EnvType.CLIENT)
public class MyBillsScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation(CozyCafe.MODID, "textures/gui/cafe_manager.png");
    private static final int WIDTH = 176;
    private static final int HEIGHT = 225;
    private static final int PAPER = 0xFFCCB6A0;
    private static final int RULE = 0xFFAF9982;
    private static final int INK = 0xFF4A2E1F;
    private static final int SOFT_INK = 0xFF7A5E4A;
    private static final int DEBT = 0xFFA01818;
    private static final int ROW_HEIGHT = 32;
    private static final int VISIBLE_ROWS = 4;
    private int left;
    private int top;
    private int scroll;

    public MyBillsScreen() {
        super(Component.translatable("gui.cozyrestaurant.my_bills.title"));
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(delta), 0, Math.max(0, ClientHandovers.all().size() - VISIBLE_ROWS));
        return true;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        gfx.blit(TEXTURE, left, top, 0, 0.0F, 0.0F, WIDTH, HEIGHT, 256, 256);
        // Blank paper over the owner's grid.
        gfx.fill(left + 8, top + 36, left + 168, top + 214, PAPER);
        gfx.drawString(this.font, this.title, left + WIDTH / 2 - this.font.width(this.title) / 2, top + 22, 0x181425, false);

        List<CafeHandovers.Summary> bills = ClientHandovers.all();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, bills.size() - VISIBLE_ROWS));
        if (bills.isEmpty()) {
            for (FormattedCharSequence line : this.font.split(Component.translatable("gui.cozyrestaurant.my_bills.empty"), WIDTH - 30)) {
                gfx.drawString(this.font, line, left + WIDTH / 2 - this.font.width(line) / 2, top + 60, SOFT_INK, false);
            }
        }
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < VISIBLE_ROWS && i + this.scroll < bills.size(); i++) {
            CafeHandovers.Summary bill = bills.get(i + this.scroll);
            int y = top + 42 + i * ROW_HEIGHT;
            gfx.drawString(this.font, this.font.plainSubstrByWidth("«" + bill.cafeName() + "»", WIDTH - 28), left + 14, y, INK, false);
            String where = bill.pos().getX() + " " + bill.pos().getY() + " " + bill.pos().getZ();
            if (!Level.OVERWORLD.location().equals(bill.dimension())) where += " · " + bill.dimension().getPath();
            gfx.drawString(this.font, where, left + 14, y + 10, SOFT_INK, false);
            Component count = Component.translatable("gui.cozyrestaurant.my_bills.count", bill.count());
            gfx.drawString(this.font, count, left + 14, y + 20, DEBT, false);
            int x = left + 18 + this.font.width(count);
            for (ItemStack amount : bill.amounts()) {
                CafeUi.drawPrice(gfx, this.font, amount.getCount(), amount, x, y + 20, DEBT, false);
                int w = CafeUi.priceWidth(this.font, amount.getCount());
                if (mouseX >= x && mouseX < x + w && mouseY >= y + 18 && mouseY < y + 30) hovered = amount;
                x += w + 6;
            }
            gfx.fill(left + 12, y + ROW_HEIGHT - 3, left + 164, y + ROW_HEIGHT - 2, RULE);
        }
        if (bills.size() > VISIBLE_ROWS) {
            String page = (this.scroll + 1) + "–" + Math.min(bills.size(), this.scroll + VISIBLE_ROWS) + " / " + bills.size();
            gfx.drawString(this.font, page, left + WIDTH / 2 - this.font.width(page) / 2, top + 42 + VISIBLE_ROWS * ROW_HEIGHT, SOFT_INK, false);
        }
        List<FormattedCharSequence> hint = this.font.split(Component.translatable("gui.cozyrestaurant.my_bills.hint"), WIDTH - 28);
        int hintY = top + 210 - hint.size() * 9;
        for (FormattedCharSequence line : hint) {
            gfx.drawString(this.font, line, left + 14, hintY, SOFT_INK, false);
            hintY += 9;
        }
        super.render(gfx, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) gfx.renderTooltip(this.font, hovered.copyWithCount(1), mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
