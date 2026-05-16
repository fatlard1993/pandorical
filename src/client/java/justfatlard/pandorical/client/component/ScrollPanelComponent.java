package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/**
 * Scrollable container that clips children to a visible region.
 * Children are laid out vertically; scrolling shifts which are visible.
 *
 * Props:
 *   scroll_offset — current scroll position in items (default 0)
 *   item_height — height per item (default 22)
 *   visible_items — how many items fit (default 5)
 *   total_items — total item count for scroll bounds
 *   show_scrollbar — "true"/"false" (default true)
 *   background — background color (default transparent)
 */
public class ScrollPanelComponent extends AbstractComponent {
    private int scrollOffset;
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

        // Children are rendered by ScreenHelper.renderComponentTree, which is called
        // by the parent screen. The scissor clipping is applied in getClipBounds().
        // For now, children rendering relies on the server managing which children
        // are within the visible range based on scroll_offset.
    }

    /**
     * Returns the clip bounds for this scroll panel.
     * Used by ScreenHelper to apply scissor clipping when rendering children.
     */
    public int[] getClipBounds() {
        return new int[]{ x, y, x + width, y + height };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (isMouseOver(mouseX, mouseY) && totalItems > visibleItems) {
            int newOffset = scrollOffset;
            if (amount > 0 && scrollOffset > 0) {
                newOffset = scrollOffset - 1;
            } else if (amount < 0 && scrollOffset < totalItems - visibleItems) {
                newOffset = scrollOffset + 1;
            }
            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                if (context != null && context.sendAction() != null) {
                    context.sendAction().accept(id, Map.of(
                        "scroll_offset", String.valueOf(scrollOffset)
                    ));
                }
                return true;
            }
        }
        return false;
    }
}
