package io.github.chakyl.cozycafe.gui;


import com.mojang.blaze3d.vertex.PoseStack;
import io.github.chakyl.cozycafe.CozyCafe;
import io.github.chakyl.cozycafe.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;



@Environment(EnvType.CLIENT)
public class CafeManagerScreen extends AbstractContainerScreen<CafeManagerMenu> implements io.github.chakyl.cozycafe.client.FeedbackScreen {

    private static final ResourceLocation GUI_LOCATION = new ResourceLocation(CozyCafe.MODID, "textures/gui/cafe_manager.png");
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int GRID_COLUMNS = 5;
    private static final int GRID_ROWS = 5;
    private int scrollRow;
    private Component errorMessage = null;
    private int errorDisplayTicks = 0;
    /** How long a refusal shows under the tablet; it slides out at the start and fades away at the end. */
    private static final int ERROR_TICKS = 100;
    private static final int ERROR_SLIDE_TICKS = 5;
    private static final int ERROR_FADE_TICKS = 25;
    private ImageButton toggleOpenButton;
    private EditBox nameField;
    private boolean isNameEditing = false;
    private ItemStack hoveredItemToRender = ItemStack.EMPTY;

    public CafeManagerScreen(CafeManagerMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 176;
        this.imageHeight = 225;
    }

    @Override
    public void showFeedback(Component message) {
        this.errorMessage = message;
        this.errorDisplayTicks = ERROR_TICKS;
    }

    private io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket.Action lastAction;
    private long lastActionTime;

    private void action(io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket.Action action) {
        // A double click would open and at once close the cafe again: ignore the same button within 300 ms.
        long now = net.minecraft.Util.getMillis();
        if (action == this.lastAction && now - this.lastActionTime < 300) return;
        this.lastAction = action;
        this.lastActionTime = now;
        EvilPacketsIHateThem.sendToServer(new io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket(this.menu.blockEntity.getBlockPos(), action));
    }

