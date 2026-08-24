package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.inventory.ClientInventorySlotRegistry;
import justfatlard.pandorical.protocol.PlayerInventoryRegistrationsS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws slot backgrounds for Pandorical's extra inventory slots.
 *
 * Vanilla only draws slot backgrounds from its background texture, so dynamically
 * added slots (map/compass at custom positions) are functional but invisible.
 * This mixin draws beveled backgrounds for every slot registered in
 * {@link ClientInventorySlotRegistry}, matching the colors used by
 * {@link justfatlard.pandorical.client.component.ItemSlotComponent}.
 *
 * Injection is at HEAD so backgrounds are drawn before items, ensuring items
 * render on top of (not underneath) the backgrounds.
 *
 * <p>Extends {@link AbstractContainerScreen} (a real superclass of the target)
 * purely to reach the protected {@code leftPos}/{@code topPos} fields: the
 * backgrounds MUST anchor to the screen's live origin, exactly like the Slot
 * objects themselves (menu-space x/y drawn and hit-tested against the live
 * origin by vanilla). Deriving the origin from centered screen math instead
 * broke when the recipe book pane shifted the panel right: the real slots and
 * their click targets moved with it, while the drawn boxes stayed centered.
 */
@Environment(EnvType.CLIENT)
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {

    private InventoryScreenMixin() {
        super(null, null, null);
    }

    /** Button faces, matching the slot colours so the two read as one panel. */
    private static final int BUTTON_FACE  = 0xFF8B8B8B;
    private static final int BUTTON_HOVER = 0xFFA8A8A8;
    private static final int BUTTON_TEXT  = 0xFF373737;

    // Colors matching ItemSlotComponent.render()
    private static final int SLOT_BORDER_DARK  = 0xFF373737;
    private static final int SLOT_BORDER_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_INNER        = 0xFF8B8B8B;

    /**
     * Before vanilla renders the inventory screen, draw backgrounds for every extra slot
     * Pandorical has registered. Injecting at HEAD ensures items render on top of backgrounds.
     *
     * Slot positions in {@link ClientInventorySlotRegistry} are relative to the screen
     * background origin ({@code leftPos}, {@code topPos}), so we add those offsets before
     * calling {@code graphics.fill()} or {@code graphics.blitSprite()}.
     */
    /**
     * Draw the buttons this server asked for, and answer clicks on them.
     *
     * <p>Anchored to {@code leftPos}/{@code topPos} for the same reason the slot backgrounds
     * are: the recipe book pane shifts the panel sideways, and anything positioned from screen
     * centre parts company with the panel the moment it opens.
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void pandorical$drawInventoryButtons(GuiGraphicsExtractor graphics,
            int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        var buttons = justfatlard.pandorical.client.inventory.ClientInventoryButtons.all();
        if (buttons.isEmpty()) return;

        var font = net.minecraft.client.Minecraft.getInstance().font;
        for (var button : buttons) {
            int bx = this.leftPos + button.screenX();
            int by = this.topPos + button.screenY();
            int size = button.size();
            boolean over = mouseX >= bx && mouseX < bx + size && mouseY >= by && mouseY < by + size;

            graphics.fill(bx, by, bx + size, by + size, SLOT_BORDER_DARK);
            graphics.fill(bx + 1, by + 1, bx + size - 1, by + size - 1,
                over ? BUTTON_HOVER : BUTTON_FACE);

            String glyph = button.glyph();
            int gx = bx + (size - font.width(glyph)) / 2;
            int gy = by + (size - font.lineHeight) / 2 + 1;
            graphics.text(font, glyph, gx, gy, BUTTON_TEXT, false);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void pandorical$clickInventoryButton(net.minecraft.client.input.MouseButtonEvent click,
            boolean handled, CallbackInfoReturnable<Boolean> cir) {
        if (handled) return;

        for (var button : justfatlard.pandorical.client.inventory.ClientInventoryButtons.all()) {
            int bx = this.leftPos + button.screenX();
            int by = this.topPos + button.screenY();
            int size = button.size();
            if (click.x() < bx || click.x() >= bx + size) continue;
            if (click.y() < by || click.y() >= by + size) continue;

            justfatlard.pandorical.client.inventory.ClientInventoryButtons.press(button);
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void pandorical$drawExtraSlotBackgrounds(GuiGraphicsExtractor graphics,
                                                     int mouseX, int mouseY, float delta,
                                                     CallbackInfo ci) {
        int leftPos = this.leftPos;
        int topPos  = this.topPos;

        for (PlayerInventoryRegistrationsS2C.SlotGroup group : ClientInventorySlotRegistry.getGroups()) {
            for (PlayerInventoryRegistrationsS2C.SlotPosition pos : group.slots()) {
                int ax = leftPos + pos.screenX() - 1;
                int ay = topPos  + pos.screenY() - 1;

                // Always draw the beveled slot background box
                int bx = ax + 18;
                int by = ay + 18;
                graphics.fill(ax,      ay,      bx,      ay + 1,  SLOT_BORDER_DARK);
                graphics.fill(ax,      ay,      ax + 1,  by,      SLOT_BORDER_DARK);
                graphics.fill(ax,      by - 1,  bx,      by,      SLOT_BORDER_LIGHT);
                graphics.fill(bx - 1,  ay,      bx,      by,      SLOT_BORDER_LIGHT);
                graphics.fill(ax + 1,  ay + 1,  bx - 1,  by - 1,  SLOT_INNER);

                // If the mod supplied a custom sprite, draw it on top of the background.
                // Items render 16x16 at (ax+1, ay+1) inside the 18x18 slot frame; the ghost
                // sprite must match exactly or its outline overhangs the item by 1px per side.
                String sprite = pos.backgroundSprite();
                if (sprite != null && !sprite.isEmpty()) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(sprite), ax + 1, ay + 1, 16, 16);
                }
            }
        }
    }
}
