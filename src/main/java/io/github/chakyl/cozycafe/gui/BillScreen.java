package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.data.CafeBill;
import io.github.chakyl.cozycafe.data.CafeMenuEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/** The guest's bill: what was ordered, the sum, five payment slots and the star rating. */
@Environment(EnvType.CLIENT)
public class BillScreen extends AbstractContainerScreen<BillMenu> {
    private static final int PAPER = 0xFFF3EAD7;
    private static final int INK = 0x3A2A1E;
    private static final int LINES_TOP = 18;
    private static final int LINE_HEIGHT = 9;
    private static final int STARS_Y = 108;
    private static final int STAR_X = 52;
    private static final int STAR_W = 12;

    public BillScreen(BillMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 244;
        this.inventoryLabelY = BillMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        gfx.fill(left, top, left + this.imageWidth, top + this.imageHeight, 0xFFC6C6C6);
        gfx.fill(left + 4, top + 4, left + this.imageWidth - 4, top + BillMenu.SLOT_Y + 22, PAPER);
        gfx.renderOutline(left, top, this.imageWidth, this.imageHeight, 0xFF555555);
        for (Slot slot : this.menu.slots) {
            gfx.fill(left + slot.x - 1, top + slot.y - 1, left + slot.x + 17, top + slot.y + 17, 0xFF8B8B8B);
            gfx.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, 0xFFA9A9A9);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(this.font, this.title, 8, 7, INK, false);
        gfx.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
        CafeBill bill = this.menu.bill();
        if (bill == null) return;

        List<CafeMenuEntry> lines = bill.getLines();
        if (bill.isDebt()) {
            gfx.drawString(this.font, Component.translatable("gui.cozyrestaurant.bill.debt_line"), 10, LINES_TOP, INK, false);
            CafeUi.drawPrice(gfx, this.font, bill.getSum(), bill.getCurrency(), this.imageWidth - 10 - CafeUi.priceWidth(this.font, bill.getSum()), LINES_TOP, INK, false);
            gfx.drawString(this.font, Component.translatable("gui.cozyrestaurant.bill.debt_hint"), 10, LINES_TOP + LINE_HEIGHT * 2, 0x8A7A62, false);
        }
        for (int i = 0; i < lines.size(); i++) {
            CafeMenuEntry line = lines.get(i);
            int y = LINES_TOP + i * LINE_HEIGHT;
            String name = this.font.plainSubstrByWidth(line.item().getHoverName().getString(), 120);
            gfx.drawString(this.font, name, 10, y, INK, false);
            CafeUi.drawPrice(gfx, this.font, line.price(), bill.getCurrency(), this.imageWidth - 10 - CafeUi.priceWidth(this.font, line.price()), y, INK, false);
        }
        int totalY = LINES_TOP + 6 * LINE_HEIGHT + 2;
        gfx.fill(10, totalY - 2, this.imageWidth - 10, totalY - 1, 0xFF9C8A70);
        gfx.drawString(this.font, Component.translatable("gui.cozyrestaurant.bill.total"), 10, totalY + 1, INK, false);
        CafeUi.drawPrice(gfx, this.font, bill.getSum(), bill.getCurrency(), this.imageWidth - 10 - CafeUi.priceWidth(this.font, bill.getSum()), totalY + 1, INK, false);
        int paid = bill.currencyPaid();
        Component paidText = Component.translatable("gui.cozyrestaurant.bill.paid", paid, bill.getSum());
        gfx.drawString(this.font, paidText, 10, totalY + 12, bill.hasEnoughMoney() ? 0x2E7D32 : 0xB0461A, false);

        // Rating (not for a debt)
        if (bill.needsRating()) {
            gfx.drawString(this.font, Component.translatable("gui.cozyrestaurant.bill.rating"), 10, STARS_Y + 2, INK, false);
            int hovered = this.hoveredStar(mouseX, mouseY);
            int shown = hovered > 0 ? hovered : bill.getRating();
            for (int i = 1; i <= 5; i++) {
                gfx.drawString(this.font, i <= shown ? "★" : "☆", STAR_X + (i - 1) * STAR_W, STARS_Y + 2, i <= shown ? 0xE0A000 : 0x9C8A70, false);
            }
        }
        // Status light: orange until paid and rated, then green.
        boolean done = bill.isPaid();
        // On its own line: long sums would run into "Внесено: X из Y".
        int statusY = totalY + 23;
        int lightX = this.imageWidth - 18;
        gfx.fill(lightX, statusY - 1, lightX + 8, statusY + 7, done ? 0xFF3FBF3F : 0xFFF08A24);
        Component status = done ? Component.translatable("gui.cozyrestaurant.bill.status_paid")
                : !bill.hasEnoughMoney() ? Component.translatable("gui.cozyrestaurant.bill.status_money")
                : Component.translatable("gui.cozyrestaurant.bill.status_rate");
        gfx.drawString(this.font, status, lightX - 4 - this.font.width(status), statusY, done ? 0x2E7D32 : 0xB0461A, false);
    }

    /** 1-5 under the mouse, or 0. */
    private int hoveredStar(int mouseX, int mouseY) {
        CafeBill bill = this.menu.bill();
        if (bill == null || !bill.needsRating()) return 0;
        int x = mouseX - this.leftPos - STAR_X;
        int y = mouseY - this.topPos - STARS_Y;
        if (x < 0 || x >= 5 * STAR_W || y < 0 || y >= 11) return 0;
        return x / STAR_W + 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int star = this.hoveredStar((int) mouseX, (int) mouseY);
        if (star > 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, star);
            if (this.minecraft.player != null) {
                this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4F, 1.0F);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);
    }
}
