package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity.Phase;
import io.github.chakyl.cozycafe.data.CafeMenuEntry;
import io.github.chakyl.cozycafe.network.ClientBoundOrderStatusPacket;
import io.github.chakyl.cozycafe.network.EvilPacketsIHateThem;
import io.github.chakyl.cozycafe.network.ServerBoundCancelOrderPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** "Мой заказ": what has been served, what is still coming, the countdown and the cancel button. */
@Environment(EnvType.CLIENT)
public class OrderStatusScreen extends Screen {
    private static final int PANEL_WIDTH = 236;
    private static final int ROW_HEIGHT = 16;
    private static final int MAX_ROWS = 6;

    private final ClientBoundOrderStatusPacket status;
    /** Ticks left, counted down on the client from what the server sent. */
    private int ticksLeft;
    private int panelHeight;
    private int left;
    private int top;
    private boolean confirmCancel;
    private Button cancelButton;

    public OrderStatusScreen(ClientBoundOrderStatusPacket status) {
        super(Component.translatable("gui.cozyrestaurant.status.title"));
        this.status = status;
        this.ticksLeft = status.ticksLeft;
    }

    private boolean canCancel() {
        return this.status.phase == Phase.WAITING && !this.status.pending.isEmpty();
    }

    private int rows(List<CafeMenuEntry> lines) {
        return Math.max(1, Math.min(MAX_ROWS, lines.size()));
    }

    @Override
    protected void init() {
        this.panelHeight = 92 + (this.rows(this.status.served) + this.rows(this.status.pending)) * ROW_HEIGHT + 32;
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.cancelButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.status.cancel"), b -> this.cancel())
                .bounds(this.left + 10, this.top + this.panelHeight - 28, 130, 20).build());
        this.cancelButton.visible = this.canCancel();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.status.close"), b -> this.onClose())
                .bounds(this.left + PANEL_WIDTH - 90, this.top + this.panelHeight - 28, 80, 20).build());
    }

    private void cancel() {
        // Two clicks: the first asks to confirm.
        if (!this.confirmCancel) {
            this.confirmCancel = true;
            this.cancelButton.setMessage(Component.translatable("gui.cozyrestaurant.status.cancel_confirm"));
            return;
        }
        EvilPacketsIHateThem.sendToServer(new ServerBoundCancelOrderPacket(this.status.tablePos));
        this.onClose();
    }

    @Override
    public void tick() {
        if (this.ticksLeft > 0) this.ticksLeft--;
    }

    private static String clock(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int hours = seconds / 3600;
        int minutes = seconds / 60 % 60;
        int secs = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, secs) : String.format("%d:%02d", minutes, secs);
    }

    private int drawLines(GuiGraphics gfx, Component header, List<CafeMenuEntry> lines, int y, int headerColor, int emptyKeyColor, String emptyKey) {
        gfx.drawString(this.font, header, this.left + 12, y, headerColor);
        y += 12;
        if (lines.isEmpty()) {
            gfx.drawString(this.font, Component.translatable(emptyKey), this.left + 20, y + 4, emptyKeyColor);
            return y + ROW_HEIGHT;
        }
        for (int i = 0; i < Math.min(MAX_ROWS, lines.size()); i++) {
            CafeMenuEntry line = lines.get(i);
            if (i == MAX_ROWS - 1 && lines.size() > MAX_ROWS) {
                gfx.drawString(this.font, Component.translatable("gui.cozyrestaurant.status.more", lines.size() - i), this.left + 20, y + 4, 0x9A9A9A);
                return y + ROW_HEIGHT;
            }
            gfx.renderFakeItem(line.item(), this.left + 16, y);
            String name = this.font.plainSubstrByWidth(line.item().getHoverName().getString(), 130);
            gfx.drawString(this.font, name, this.left + 36, y + 4, 0xFFFFFF);
            int priceX = this.left + PANEL_WIDTH - 14 - CafeUi.priceWidth(this.font, line.price());
            CafeUi.drawPrice(gfx, this.font, line.price(), this.status.currency, priceX, y + 4, 0xFFE08A, true);
            y += ROW_HEIGHT;
        }
        return y;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        int left = this.left;
        int top = this.top;
        gfx.fill(left, top, left + PANEL_WIDTH, top + this.panelHeight, 0xE0202020);
        gfx.renderOutline(left, top, PANEL_WIDTH, this.panelHeight, 0xFFC9A227);
        gfx.drawCenteredString(this.font, this.title, left + PANEL_WIDTH / 2, top + 8, 0xFFFFFF);
        gfx.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.status.cafeName, PANEL_WIDTH - 20), left + PANEL_WIDTH / 2, top + 19, 0x9A9A9A);

        int y = top + 34;
        y = this.drawLines(gfx, Component.translatable("gui.cozyrestaurant.status.served"), this.status.served, y, 0x7CFC5A, 0x9A9A9A, "gui.cozyrestaurant.status.none_served");
        y += 4;
        y = this.drawLines(gfx, Component.translatable("gui.cozyrestaurant.status.pending"), this.status.pending, y, 0xFFC04D, 0x9A9A9A, "gui.cozyrestaurant.status.none_pending");

        // What happens now, and the countdown.
        y += 6;
        String clock = clock(this.ticksLeft);
        Component line1;
        Component line2 = null;
        switch (this.status.phase) {
            case WAITING -> {
                line1 = Component.translatable("gui.cozyrestaurant.status.waiting", clock);
                if (this.status.foodOnTable) line2 = Component.translatable("gui.cozyrestaurant.status.take_part");
            }
            case EATING -> {
                line1 = Component.translatable("gui.cozyrestaurant.status.ready", clock);
                line2 = Component.translatable("gui.cozyrestaurant.status.ready_hint");
            }
            case AFTER_MEAL -> {
                line1 = Component.translatable("gui.cozyrestaurant.status.bill_wait", clock);
                line2 = Component.translatable("gui.cozyrestaurant.status.bill_wait_hint");
            }
            case BILL -> line1 = Component.translatable("gui.cozyrestaurant.status.bill");
            default -> line1 = Component.empty();
        }
        gfx.drawString(this.font, line1, left + 12, y, 0xFFFFFF);
        if (line2 != null) gfx.drawString(this.font, this.font.plainSubstrByWidth(line2.getString(), PANEL_WIDTH - 24), left + 12, y + 11, 0xAAAAAA);

        super.render(gfx, mouseX, mouseY, partialTick);
        if (this.cancelButton.visible && this.cancelButton.isHovered()) {
            gfx.renderTooltip(this.font, Component.translatable(this.status.served.isEmpty() ? "gui.cozyrestaurant.status.cancel_tooltip" : "gui.cozyrestaurant.status.cancel_rest_tooltip"), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