    @Override
    protected void init() {
        super.init();
        int leftPos = this.leftPos;
        int topPos = this.topPos;
        ImageButton editMenuButton = new ImageButton(leftPos + 20, topPos + 36, 17, 15, 224, 80, 16, GUI_LOCATION, 256, 256, (button) -> {
            this.action(io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket.Action.OPEN_MENU_EDITOR);
        }){
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
                // The usual text, plus a red warning for every reason the editor can't open right now.
                net.minecraft.network.chat.MutableComponent text = Component.translatable("gui.cozyrestaurant.cafe_manager.edit_menu");
                if (!CafeManagerScreen.this.menu.blockEntity.hasCurrency()) {
                    text.append("\n").append(Component.translatable("block.cozyrestaurant.cafe_manager.no_currency").withStyle(ChatFormatting.RED));
                }
                if (CafeManagerScreen.this.menu.getIsCafeOpen()) {
                    text.append("\n").append(Component.translatable("tooltip.cozyrestaurant.cafe_manager.cannot_edit").withStyle(ChatFormatting.RED));
                }
                this.setTooltip(Tooltip.create(text));
            }
        };
        this.addRenderableWidget(editMenuButton);
        // hgate this
        this.toggleOpenButton = new ImageButton(leftPos + 56, topPos + 192, 64, 24, 176, 0, 32, GUI_LOCATION, 256, 256, (button) -> {
            this.action(io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket.Action.TOGGLE_OPEN);
        }) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                boolean open = CafeManagerScreen.this.menu.getIsCafeOpen();
                int yOffset = open ? 192 : 0;
                if (this.isHoveredOrFocused()) yOffset += 32;
                this.setTooltip(Tooltip.create(Component.translatable("gui.cozyrestaurant.cafe_manager." + (open ? "close" : "open"))));
                // The original texture has English "OPEN"/"CLOSE" baked in: draw only the stamp frame and the word in Russian.
                int b = 5; // frame thickness in the texture (the "-" marks inside it are left out to make room for the word)
                guiGraphics.blit(GUI_LOCATION, this.getX(), this.getY(), 176, yOffset, this.width, b, 256, 256);
                guiGraphics.blit(GUI_LOCATION, this.getX(), this.getY() + this.height - b, 176, yOffset + this.height - b, this.width, b, 256, 256);
                guiGraphics.blit(GUI_LOCATION, this.getX(), this.getY(), 176, yOffset, b, this.height, 256, 256);
                guiGraphics.blit(GUI_LOCATION, this.getX() + this.width - b, this.getY(), 176 + this.width - b, yOffset, b, this.height, 256, 256);
                Component label = Component.translatable("gui.cozyrestaurant.cafe_manager.stamp_" + (open ? "close" : "open"));
                int color = open ? 0x8E1B24 : 0x5FA816;
                if (this.isHoveredOrFocused()) color = open ? 0xB0242E : 0x7ACC1C;
                guiGraphics.drawString(CafeManagerScreen.this.font, label, this.getX() + (this.width - CafeManagerScreen.this.font.width(label)) / 2, this.getY() + 8, color, false);
            }
        };
        this.addRenderableWidget(toggleOpenButton);

        this.nameField = new EditBox(this.font, leftPos + 40, topPos + 18, 96, 16, Component.empty());
        this.nameField.setMaxLength(32);
        this.nameField.setEditable(false);
        this.nameField.setVisible(false);
        this.addRenderableWidget(this.nameField);

        ImageButton toggleEditButton = new ImageButton(leftPos + 146, topPos + 36, 15, 15, 176, 80, 16, GUI_LOCATION, 256, 256, (button) -> {
            this.isNameEditing = !this.isNameEditing;
            this.nameField.setEditable(this.isNameEditing);
            this.nameField.setVisible(this.isNameEditing);

            if (!this.isNameEditing) {
                EvilPacketsIHateThem.sendToServer(new ServerBoundRenameCafePacket(this.menu.blockEntity.getBlockPos(), this.nameField.getValue()));
            } else {
                this.nameField.setValue(this.menu.getCafeName());
                // The screen must focus the field too: typed characters go to the screen's focused widget.
                this.setFocused(this.nameField);
                this.nameField.setFocused(true);
            }
        });
        toggleEditButton.setTooltip(Tooltip.create(Component.translatable("gui.cozyrestaurant.cafe_manager.edit_name")));
        this.addRenderableWidget(toggleEditButton);

        ImageButton showAreaButton = new ImageButton(leftPos + this.imageWidth - 25, topPos + this.imageHeight - 29, 15, 15, 192, 80, 16, GUI_LOCATION, 256, 256, (button) -> {
            this.action(io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket.Action.SHOW_AREA);
            this.onClose();
        });
        showAreaButton.setTooltip(Tooltip.create(Component.translatable("gui.cozyrestaurant.cafe_manager.show_area")));
        this.addRenderableWidget(showAreaButton);

        ImageButton clearCafeButton = new ImageButton(leftPos + 13, topPos + this.imageHeight - 29, 15, 15, 208, 80, 16, GUI_LOCATION, 256, 256, (button) -> {
            if (Screen.hasShiftDown()) {
                this.action(io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket.Action.CLEAR);
                this.onClose();
            }
        });
        clearCafeButton.setTooltip(Tooltip.create(Component.translatable("gui.cozyrestaurant.cafe_manager.clear_data")));
        this.addRenderableWidget(clearCafeButton);

        // Tabs next to the panel.
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.cafe_manager.waiters"),
                        (button) -> this.minecraft.setScreen(new WaitersScreen(this, this.menu.blockEntity)))
                .bounds(leftPos + this.imageWidth + 2, topPos + 6, 76, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.cafe_manager.settings"),
                        (button) -> this.action(io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket.Action.OPEN_SETTINGS))
                .bounds(leftPos + this.imageWidth + 2, topPos + 28, 76, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.cafe_manager.revenue"),
                        (button) -> this.minecraft.setScreen(new RevenueScreen(this, this.menu.blockEntity)))
                .bounds(leftPos + this.imageWidth + 2, topPos + 50, 76, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.cafe_manager.debtors"),
                        (button) -> this.minecraft.setScreen(new DebtorsScreen(this, this.menu.blockEntity)))
                .bounds(leftPos + this.imageWidth + 2, topPos + 72, 76, 20).build());
    }


    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        if (this.nameField.isFocused()) {
            if (pKeyCode == 256 || pKeyCode == 257) {
                this.isNameEditing = false;
                this.nameField.setEditable(false);
                this.nameField.setVisible(false);
                this.nameField.setFocused(false);
                EvilPacketsIHateThem.sendToServer(new ServerBoundRenameCafePacket(this.menu.blockEntity.getBlockPos(), this.nameField.getValue()));
                return true;
            }

            this.nameField.keyPressed(pKeyCode, pScanCode, pModifiers);
            return true;
        }

        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // Letters reach the name field even if another widget (the edit button just clicked) holds the screen's focus.
        if (this.nameField.isFocused()) return this.nameField.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.errorDisplayTicks > 0) {
            this.errorDisplayTicks--;
            if (this.errorDisplayTicks == 0) {
                this.errorMessage = null;
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float pPartialTick, int pMouseX, int pMouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        gfx.blit(GUI_LOCATION, left, top, 0, 0.0F, 0.0F, this.imageWidth, this.imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        if (!this.isNameEditing) {
            Component titleText = Component.literal(this.menu.getCafeName());
            PoseStack poseStack = gfx.pose();
            poseStack.pushPose();
            poseStack.translate(left + (this.imageWidth / 2.0f), top + 22, 0);
            gfx.drawString(this.font, titleText, -(this.font.width(titleText) / 2), 0, 0x181425, false);
            poseStack.popPose();
        }
        if (this.menu.blockEntity.hasOwner()) {
            // Under the side tabs, wrapped to the tab width.
            int y = top + 96;
            for (net.minecraft.util.FormattedCharSequence line : this.font.split(Component.translatable("gui.cozyrestaurant.cafe_manager.owner", this.menu.blockEntity.getOwnerName()), 76)) {
                gfx.drawString(this.font, line, left + this.imageWidth + 3, y, 0xFFFFFF, true);
                y += 10;
            }
        }
        // Stars: the average rating rounded to whole stars.
        int stars = this.menu.blockEntity.getStars();
        for (int i = 0; i < stars; i++) {
            gfx.blit(GUI_LOCATION, this.leftPos + 57 + (i * 12), this.topPos + 34, 176, 112, 16, 16);
        }
        // Menu positions, five per row; the wheel scrolls rows when there are more than 25.
        List<io.github.chakyl.cozycafe.data.CafeMenuEntry> entries = this.menu.blockEntity.getMenu();
        int totalRows = (entries.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        this.scrollRow = net.minecraft.util.Mth.clamp(this.scrollRow, 0, Math.max(0, totalRows - GRID_ROWS));
        int slotWidth = 29;
        int slotHeight = 25;
        int startX = this.leftPos + 20;
        int startY = this.topPos + 55;
        for (int index = this.scrollRow * GRID_COLUMNS; index < entries.size(); index++) {
            int visible = index - this.scrollRow * GRID_COLUMNS;
            int row = visible / GRID_COLUMNS;
            if (row >= GRID_ROWS) break;
            int col = visible % GRID_COLUMNS;
            int itemX = startX + (col * slotWidth);
            int itemY = startY + (row * slotHeight) + (row * 2);
            io.github.chakyl.cozycafe.data.CafeMenuEntry entry = entries.get(index);
            gfx.renderFakeItem(entry.item(), itemX + 2, itemY + 4);
            String price = String.valueOf(entry.price());
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 200);
            gfx.drawString(this.font, price, itemX + 19 - this.font.width(price), itemY + 13, 0xFFE08A, true);
            gfx.pose().popPose();
            if (pMouseX >= itemX && pMouseX < itemX + 22 && pMouseY >= itemY && pMouseY < itemY + 25) {
                this.hoveredEntry = entry;
            }
        }
        if (totalRows > GRID_ROWS) {
            String pages = (this.scrollRow * GRID_COLUMNS + 1) + "–" + Math.min(entries.size(), (this.scrollRow + GRID_ROWS) * GRID_COLUMNS) + " / " + entries.size();
            gfx.drawString(this.font, pages, left + (this.imageWidth - this.font.width(pages)) / 2, top + 182, 0x6B4F3A, false);
        }
        this.starsHovered = pMouseX >= this.leftPos + 57 && pMouseX < this.leftPos + 57 + 60 && pMouseY >= this.topPos + 34 && pMouseY < this.topPos + 50;
    }

    private io.github.chakyl.cozycafe.data.CafeMenuEntry hoveredEntry;
    private boolean starsHovered;

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.scrollRow -= (int) Math.signum(delta);
        return true;
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.hoveredEntry = null;
        this.starsHovered = false;
        this.renderBackground(pGuiGraphics);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        this.renderTooltip(pGuiGraphics, pMouseX, pMouseY);
        if (this.errorMessage != null) this.renderErrorToast(pGuiGraphics, pPartialTick);
        if (this.hoveredEntry != null) {
            List<Component> tooltipList = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), this.hoveredEntry.item()));
            ItemStack currency = this.menu.blockEntity.getCurrency();
            tooltipList.add(Component.translatable("gui.cozyrestaurant.cafe_manager.price", this.hoveredEntry.price(),
                    currency.isEmpty() ? Component.literal("?") : currency.getHoverName()).withStyle(ChatFormatting.GOLD));
            pGuiGraphics.renderComponentTooltip(this.font, tooltipList, pMouseX, pMouseY);
        } else if (this.starsHovered) {
            pGuiGraphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("gui.cozyrestaurant.cafe_manager.rating", String.format(java.util.Locale.ROOT, "%.1f", this.menu.blockEntity.getAverageRating())).withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.cozyrestaurant.cafe_manager.rating_count", this.menu.blockEntity.getRatingCount()).withStyle(ChatFormatting.GRAY)), pMouseX, pMouseY);
        }
    }

    /** A note that slides out from under the bottom of the tablet and fades away, covering nothing on it. */
    private void renderErrorToast(GuiGraphics gfx, float partialTick) {
        float age = ERROR_TICKS - this.errorDisplayTicks + partialTick;
        float left = this.errorDisplayTicks - partialTick;
        float alpha = left >= ERROR_FADE_TICKS ? 1.0F : Math.max(0.0F, left / ERROR_FADE_TICKS);
        if (alpha <= 0.03F) return;
        float slide = Math.min(1.0F, age / ERROR_SLIDE_TICKS);
        slide = 1.0F - (1.0F - slide) * (1.0F - slide);
        List<Component> lines = wrapErrorText(this.font, this.errorMessage);
        int width = 0;
        for (Component line : lines) width = Math.max(width, this.font.width(line));
        int boxWidth = width + 12;
        int boxHeight = lines.size() * 10 + 8;
        int x = this.leftPos + (this.imageWidth - boxWidth) / 2;
        int restY = Math.min(this.topPos + this.imageHeight + 3, this.height - boxHeight - 2);
        int y = Math.round(restY - (1.0F - slide) * (boxHeight + 3));
        int a = Math.round(alpha * 255.0F);
        gfx.pose().pushPose();
        // Under the tablet while it slides out (the tablet hides its top), above everything once it is out.
        gfx.pose().translate(0, 0, slide < 1.0F ? -10 : 300);
        gfx.fill(x, y, x + boxWidth, y + boxHeight, (Math.round(alpha * 0xE0) << 24) | 0x2A1010);
        gfx.renderOutline(x, y, boxWidth, boxHeight, (a << 24) | 0xC0503A);
        int textAlpha = Math.max(5, a) << 24;
        for (int i = 0; i < lines.size(); i++) {
            int lineWidth = this.font.width(lines.get(i));
            int color = i == 0 ? 0xFF6B5A : 0xFFFFFF;
            gfx.drawString(this.font, lines.get(i), x + (boxWidth - lineWidth) / 2, y + 5 + i * 10, textAlpha | color, false);
        }
        gfx.pose().popPose();
    }

    private List<Component> wrapErrorText(Font font, Component component) {
        List<Component> errorComps = new ArrayList<>();
        List<FormattedText> lines = font.getSplitter().splitLines(component, 148, Style.EMPTY);

        errorComps.add(Component.translatable("tooltip.cozyrestaurant.cafe_manager.error").withStyle(ChatFormatting.RED));
        for (FormattedText line : lines) {
            if (line instanceof Component comp) {
                errorComps.add(comp);
            } else {
                errorComps.add(Component.literal(line.getString()));
            }
        }

        return errorComps;
    }
}