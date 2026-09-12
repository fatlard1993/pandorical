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
import justfatlard.pandorical.client.inventory.ClientInventoryButtons;
import net.minecraft.client.Minecraft;

/**
 * Draws backgrounds for the slots in {@link ClientInventorySlotRegistry}, which vanilla's
 * background texture does not have.
 *
 * <p>Extends {@link AbstractContainerScreen} only to reach {@code leftPos}/{@code topPos}. Vanilla
 * draws and hit-tests slots against that live origin, which the recipe book pane shifts, so
 * anything positioned from screen centre drifts off its slot when the book opens.
 */
@Environment(EnvType.CLIENT)
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {

    private InventoryScreenMixin() {
        super(null, null, null);
    }

    private static final int BUTTON_FACE  = 0xFF8B8B8B;
    private static final int BUTTON_HOVER = 0xFFA8A8A8;
    private static final int BUTTON_TEXT  = 0xFF373737;

    // Colors matching ItemSlotComponent.render()
    private static final int SLOT_BORDER_DARK  = 0xFF373737;
    private static final int SLOT_BORDER_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_INNER        = 0xFF8B8B8B;

    /** HEAD, so vanilla draws the slot items on top of these backgrounds. */
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

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void pandorical$drawInventoryButtons(GuiGraphicsExtractor graphics,
            int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        var buttons = ClientInventoryButtons.all();
        if (buttons.isEmpty()) return;

        var font = Minecraft.getInstance().font;
        for (var button : buttons) {
            int bx = this.leftPos + button.screenX();
            int by = this.topPos + button.screenY();
            int size = button.size();
            boolean over = mouseX >= bx && mouseX < bx + size && mouseY >= by && mouseY < by + size;

            graphics.fill(bx, by, bx + size, by + size, SLOT_BORDER_DARK);
            graphics.fill(bx + 1, by + 1, bx + size - 1, by + size - 1,
                over ? BUTTON_HOVER : BUTTON_FACE);

            // A face is a sprite id or a character to draw; only a sprite id contains a colon.
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
