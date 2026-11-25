package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.CozyCafe;
import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.client.FeedbackScreen;
import io.github.chakyl.cozycafe.data.CafeMenuEntry;
import io.github.chakyl.cozycafe.network.EvilPacketsIHateThem;
import io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket;
import io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket.Action;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The menu editor: positions with their prices. Put any item into the slot to add it, click a position to set
 * its price, ✕ removes it.
 */
@Environment(EnvType.CLIENT)
public class MenuSelectorScreen extends AbstractContainerScreen<MenuSelectorMenu> implements FeedbackScreen {
    private static final ResourceLocation GUI_LOCATION = new ResourceLocation(CozyCafe.MODID, "textures/gui/menu_selector.png");
    private static final int ROWS = 4;
    private static final int ROW_HEIGHT = 15;
    private static final int LIST_X = 18;
    private static final int LIST_Y = 22;
    private static final int REMOVE_X = 140;
    private static final int SCROLL_BAR_X = 155;
    private static final int SCROLL_BAR_HEIGHT = ROW_HEIGHT * ROWS;
    private static final int SCROLLER_HEIGHT = 27;
    private static final int PANEL_COLOR = 0xFFBB9A81;

    private int scrollOff;
    private boolean isDragging;
    private int selected = -1;
    private int lastResultCounter;
    private EditBox priceField;
    private boolean updatingField;
    private Component feedback;
    private int feedbackTicks;
    private final ImageButton[] removeButtons = new ImageButton[ROWS];

    public MenuSelectorScreen(MenuSelectorMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 176;
        this.imageHeight = 225;
    }

    private CafeManagerBlockEntity cafe() {
        return this.menu.blockEntity;
    }

    private List<CafeMenuEntry> entries() {
        return this.cafe().getMenu();
    }

