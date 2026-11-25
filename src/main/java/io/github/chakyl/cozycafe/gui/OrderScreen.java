package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.blockentities.CafeMenuBlockEntity;
import io.github.chakyl.cozycafe.data.CafeMenuEntry;
import io.github.chakyl.cozycafe.network.EvilPacketsIHateThem;
import io.github.chakyl.cozycafe.network.ServerBoundPlaceOrderPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The guest's order: tick up to six menu positions, set how many minutes you are ready to wait, order.
 */
@Environment(EnvType.CLIENT)
public class OrderScreen extends Screen {
    private static final int PANEL_WIDTH = 236;
    private static final int PANEL_HEIGHT = 236;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 6;
    private static final int LIST_TOP = 32;
    private static final int DEFAULT_MINUTES = 30;

    private final BlockPos tablePos;
    private final String cafeName;
    private final ItemStack currency;
    private final List<CafeMenuEntry> menu;
    /** Indices into {@link #menu}, in the order they were ticked. */
    private final Set<Integer> chosen = new LinkedHashSet<>();
    private EditBox minutesField;
    private Button orderButton;
    private int scroll;
    private Component warning;
    private int warningTicks;
    private int left;
    private int top;

    public OrderScreen(BlockPos tablePos, String cafeName, ItemStack currency, List<CafeMenuEntry> menu) {
        super(Component.translatable("gui.cozyrestaurant.order.title"));
        this.tablePos = tablePos;
        this.cafeName = cafeName;
        this.currency = currency;
        this.menu = menu;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = (this.height - PANEL_HEIGHT) / 2;
        String previous = this.minutesField == null ? String.valueOf(DEFAULT_MINUTES) : this.minutesField.getValue();
        this.minutesField = new EditBox(this.font, left + PANEL_WIDTH - 52, top + PANEL_HEIGHT - 56, 40, 16, Component.translatable("gui.cozyrestaurant.order.minutes"));
        this.minutesField.setMaxLength(3);
        this.minutesField.setFilter(text -> text.chars().allMatch(Character::isDigit));
        this.minutesField.setValue(previous);
        this.addRenderableWidget(this.minutesField);
        this.orderButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.order.place"), b -> this.submit())
                .bounds(left + 10, top + PANEL_HEIGHT - 30, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.order.cancel"), b -> this.onClose())
                .bounds(left + PANEL_WIDTH - 100, top + PANEL_HEIGHT - 30, 90, 20).build());
    }

