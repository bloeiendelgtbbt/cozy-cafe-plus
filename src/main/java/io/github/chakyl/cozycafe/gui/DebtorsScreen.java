package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.data.CafeDebts;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * "Должники" tab of the Cafe Manager: who ate and left without paying, and how much they owe.
 * Read straight from the client copy of the block entity, so it refreshes when a debt appears or is paid.
 */
@Environment(EnvType.CLIENT)
public class DebtorsScreen extends Screen implements io.github.chakyl.cozycafe.client.FeedbackScreen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 222;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 7;

    private final Screen parent;
    private final CafeManagerBlockEntity cafe;
    private static final int BUTTON_WIDTH = 60;
    private final List<Button> forgiveButtons = new ArrayList<>();
    /** The debtor whose button was clicked once: the second click within 3 seconds forgives. */
    private java.util.UUID armed;
    private int armedTicks;
    private int lastCount = -1;
    private Component feedback;
    private int feedbackTicks;
    private int scroll;
    private int left;
    private int top;

    public DebtorsScreen(Screen parent, CafeManagerBlockEntity cafe) {
        super(Component.translatable("gui.cozyrestaurant.debtors.title"));
        this.parent = parent;
        this.cafe = cafe;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = (this.height - PANEL_HEIGHT) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.waiters.back"), b -> this.onClose())
                .bounds(left + PANEL_WIDTH / 2 - 40, top + PANEL_HEIGHT - 28, 80, 20).build());
        this.rebuildButtons();
    }

    private void rebuildButtons() {
        this.forgiveButtons.forEach(this::removeWidget);
        this.forgiveButtons.clear();
        List<java.util.UUID> ids = new ArrayList<>(this.cafe.getDebts().all().keySet());
        this.lastCount = ids.size();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, ids.size() - VISIBLE_ROWS));
        for (int i = 0; i < VISIBLE_ROWS && i + this.scroll < ids.size(); i++) {
            java.util.UUID id = ids.get(i + this.scroll);
            boolean armedHere = id.equals(this.armed);
            Button forgive = Button.builder(Component.translatable(armedHere ? "gui.cozyrestaurant.debtors.confirm" : "gui.cozyrestaurant.debtors.forgive"), b -> this.clickForgive(id))
                    .bounds(left + PANEL_WIDTH - 12 - BUTTON_WIDTH, rowY(i) - 1, BUTTON_WIDTH, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.cozyrestaurant.debtors.forgive_tooltip")))
                    .build();
            this.forgiveButtons.add(forgive);
            this.addRenderableWidget(forgive);
        }
    }

    private long armedAt;

    private void clickForgive(java.util.UUID id) {
        long now = net.minecraft.Util.getMillis();
        // A double click must not confirm by itself: the second click counts only after a short pause.
        if (id.equals(this.armed) && now - this.armedAt < 300) return;
        if (id.equals(this.armed)) {
            io.github.chakyl.cozycafe.network.EvilPacketsIHateThem.sendToServer(new io.github.chakyl.cozycafe.network.ServerBoundForgiveDebtPacket(this.cafe.getBlockPos(), id));
            this.armed = null;
        } else {
            this.armed = id;
            this.armedAt = now;
            this.armedTicks = 60;
        }
        this.rebuildButtons();
    }

    @Override
    public void showFeedback(Component message) {
        this.feedback = message;
        this.feedbackTicks = 100;
    }

    private List<CafeDebts.Debt> debts() {
        return new ArrayList<>(this.cafe.getDebts().all().values());
    }

    private int rowY(int row) {
        return this.top + 30 + row * ROW_HEIGHT;
    }

    @Override
    public void tick() {
        if (this.cafe.isRemoved() || this.minecraft.player == null
                || this.minecraft.player.distanceToSqr(this.cafe.getBlockPos().getCenter()) > 100) {
            this.onClose();
            return;
        }
        if (this.armedTicks > 0 && --this.armedTicks == 0) {
            this.armed = null;
            this.rebuildButtons();
        }
        if (this.feedbackTicks > 0 && --this.feedbackTicks == 0) this.feedback = null;
        if (this.debts().size() != this.lastCount) this.rebuildButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int newScroll = Mth.clamp(this.scroll - (int) Math.signum(delta), 0, Math.max(0, this.debts().size() - VISIBLE_ROWS));
        if (newScroll != this.scroll) {
            this.scroll = newScroll;
            this.rebuildButtons();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        gfx.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0202020);
        gfx.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFC24A3F);
        gfx.drawCenteredString(this.font, this.title, left + PANEL_WIDTH / 2, top + 10, 0xFFFFFF);

        List<CafeDebts.Debt> debts = this.debts();
        if (debts.isEmpty()) {
            gfx.drawCenteredString(this.font, Component.translatable("gui.cozyrestaurant.debtors.empty"), left + PANEL_WIDTH / 2, rowY(0) + 4, 0x9A9A9A);
        }
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < VISIBLE_ROWS && i + this.scroll < debts.size(); i++) {
            CafeDebts.Debt debt = debts.get(i + this.scroll);
            int y = rowY(i);
            gfx.fill(left + 10, y - 2, left + PANEL_WIDTH - 10, y + ROW_HEIGHT - 4, (i % 2 == 0) ? 0x40FFFFFF : 0x20FFFFFF);
            gfx.drawString(this.font, this.font.plainSubstrByWidth((i + this.scroll + 1) + ". " + debt.name(), 80), left + 14, y + 3, 0xFF7A6E);
            // Amounts from the right: "3 [icon]  12 [icon]"
            int x = left + PANEL_WIDTH - 16 - BUTTON_WIDTH;
            for (ItemStack amount : debt.amounts()) {
                x -= CafeUi.priceWidth(this.font, amount.getCount());
                CafeUi.drawPrice(gfx, this.font, amount.getCount(), amount, x, y + 3, 0xFFE08A, true);
                if (mouseX >= x && mouseX < x + CafeUi.priceWidth(this.font, amount.getCount()) && mouseY >= y && mouseY < y + 14) hovered = amount;
                x -= 8;
            }
        }
        if (debts.size() > VISIBLE_ROWS) {
            gfx.drawCenteredString(this.font, (this.scroll + 1) + "–" + Math.min(debts.size(), this.scroll + VISIBLE_ROWS) + " / " + debts.size(), left + PANEL_WIDTH / 2, rowY(VISIBLE_ROWS) - 2, 0x9A9A9A);
        }

        List<FormattedCharSequence> hint = this.font.split(Component.translatable("gui.cozyrestaurant.debtors.hint"), PANEL_WIDTH - 20);
        int hintY = top + PANEL_HEIGHT - 34 - hint.size() * 10;
        for (FormattedCharSequence line : hint) {
            gfx.drawString(this.font, line, left + 10, hintY, 0x9A9A9A);
            hintY += 10;
        }
        if (this.feedback != null) {
            gfx.drawCenteredString(this.font, this.feedback, left + PANEL_WIDTH / 2, top + 20, 0xFFFFFF);
        }
        super.render(gfx, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) gfx.renderTooltip(this.font, hovered.copyWithCount(1), mouseX, mouseY);
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
