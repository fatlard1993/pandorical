package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/**
 * Scrollable container that clips children to a visible region.
 * Children are laid out vertically; scrolling shifts which are visible.
 *
 * Props:
 *   scroll_offset: current scroll position in items (default 0)
 *   scroll_step: items per wheel notch (default 1)
 *   item_height: height per item (default 22)
 *   visible_items: how many items fit (default 5)
 *   total_items: total item count for scroll bounds
 *   show_scrollbar: "true"/"false" (default true)
 *   background: background color (default transparent)
 */
public class ScrollPanelComponent extends AbstractComponent {
    private int scrollOffset;
    /** Items per wheel notch: one by default, more for a panel whose items are lines of text. */
    private int scrollStep;
    private int itemHeight;
    private int visibleItems;
    private int totalItems;
    private boolean showScrollbar;
    private int bgColor;

    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_THUMB_HEIGHT = 10;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        parseStyle();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        parseStyle();
    }

    private void parseStyle() {
        scrollOffset = parseInt("scroll_offset", 0);
        scrollStep = Math.max(1, parseInt("scroll_step", 1));
        itemHeight = parseInt("item_height", 22);
        visibleItems = parseInt("visible_items", 5);
        totalItems = parseInt("total_items", 0);
        showScrollbar = parseBool("show_scrollbar", true);
        bgColor = parseColor("background", 0x00000000);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Background
        if ((bgColor & 0xFF000000) != 0) {
            graphics.fill(x, y, x + width, y + height, bgColor);
        }

        // Scrollbar
        if (showScrollbar && totalItems > visibleItems) {
            int scrollbarX = x + width - SCROLLBAR_WIDTH;
            int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) ((float) visibleItems / totalItems * height));
            int maxScroll = totalItems - visibleItems;
            int thumbY = maxScroll > 0 ? y + (int) ((float) scrollOffset / maxScroll * (height - thumbHeight)) : y;

            // Track
            graphics.fill(scrollbarX, y, scrollbarX + SCROLLBAR_WIDTH, y + height, 0xFF333333);
            // Thumb
            graphics.fill(scrollbarX + 1, thumbY, scrollbarX + SCROLLBAR_WIDTH - 1, thumbY + thumbHeight, 0xFFAAAAAA);
        }

        // Children are rendered by ScreenHelper.renderComponentTree, which
        // scissor-clips them to getClipBounds() and translates them up by
        // scrollPixels(): the scroll is entirely client-side visual state,
        // children keep the positions they were built with.
    }

    /** Current scroll displacement in pixels; children draw shifted up by this. */
    public int scrollPixels() {
        return scrollOffset * itemHeight;
    }

    /**
     * Returns the clip bounds for this scroll panel.
     * Used by ScreenHelper to apply scissor clipping when rendering children.
     */
    public int[] getClipBounds() {
        return new int[]{ x, y, x + width, y + height };
    }

    /**
     * Wheel travel not yet spent on a whole item. A notch is one event of one on a plain wheel
     * and a run of small fractions on a high-resolution wheel or a touchpad; stepping a whole
     * item on every event made those fly through the pane several items a notch.
     */
    private double pending;

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isMouseOver(mouseX, mouseY) || totalItems <= visibleItems) return false;
        pending -= amount * scrollStep;
        int steps = (int) pending;
        if (steps == 0) return true;
        pending -= steps;
        int newOffset = Math.clamp(scrollOffset + steps, 0, totalItems - visibleItems);
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset;
            if (context != null && context.sendAction() != null) {
                context.sendAction().accept(id, Map.of(
                    "scroll_offset", String.valueOf(scrollOffset)
                ));
            }
        }
        return true;
    }
}