    private int minutes() {
        try {
            return Integer.parseInt(this.minutesField.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean minutesValid() {
        int minutes = this.minutes();
        return minutes >= 1 && minutes <= CafeMenuBlockEntity.MAX_ORDER_MINUTES;
    }

    private int total() {
        return this.chosen.stream().mapToInt(i -> this.menu.get(i).price()).sum();
    }

    private void submit() {
        if (this.chosen.isEmpty() || !this.minutesValid()) return;
        List<ItemStack> items = new ArrayList<>();
        for (int index : this.chosen) items.add(this.menu.get(index).item());
        EvilPacketsIHateThem.sendToServer(new ServerBoundPlaceOrderPacket(this.tablePos, items, this.minutes()));
        this.onClose();
    }

    @Override
    public void tick() {
        this.minutesField.tick();
        this.orderButton.active = !this.chosen.isEmpty() && this.minutesValid();
        if (this.warningTicks > 0 && --this.warningTicks == 0) this.warning = null;
    }

    private int rowY(int row) {
        return this.top + LIST_TOP + row * ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int row = 0; row < VISIBLE_ROWS && row + this.scroll < this.menu.size(); row++) {
            int y = this.rowY(row);
            if (mouseX >= left + 10 && mouseX < left + PANEL_WIDTH - 10 && mouseY >= y && mouseY < y + ROW_HEIGHT - 1) {
                this.toggle(row + this.scroll);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int lastToggled = -1;
    private long lastToggleTime;

    private void toggle(int index) {
        // An accidental double click must not tick and untick the same position at once.
        long now = net.minecraft.Util.getMillis();
        if (index == this.lastToggled && now - this.lastToggleTime < 250) return;
        this.lastToggled = index;
        this.lastToggleTime = now;
        if (!this.chosen.remove(index)) {
            if (this.chosen.size() >= CafeManagerBlockEntity.MAX_ORDER_SIZE) {
                this.warning = Component.translatable("gui.cozyrestaurant.order.max", CafeManagerBlockEntity.MAX_ORDER_SIZE);
                this.warningTicks = 60;
                return;
            }
            this.chosen.add(index);
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4F, 1.0F);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(delta), 0, Math.max(0, this.menu.size() - VISIBLE_ROWS));
        return true;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        gfx.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0202020);
        gfx.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFC9A227);
        gfx.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.cafeName, PANEL_WIDTH - 20), left + PANEL_WIDTH / 2, top + 8, 0xFFFFFF);
        gfx.drawCenteredString(this.font, Component.translatable("gui.cozyrestaurant.order.pick", CafeManagerBlockEntity.MAX_ORDER_SIZE), left + PANEL_WIDTH / 2, top + 19, 0x9A9A9A);

        ItemStack hovered = ItemStack.EMPTY;
        for (int row = 0; row < VISIBLE_ROWS && row + this.scroll < this.menu.size(); row++) {
            int index = row + this.scroll;
            CafeMenuEntry entry = this.menu.get(index);
            int y = this.rowY(row);
            boolean ticked = this.chosen.contains(index);
            boolean hover = mouseX >= left + 10 && mouseX < left + PANEL_WIDTH - 10 && mouseY >= y && mouseY < y + ROW_HEIGHT - 1;
            gfx.fill(left + 10, y, left + PANEL_WIDTH - 10, y + ROW_HEIGHT - 1, ticked ? 0x6040A040 : hover ? 0x40FFFFFF : (row % 2 == 0 ? 0x30FFFFFF : 0x18FFFFFF));
            // Checkbox
            gfx.renderOutline(left + 14, y + 4, 9, 9, 0xFFDDDDDD);
            if (ticked) gfx.drawString(this.font, "✔", left + 15, y + 5, 0x7CFC5A, false);
            gfx.renderFakeItem(entry.item(), left + 27, y);
            String name = this.font.plainSubstrByWidth(entry.item().getHoverName().getString(), 120);
            gfx.drawString(this.font, name, left + 46, y + 5, 0xFFFFFF);
            int priceX = left + PANEL_WIDTH - 14 - CafeUi.priceWidth(this.font, entry.price());
            CafeUi.drawPrice(gfx, this.font, entry.price(), this.currency, priceX, y + 5, 0xFFE08A, true);
            if (hover && mouseX >= left + 27 && mouseX < left + 43) hovered = entry.item();
        }
        if (this.menu.size() > VISIBLE_ROWS) {
            gfx.drawCenteredString(this.font, (this.scroll + 1) + "–" + Math.min(this.menu.size(), this.scroll + VISIBLE_ROWS) + " / " + this.menu.size(),
                    left + PANEL_WIDTH / 2, this.rowY(VISIBLE_ROWS) + 1, 0x9A9A9A);
        }

        // Total and waiting time
        int totalY = top + PANEL_HEIGHT - 74;
        Component chosenText = Component.translatable("gui.cozyrestaurant.order.chosen", this.chosen.size(), CafeManagerBlockEntity.MAX_ORDER_SIZE);
        gfx.drawString(this.font, chosenText, left + 12, totalY, 0xCCCCCC);
        Component totalLabel = Component.translatable("gui.cozyrestaurant.order.total");
        int totalX = left + PANEL_WIDTH - 14 - CafeUi.priceWidth(this.font, this.total()) - this.font.width(totalLabel) - 4;
        gfx.drawString(this.font, totalLabel, totalX, totalY, 0xFFFFFF);
        CafeUi.drawPrice(gfx, this.font, this.total(), this.currency, left + PANEL_WIDTH - 14 - CafeUi.priceWidth(this.font, this.total()), totalY, 0xFFE08A, true);

        Component minutesLabel = Component.translatable("gui.cozyrestaurant.order.minutes");
        gfx.drawString(this.font, minutesLabel, left + 12, top + PANEL_HEIGHT - 52, this.minutesValid() ? 0xFFFFFF : 0xFF5555);
        if (!this.minutesValid()) {
            gfx.drawString(this.font, Component.translatable("gui.cozyrestaurant.order.minutes_range", CafeMenuBlockEntity.MAX_ORDER_MINUTES).withStyle(ChatFormatting.RED),
                    left + 12, top + PANEL_HEIGHT - 42, 0xFF5555);
        } else if (this.warning != null) {
            gfx.drawString(this.font, this.warning, left + 12, top + PANEL_HEIGHT - 42, 0xFF5555);
        }

        super.render(gfx, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) gfx.renderTooltip(this.font, hovered, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
