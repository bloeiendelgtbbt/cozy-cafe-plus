package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.client.FeedbackScreen;
import io.github.chakyl.cozycafe.data.CafeRevenue;
import io.github.chakyl.cozycafe.network.EvilPacketsIHateThem;
import io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket;
import io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket.Action;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * "Касса" tab of the Cafe Manager: the money collected from bills, kept here until the owner takes it.
 */
@Environment(EnvType.CLIENT)
public class RevenueScreen extends Screen implements FeedbackScreen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 222;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 7;

    private final Screen parent;
    private final CafeManagerBlockEntity cafe;
    private Button withdrawButton;
    private int scroll;
    private int withdrawCooldown;
    private Component feedback;
    private int feedbackTicks;
    private int left;
    private int top;

    public RevenueScreen(Screen parent, CafeManagerBlockEntity cafe) {
        super(Component.translatable("gui.cozyrestaurant.revenue.title"));
        this.parent = parent;
        this.cafe = cafe;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = (this.height - PANEL_HEIGHT) / 2;
        this.withdrawButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.revenue.withdraw"),
                        b -> {
                            EvilPacketsIHateThem.sendToServer(new ServerBoundCafeActionPacket(this.cafe.getBlockPos(), Action.WITHDRAW));
                            // A double click must not send a second request into the now empty register.
                            this.withdrawCooldown = 10;
                            b.active = false;
                        })
                .bounds(left + 10, top + PANEL_HEIGHT - 28, 110, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.back"), b -> this.onClose())
                .bounds(left + PANEL_WIDTH - 90, top + PANEL_HEIGHT - 28, 80, 20).build());
    }

    @Override
    public void showFeedback(Component message) {
        this.feedback = message;
        this.feedbackTicks = 100;
    }

    @Override
    public void tick() {
        if (this.cafe.isRemoved() || this.minecraft.player == null
                || this.minecraft.player.distanceToSqr(this.cafe.getBlockPos().getCenter()) > 100) {
            this.onClose();
            return;
        }
        if (this.withdrawCooldown > 0) this.withdrawCooldown--;
        this.withdrawButton.active = this.withdrawCooldown == 0 && !this.cafe.getRevenue().isEmpty();
        if (this.feedbackTicks > 0 && --this.feedbackTicks == 0) this.feedback = null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, this.cafe.getRevenue().entries().size() - VISIBLE_ROWS);
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(delta), 0, max);
        return true;
    }

    private int rowY(int row) {
        return this.top + 34 + row * ROW_HEIGHT;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        gfx.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0202020);
        gfx.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFC9A227);
        gfx.drawCenteredString(this.font, this.title, left + PANEL_WIDTH / 2, top + 10, 0xFFFFFF);

        List<CafeRevenue.Entry> entries = this.cafe.getRevenue().entries();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, entries.size() - VISIBLE_ROWS));
        if (entries.isEmpty()) {
            gfx.drawCenteredString(this.font, Component.translatable("gui.cozyrestaurant.revenue.empty"), left + PANEL_WIDTH / 2, rowY(0) + 4, 0x9A9A9A);
        }
        CafeRevenue.Entry hovered = null;
        for (int i = 0; i < VISIBLE_ROWS && i + this.scroll < entries.size(); i++) {
            CafeRevenue.Entry entry = entries.get(i + this.scroll);
            int y = rowY(i);
            gfx.fill(left + 10, y - 2, left + PANEL_WIDTH - 10, y + ROW_HEIGHT - 4, (i % 2 == 0) ? 0x40FFFFFF : 0x20FFFFFF);
            gfx.renderFakeItem(entry.template(), left + 13, y - 1);
            String name = this.font.plainSubstrByWidth(entry.template().getHoverName().getString(), 110);
            gfx.drawString(this.font, name, left + 34, y + 3, 0xFFFFFF);
            String amount = "× " + CafeUi.formatAmount(entry.count());
            gfx.drawString(this.font, amount, left + PANEL_WIDTH - 14 - this.font.width(amount), y + 3, 0xFFE08A);
            if (mouseX >= left + 10 && mouseX < left + PANEL_WIDTH - 10 && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 4) hovered = entry;
        }
        if (entries.size() > VISIBLE_ROWS) {
            gfx.drawCenteredString(this.font, (this.scroll + 1) + "–" + Math.min(entries.size(), this.scroll + VISIBLE_ROWS) + " / " + entries.size(), left + PANEL_WIDTH / 2, rowY(VISIBLE_ROWS) - 2, 0x9A9A9A);
        }

        List<FormattedCharSequence> hint = this.font.split(Component.translatable("gui.cozyrestaurant.revenue.hint"), PANEL_WIDTH - 20);
        int hintY = top + PANEL_HEIGHT - 34 - hint.size() * 10;
        for (FormattedCharSequence line : hint) {
            gfx.drawString(this.font, line, left + 10, hintY, 0x9A9A9A);
            hintY += 10;
        }
        if (this.feedback != null) {
            gfx.drawCenteredString(this.font, this.feedback, left + PANEL_WIDTH / 2, top + 22, 0xFFFFFF);
        }
        super.render(gfx, mouseX, mouseY, partialTick);
        if (hovered != null) {
            gfx.renderTooltip(this.font, hovered.template(), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
