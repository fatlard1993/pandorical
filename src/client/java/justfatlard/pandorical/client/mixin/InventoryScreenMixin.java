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
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void pandorical$drawExtraSlotBackgrounds(GuiGraphicsExtractor graphics,
            int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        for (PlayerInventoryRegistrationsS2C.SlotGroup group : ClientInventorySlotRegistry.getGroups()) {
            for (PlayerInventoryRegistrationsS2C.SlotPosition pos : group.slots()) {
                int ax = this.leftPos + pos.screenX() - 1;
                int ay = this.topPos + pos.screenY() - 1;

                int bx = ax + 18;
                int by = ay + 18;
                graphics.fill(ax,      ay,      bx,      ay + 1,  SLOT_BORDER_DARK);
                graphics.fill(ax,      ay,      ax + 1,  by,      SLOT_BORDER_DARK);
                graphics.fill(ax,      by - 1,  bx,      by,      SLOT_BORDER_LIGHT);
                graphics.fill(bx - 1,  ay,      bx,      by,      SLOT_BORDER_LIGHT);
                graphics.fill(ax + 1,  ay + 1,  bx - 1,  by - 1,  SLOT_INNER);

                String sprite = pos.backgroundSprite();
                if (sprite != null && !sprite.isEmpty()) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(sprite), ax, ay, 18, 18);
                }
            }
        }
    }

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

            // A face is either a sprite id or a character to draw. Only the former can carry a
            // colon, so that is the whole test - and sprite art is what these should be wearing:
            // a font arrow is a one-pixel hairline against vanilla's chunky widgets.
            String face = button.glyph();
            if (face.indexOf(':') >= 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(face),
                    bx + 1, by + 1, size - 2, size - 2);
                continue;
            }
            int gx = bx + (size - font.width(face)) / 2;
            int gy = by + (size - font.lineHeight) / 2 + 1;
            graphics.text(font, face, gx, gy, BUTTON_TEXT, false);
        }
    }
}