    @Override
    protected void init() {
        super.init();
        this.lastResultCounter = this.menu.getResultCounter();
        this.knownSize = this.entries().size();
        for (int row = 0; row < ROWS; row++) {
            final int r = row;
            ImageButton remove = new ImageButton(this.leftPos + REMOVE_X, this.topPos + LIST_Y + row * ROW_HEIGHT, 15, 15, 176, 32, 16, GUI_LOCATION, 256, 256, button -> {
                int index = this.scrollOff + r;
                if (index < this.entries().size()) {
                    EvilPacketsIHateThem.sendToServer(new ServerBoundCafeActionPacket(this.cafe().getBlockPos(), Action.REMOVE_MENU_ITEM, index, 0));
                    if (this.selected == index) this.select(-1);
                    else if (this.selected > index) this.selected--;
                }
            });
            remove.setTooltip(Tooltip.create(Component.translatable("gui.cozyrestaurant.menu_selector.remove")));
            this.removeButtons[row] = this.addRenderableWidget(remove);
        }

        this.priceField = new EditBox(this.font, this.leftPos + 78, this.topPos + 93, 34, 12, Component.empty());
        this.priceField.setMaxLength(3);
        this.priceField.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        this.priceField.setResponder(this::onPriceTyped);
        this.priceField.setVisible(false);
        this.addRenderableWidget(this.priceField);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.back"),
                        button -> EvilPacketsIHateThem.sendToServer(new ServerBoundCafeActionPacket(this.cafe().getBlockPos(), Action.OPEN_MANAGER)))
                .bounds(this.leftPos + this.imageWidth + 2, this.topPos + 6, 76, 20).build());
    }

    private void select(int index) {
        this.selected = index;
        boolean valid = index >= 0 && index < this.entries().size();
        this.priceField.setVisible(valid);
        if (valid) {
            this.updatingField = true;
            this.priceField.setValue(String.valueOf(this.entries().get(index).price()));
            this.updatingField = false;
            this.priceField.setTooltip(Tooltip.create(Component.translatable("gui.cozyrestaurant.menu_selector.max_price", this.cafe().getMaxPrice())));
            this.priceField.setFocused(true);
            this.setFocused(this.priceField);
        }
    }

    private void onPriceTyped(String value) {
        if (this.updatingField || value.isEmpty() || this.selected < 0 || this.selected >= this.entries().size()) return;
        int price = Mth.clamp(Integer.parseInt(value), CafeMenuEntry.MIN_PRICE, this.cafe().getMaxPrice());
        if (price != this.entries().get(this.selected).price()) {
            EvilPacketsIHateThem.sendToServer(new ServerBoundCafeActionPacket(this.cafe().getBlockPos(), Action.SET_PRICE, this.selected, price));
        }
    }

    @Override
    public void showFeedback(Component message) {
        this.feedback = message;
        this.feedbackTicks = 100;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.priceField.tick();
        if (this.feedbackTicks > 0 && --this.feedbackTicks == 0) this.feedback = null;
        int counter = this.menu.getResultCounter();
        if (counter != this.lastResultCounter) {
            this.lastResultCounter = counter;
            CafeManagerBlockEntity.AddResult result = this.menu.getLastResult();
            if (Minecraft.getInstance().player != null && result != null) {
                Minecraft.getInstance().player.playSound(result == CafeManagerBlockEntity.AddResult.ADDED ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 1.0F);
            }
        }
        // A position was added (new items always go to the end): jump to it so the owner can price it right away.
        int size = this.entries().size();
        if (size > this.knownSize) {
            this.scrollOff = Math.max(0, size - ROWS);
            this.select(size - 1);
        }
        this.knownSize = size;
        if (this.selected >= this.entries().size()) this.select(-1);
    }

    /** Menu size seen on the previous tick; growth means a new position arrived. */
    private int knownSize;

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        List<CafeMenuEntry> entries = this.entries();
        ItemStack currency = this.cafe().getCurrency();
        this.scrollOff = Mth.clamp(this.scrollOff, 0, Math.max(0, entries.size() - ROWS));
        gfx.blit(GUI_LOCATION, left, top, 0, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        // The texture's star marked the old reputation level: cover it, the panel now holds the price editor.
        gfx.fill(left + 26, top + 91, left + 44, top + 106, PANEL_COLOR);

        gfx.drawString(this.font, Component.translatable("gui.cozyrestaurant.menu_selector.name"), left + 16, top + 12, 0xFFFFFF, true);
        String count = entries.size() + "/" + CafeManagerBlockEntity.MAX_MENU_SIZE;
        gfx.drawString(this.font, count, left + 160 - this.font.width(count), top + 12, 0xFFFFFF, true);

        for (int row = 0; row < ROWS; row++) {
            int index = this.scrollOff + row;
            this.removeButtons[row].visible = index < entries.size();
            if (index >= entries.size()) continue;
            CafeMenuEntry entry = entries.get(index);
            int y = top + LIST_Y + row * ROW_HEIGHT;
            int x = left + LIST_X;
            boolean hovered = mouseX >= x && mouseX < left + REMOVE_X && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (index == this.selected) {
                gfx.fill(x - 2, y, left + REMOVE_X - 1, y + ROW_HEIGHT, 0x60FFFFFF);
            } else if (hovered) {
                gfx.fill(x - 2, y, left + REMOVE_X - 1, y + ROW_HEIGHT, 0x30FFFFFF);
            }
            CafeUi.drawIcon(gfx, entry.item(), x, y + 1, 13);
            int priceWidth = CafeUi.priceWidth(this.font, entry.price());
            int priceX = left + REMOVE_X - 3 - priceWidth;
            String name = this.font.plainSubstrByWidth(entry.item().getHoverName().getString(), priceX - (x + 16) - 3);
            gfx.drawString(this.font, name, x + 16, y + 4, 0xFFFFFF, true);
            CafeUi.drawPrice(gfx, this.font, entry.price(), currency, priceX, y + 4, 0xFFE08A, true);
            if (hovered) this.hoveredEntry = entry;
        }
        this.renderScroller(gfx, entries.size());

        // Middle panel: price of the selected position.
        if (this.selected >= 0 && this.selected < entries.size()) {
            gfx.drawString(this.font, Component.translatable("gui.cozyrestaurant.menu_selector.price_label"), left + 46, top + 95, 0xFFFFFF, true);
            CafeUi.drawIcon(gfx, currency, left + 115, top + 94, CafeUi.ICON);
        } else {
            // Two different steps, two different looks: the price row is a quiet hint, adding is the gold action below.
            String hint = this.font.plainSubstrByWidth(Component.translatable("gui.cozyrestaurant.menu_selector.select_position").getString(), 160 - 46);
            gfx.drawString(this.font, hint, left + 46, top + 95, 0x6E5340, false);
        }

        // Result of putting an item into the slot.
        int feedbackX = left + 44;
        int feedbackY = top + 117;
        CafeManagerBlockEntity.AddResult result = this.menu.getLastResult();
        Component status;
        if (this.feedback != null) {
            status = this.feedback;
        } else if (result == null) {
            status = Component.translatable("gui.cozyrestaurant.menu_selector.place_items");
        } else {
            boolean ok = result == CafeManagerBlockEntity.AddResult.ADDED;
            gfx.blit(GUI_LOCATION, feedbackX - 4, feedbackY - 4, ok ? 192 : 176, 32, 16, 16);
            feedbackX += 12;
            status = Component.translatable("gui.cozyrestaurant.menu_selector.result." + result.name().toLowerCase(), CafeManagerBlockEntity.MAX_MENU_SIZE);
        }
        int statusColor = this.feedback == null && result == null ? 0xFFE08A : 0xFFFFFF;
        gfx.drawString(this.font, this.font.plainSubstrByWidth(status.getString(), left + 162 - feedbackX), feedbackX, feedbackY, statusColor, true);
    }

    private CafeMenuEntry hoveredEntry;

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.hoveredEntry = null;
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);
        if (this.hoveredEntry != null) {
            List<Component> tooltip = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), this.hoveredEntry.item()));
            tooltip.add(Component.translatable("gui.cozyrestaurant.menu_selector.click_to_price").withStyle(ChatFormatting.GRAY));
            gfx.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.priceField.isFocused() && this.priceField.isVisible()) {
            if (keyCode == 256) {
                this.onClose();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                this.select(-1);
                return true;
            }
            this.priceField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int size = this.entries().size();
        if (size > ROWS) this.scrollOff = Mth.clamp((int) (this.scrollOff - delta), 0, size - ROWS);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int size = this.entries().size();
        if (this.isDragging && size > ROWS) {
            int top = this.topPos + LIST_Y;
            float f = ((float) mouseY - top - SCROLLER_HEIGHT / 2.0F) / (SCROLL_BAR_HEIGHT - SCROLLER_HEIGHT);
            this.scrollOff = Mth.clamp(Math.round(f * (size - ROWS)), 0, size - ROWS);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.isDragging = false;
        int left = this.leftPos;
        int top = this.topPos;
        if (this.entries().size() > ROWS && mouseX >= left + SCROLL_BAR_X && mouseX < left + SCROLL_BAR_X + 6
                && mouseY >= top + LIST_Y && mouseY < top + LIST_Y + SCROLL_BAR_HEIGHT) {
            this.isDragging = true;
        }
        if (mouseX >= left + LIST_X - 2 && mouseX < left + REMOVE_X - 1 && mouseY >= top + LIST_Y && mouseY < top + LIST_Y + SCROLL_BAR_HEIGHT) {
            int index = this.scrollOff + (int) ((mouseY - top - LIST_Y) / ROW_HEIGHT);
            if (index < this.entries().size()) {
                this.select(index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderScroller(GuiGraphics gfx, int size) {
        int x = this.leftPos + SCROLL_BAR_X;
        int y = this.topPos + LIST_Y;
        int hidden = size - ROWS;
        if (hidden > 0) {
            int offset = (int) ((float) this.scrollOff / hidden * (SCROLL_BAR_HEIGHT - SCROLLER_HEIGHT));
            gfx.blit(GUI_LOCATION, x, y + offset, 0, 176.0F, 0.0F, 6, SCROLLER_HEIGHT, 256, 256);
        } else {
            gfx.blit(GUI_LOCATION, x, y, 0, 182.0F, 0.0F, 6, SCROLLER_HEIGHT, 256, 256);
        }
    }
}
