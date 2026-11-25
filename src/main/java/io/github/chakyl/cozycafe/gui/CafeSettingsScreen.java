package io.github.chakyl.cozycafe.gui;

import io.github.chakyl.cozycafe.CozyCafe;
import io.github.chakyl.cozycafe.blockentities.CafeManagerBlockEntity;
import io.github.chakyl.cozycafe.client.FeedbackScreen;
import io.github.chakyl.cozycafe.network.EvilPacketsIHateThem;
import io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket;
import io.github.chakyl.cozycafe.network.ServerBoundCafeActionPacket.Action;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** "Настройки кафе": payment currency (put any item into the slot) and who the guests are. */
@Environment(EnvType.CLIENT)
public class CafeSettingsScreen extends AbstractContainerScreen<CafeSettingsMenu> implements FeedbackScreen {
    private static final ResourceLocation GUI_LOCATION = new ResourceLocation(CozyCafe.MODID, "textures/gui/menu_selector.png");
    private static final int PANEL_COLOR = 0xFFBB9A81;
    private static final int INK = 0x3A2A1E;
    private static final int LABEL = 0x7A5C44;
    /** A quiet ink blue: clickable, like a link, without looking like a warning. */
    private static final int ACCENT = 0x2E5E8A;
    private static final int RULE_LEFT = 16;
    /** Text stays inside the ruled table, whose edges are at x = 16 and x = 160. */
    private static final int RULE_RIGHT = 158;
    private static final int PAPER = 0xFFCCB6A0;
    private static final int RULE = 0xFFB39279;
    /** Tops of the ruled rows of the sheet (the texture's lines at 23, 37, 52, 67, 82). */
    private static final int ROW_TITLE = 27;
    private static final int ROW_CURRENCY = 41;
    private static final int ROW_GUESTS = 56;
    private static final int ROW_GUESTS_HINT = 71;
    private Component feedback;
    private int feedbackTicks;

    public CafeSettingsScreen(CafeSettingsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 225;
    }

    private CafeManagerBlockEntity cafe() {
        return this.menu.blockEntity;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cozyrestaurant.back"),
                        button -> EvilPacketsIHateThem.sendToServer(new ServerBoundCafeActionPacket(this.cafe().getBlockPos(), Action.OPEN_MANAGER)))
                .bounds(this.leftPos + this.imageWidth + 2, this.topPos + 6, 76, 20).build());
    }

    @Override
    public void showFeedback(Component message) {
        this.feedback = message;
        this.feedbackTicks = 100;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.feedbackTicks > 0 && --this.feedbackTicks == 0) this.feedback = null;
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        gfx.blit(GUI_LOCATION, left, top, 0, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        gfx.fill(left + 26, top + 91, left + 44, top + 106, PANEL_COLOR);
        // No list here, so no scroll track.
        // The track's outer line at x = 160 stays: it is the right edge of the ruled table.
        gfx.fill(left + 155, top + 23, left + 160, top + 82, PAPER);
        for (int ruleY : new int[]{37, 52, 67}) gfx.fill(left + 155, top + ruleY, left + 160, top + ruleY + 1, RULE);
        // Every line sits inside one ruled row of the sheet, under the clip.
        Component title = Component.translatable("container.cozyrestaurant.cafe_settings");
        gfx.drawString(this.font, title, left + (this.imageWidth - this.font.width(title)) / 2, top + ROW_TITLE, INK, false);

        Component currencyLabel = Component.translatable("gui.cozyrestaurant.settings.currency");
        gfx.drawString(this.font, currencyLabel, left + RULE_LEFT + 2, top + ROW_CURRENCY, LABEL, false);
        int valueX = left + RULE_LEFT + 4 + this.font.width(currencyLabel);
        ItemStack currency = this.cafe().getCurrency();
        if (currency.isEmpty()) {
            gfx.drawString(this.font, Component.translatable("gui.cozyrestaurant.settings.no_currency"), valueX, top + ROW_CURRENCY, 0xA02020, false);
        } else {
            CafeUi.drawIcon(gfx, currency, valueX, top + ROW_CURRENCY - 2, 11);
            String name = this.font.plainSubstrByWidth(currency.getHoverName().getString(), left + RULE_RIGHT - (valueX + 13));
            gfx.drawString(this.font, name, valueX + 13, top + ROW_CURRENCY, INK, false);
        }

        // Guests: the whole row is a switch.
        boolean hover = this.overGuestsRow(mouseX, mouseY);
        if (hover) gfx.fill(left + RULE_LEFT + 1, top + ROW_GUESTS - 3, left + 160, top + ROW_GUESTS + 10, 0x30FFFFFF);
        Component guestsLabel = Component.translatable("gui.cozyrestaurant.settings.guests");
        gfx.drawString(this.font, guestsLabel, left + RULE_LEFT + 2, top + ROW_GUESTS, LABEL, false);
        Component value = Component.literal("⇄ ").append(Component.translatable(this.cafe().hasNpcCustomers() ? "gui.cozyrestaurant.settings.guests_npc" : "gui.cozyrestaurant.settings.guests_players"));
        if (hover) value = value.copy().withStyle(net.minecraft.ChatFormatting.UNDERLINE);
        gfx.drawString(this.font, value, left + RULE_LEFT + 4 + this.font.width(guestsLabel), top + ROW_GUESTS, ACCENT, false);
        Component hint = Component.translatable("gui.cozyrestaurant.settings.guests_click");
        gfx.drawString(this.font, this.font.plainSubstrByWidth(hint.getString(), RULE_RIGHT - RULE_LEFT - 4), left + RULE_LEFT + 2, top + ROW_GUESTS_HINT, 0x9C8A70, false);

        gfx.drawString(this.font, Component.translatable("gui.cozyrestaurant.settings.currency_slot"), left + 46, top + 95, 0xFFFFFF, true);
        Component status = this.feedback != null ? this.feedback : Component.translatable("gui.cozyrestaurant.settings.currency_hint");
        gfx.drawString(this.font, this.font.plainSubstrByWidth(status.getString(), 116), left + 44, top + 117, 0xFFFFFF, true);
    }

    private boolean overGuestsRow(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + RULE_LEFT && mouseX < this.leftPos + RULE_RIGHT
                && mouseY >= this.topPos + ROW_GUESTS - 3 && mouseY < this.topPos + ROW_GUESTS + 10;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.overGuestsRow(mouseX, mouseY)) {
            EvilPacketsIHateThem.sendToServer(new ServerBoundCafeActionPacket(this.cafe().getBlockPos(), Action.SET_NPC_CUSTOMERS, this.cafe().hasNpcCustomers() ? 0 : 1, 0));
            if (this.minecraft != null && this.minecraft.player != null) {
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
        if (this.overGuestsRow(mouseX, mouseY)) {
            gfx.renderTooltip(this.font, Component.translatable("gui.cozyrestaurant.settings.guests_tooltip"), mouseX, mouseY);
        }
    }
}
