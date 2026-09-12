package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ContainerDef;
import justfatlard.pandorical.protocol.OpenScreenS2C;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ScreenBuilder {
    private final String screenType;
    private String screenId;
    private int width = 176;
    private int height = 166;
    private boolean pauseGame = false;
    private String title = "";
    private final List<ComponentDef> components = new ArrayList<>();
    private ContainerDef containerDef = null;

    private String recipeStation = null;

    public ScreenBuilder(String screenType) {
        this.screenType = screenType;
        this.screenId = UUID.randomUUID().toString();
    }

    public ScreenBuilder id(String screenId) {
        this.screenId = screenId;
        return this;
    }

    public ScreenBuilder size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public ScreenBuilder pauseGame(boolean pause) {
        this.pauseGame = pause;
        return this;
    }

    public ScreenBuilder title(String title) {
        this.title = title;
        return this;
    }

    public ScreenBuilder container(int slotCount, boolean includePlayerInventory) {
        this.containerDef = new ContainerDef(slotCount, includePlayerInventory);
        return this;
    }

    /**
     * Declare this screen a crafting station, so a client-side recipe book can be shown on it.
     * A client with no such book shows nothing. See {@link ScreenApi#onPlaceRecipe}.
     *
     * @param categoryId a registered {@code RecipeBookCategory} id, e.g. {@code fletch_craft:fletching}
     */
    public ScreenBuilder recipeStation(String categoryId) {
        this.recipeStation = categoryId;
        return this;
    }

    public ScreenBuilder component(ComponentDef component) {
        this.components.add(component);
        return this;
    }

    public ScreenBuilder component(ComponentBuilder builder) {
        this.components.add(builder.build());
        return this;
    }

    public ScreenBuilder panel(String id, int x, int y, int w, int h, Map<String, String> props) {
        this.components.add(new ComponentBuilder(id, ComponentType.PANEL)
            .bounds(x, y, w, h).props(props).build());
        return this;
    }

    /**
     * Children use panel-relative coordinates and are laid out once, unscrolled: the client clips
     * and scrolls them itself, and reports {@code scroll_offset} as an action on this component
     * id. The scrollbar appears only when total_items exceeds visible_items.
     */
    public ScreenBuilder scrollPanel(String id, int x, int y, int w, int h,
            Map<String, String> props, List<ComponentDef> children) {
        ComponentBuilder builder = new ComponentBuilder(id, ComponentType.SCROLL_PANEL)
            .bounds(x, y, w, h).props(props);
        for (ComponentDef child : children) {
            builder.child(child);
        }
        this.components.add(builder.build());
        return this;
    }

    public ScreenBuilder button(String id, int x, int y, int w, int h, Map<String, String> props) {
        this.components.add(new ComponentBuilder(id, ComponentType.BUTTON)
            .bounds(x, y, w, h).props(props).build());
        return this;
    }

    public ScreenBuilder text(String id, int x, int y, String text) {
        this.components.add(new ComponentBuilder(id, ComponentType.TEXT)
            .pos(x, y).prop("text", text).build());
        return this;
    }

    public ScreenBuilder text(String id, int x, int y, Map<String, String> props) {
        this.components.add(new ComponentBuilder(id, ComponentType.TEXT)
            .pos(x, y).props(props).build());
        return this;
    }

    public ScreenBuilder inventoryGrid(String id, int x, int y, int rows, int cols, int startSlot) {
        return inventoryGrid(id, x, y, rows, cols, startSlot, Map.of());
    }

    /** Rows, columns and first slot are applied after {@code props} and win over them. */
    public ScreenBuilder inventoryGrid(String id, int x, int y, int rows, int cols, int startSlot,
            Map<String, String> props) {
        this.components.add(new ComponentBuilder(id, ComponentType.INVENTORY_GRID)
            .bounds(x, y, cols * 18, rows * 18)
            .props(props)
            .prop("rows", String.valueOf(rows))
            .prop("cols", String.valueOf(cols))
            .prop("start_slot", String.valueOf(startSlot))
            .build());
        return this;
    }

    /** @param count shown on the icon when above 1 */
    public ScreenBuilder itemIcon(String id, int x, int y, String itemId, int count) {
        this.components.add(new ComponentBuilder(id, ComponentType.ITEM_ICON)
            .bounds(x, y, 16, 16)
            .prop(ComponentType.PROP_ITEM_ID, itemId)
            .prop(ComponentType.PROP_ITEM_COUNT, String.valueOf(count))
            .build());
        return this;
    }

    public ScreenBuilder sprite(String id, int x, int y, int w, int h, Map<String, String> props) {
        this.components.add(new ComponentBuilder(id, ComponentType.SPRITE)
            .bounds(x, y, w, h).props(props).build());
        return this;
    }

    public String screenId() {
        return screenId;
    }

    public OpenScreenS2C build() {
        return new OpenScreenS2C(
            screenId, screenType, width, height, pauseGame, title,
            List.copyOf(components),
            Optional.ofNullable(containerDef),
            Optional.ofNullable(recipeStation)
        );
    }
}
