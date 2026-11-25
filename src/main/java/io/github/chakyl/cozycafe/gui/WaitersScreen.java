package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.network.EvilPacketsIHateThem;
import io.github.chakyl.cozycafe.network.ServerBoundAddWaiterPacket;
import io.github.chakyl.cozycafe.network.ServerBoundRemoveWaiterPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * "Официанты" tab of the Cafe Manager: add waiters by nickname, remove them from the list.
 * Reads the list straight from the client copy of the block entity, so it refreshes as soon as the server updates it.
 */
@Environment(EnvType.CLIENT)
public class WaitersScreen extends Screen implements io.github.chakyl.cozycafe.client.FeedbackScreen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 222;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 7;

    private final Screen parent;
    private final CafeManagerBlockEntity cafe;
    private EditBox nameField;
    private final List<Button> removeButtons = new ArrayList<>();
    private int scroll = 0;
    private int lastWaiterCount = -1;
    private Component feedback;
    private int feedbackTicks = 0;
    private int left;
    private int top;

    public WaitersScreen(Screen parent, CafeManagerBlockEntity cafe) {
        super(Component.translatable("gui.cozyrestaurant.waiters.title"));
        this.parent = parent;
        this.cafe = cafe;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = (this.height - PANEL_HEIGHT) / 2;
        String previous = this.nameField == null ? "" : this.nameField.getValue();
        this.nameField = new EditBox(this.font, left + 10, top + 28, 130, 18, Component.translatable("gui.cozyrestaurant.waiters.nickname"));
        this.nameField.setMaxLength(16);
        this.nameField.setValue(previous);
        this.nameField.setHint(Component.translatable("gui.cozyrestaurant.waiters.nickname"));
        this.addRenderableWidget(this.nameField);
        this.setInitialFocus(this.nameField);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.waiters.add"), b -> this.submit())
                .bounds(left + 144, top + 27, 66, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.waiters.back"), b -> this.onClose())
                .bounds(left + PANEL_WIDTH / 2 - 40, top + PANEL_HEIGHT - 28, 80, 20).build());
        this.rebuildRemoveButtons();
    }

    private void submit() {
        String name = this.nameField.getValue().trim();
        if (name.isEmpty()) return;
        EvilPacketsIHateThem.sendToServer(new ServerBoundAddWaiterPacket(this.cafe.getBlockPos(), name));
        this.nameField.setValue("");
    }

    private void rebuildRemoveButtons() {
        this.removeButtons.forEach(this::removeWidget);
        this.removeButtons.clear();
        List<CafeManagerBlockEntity.Waiter> waiters = this.cafe.getWaiters();
        this.lastWaiterCount = waiters.size();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, waiters.size() - VISIBLE_ROWS));
        for (int i = 0; i < VISIBLE_ROWS && i + this.scroll < waiters.size(); i++) {
            CafeManagerBlockEntity.Waiter waiter = waiters.get(i + this.scroll);
            Button remove = Button.builder(Component.literal("✕"), b -> EvilPacketsIHateThem.sendToServer(new ServerBoundRemoveWaiterPacket(this.cafe.getBlockPos(), waiter.id())))
                    .bounds(left + PANEL_WIDTH - 32, rowY(i) - 1, 20, 18)
                    .tooltip(Tooltip.create(Component.translatable("gui.cozyrestaurant.waiters.remove")))
                    .build();
            this.removeButtons.add(remove);
            this.addRenderableWidget(remove);
        }
    }

    private int rowY(int row) {
        return this.top + 60 + row * ROW_HEIGHT;
    }

    public void showFeedback(Component message) {
        this.feedback = message;
        this.feedbackTicks = 100;
    }

    @Override
    public void tick() {
        this.nameField.tick();
        if (this.cafe.isRemoved() || this.minecraft.player == null
                || this.minecraft.player.distanceToSqr(this.cafe.getBlockPos().getCenter()) > 100) {
            this.onClose();
            return;
        }
        if (this.cafe.getWaiters().size() != this.lastWaiterCount) {
            this.rebuildRemoveButtons();
        }
        if (this.feedbackTicks > 0 && --this.feedbackTicks == 0) {
            this.feedback = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameField.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            this.submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, this.cafe.getWaiters().size() - VISIBLE_ROWS);
        int newScroll = Mth.clamp(this.scroll - (int) Math.signum(delta), 0, max);
        if (newScroll != this.scroll) {
            this.scroll = newScroll;
            this.rebuildRemoveButtons();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        gfx.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0202020);
        gfx.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF8A3FC2);
        gfx.drawCenteredString(this.font, this.title, left + PANEL_WIDTH / 2, top + 10, 0xFFFFFF);

        List<CafeManagerBlockEntity.Waiter> waiters = this.cafe.getWaiters();
        if (waiters.isEmpty()) {
            gfx.drawCenteredString(this.font, Component.translatable("gui.cozyrestaurant.waiters.empty"), left + PANEL_WIDTH / 2, rowY(0) + 4, 0x9A9A9A);
        }
        for (int i = 0; i < VISIBLE_ROWS && i + this.scroll < waiters.size(); i++) {
            int y = rowY(i);
            gfx.fill(left + 10, y - 2, left + PANEL_WIDTH - 10, y + ROW_HEIGHT - 4, (i % 2 == 0) ? 0x40FFFFFF : 0x20FFFFFF);
            gfx.drawString(this.font, (i + this.scroll + 1) + ". " + waiters.get(i + this.scroll).name(), left + 14, y + 3, 0xC9A227);
        }
        if (waiters.size() > VISIBLE_ROWS) {
            gfx.drawCenteredString(this.font, (this.scroll + 1) + "–" + Math.min(waiters.size(), this.scroll + VISIBLE_ROWS) + " / " + waiters.size(), left + PANEL_WIDTH / 2, rowY(VISIBLE_ROWS) - 2, 0x9A9A9A);
        }

        List<net.minecraft.util.FormattedCharSequence> hint = this.font.split(Component.translatable("gui.cozyrestaurant.waiters.hint"), PANEL_WIDTH - 20);
        int hintY = top + PANEL_HEIGHT - 34 - hint.size() * 10;
        for (net.minecraft.util.FormattedCharSequence line : hint) {
            gfx.drawString(this.font, line, left + 10, hintY, 0x9A9A9A);
            hintY += 10;
        }
        if (this.feedback != null) {
            gfx.drawCenteredString(this.font, this.feedback, left + PANEL_WIDTH / 2, top + 49, 0xFFFFFF);
        }
        super.render(gfx, mouseX, mouseY, partialTick);
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
