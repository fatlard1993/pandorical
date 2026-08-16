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
