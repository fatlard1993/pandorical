package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ContainerDef;
import justfatlard.pandorical.protocol.OpenScreenS2C;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fluent builder for constructing OpenScreenS2C payloads.
 * Server mods use this to describe screens declaratively.
 */
public class ScreenBuilder {
    private final String screenType;
    private String screenId;
    private int width = 176;
    private int height = 166;
    private boolean pauseGame = false;
    private String title = "";
    private final List<ComponentDef> components = new ArrayList<>();
    private ContainerDef containerDef = null;

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

    /**
     * Mark this screen as a container screen with item slots.
     * Must use openContainer() instead of open() when this is set.
     */
    public ScreenBuilder container(int slotCount, boolean includePlayerInventory) {
        this.containerDef = new ContainerDef(slotCount, includePlayerInventory);
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

    // Convenience methods for common components
    // See ComponentType for valid prop keys per component type.

    /** Add a panel component. Props: background, border, border_light/dark/mid_light/mid_dark, border_color */
    public ScreenBuilder panel(String id, int x, int y, int w, int h, Map<String, String> props) {
        this.components.add(new ComponentBuilder(id, ComponentType.PANEL)
            .bounds(x, y, w, h).props(props).build());
        return this;
    }

    /** Add a button component. Props: label, label_key, enabled, style ("default" or "accepted") */
    public ScreenBuilder button(String id, int x, int y, int w, int h, Map<String, String> props) {
        this.components.add(new ComponentBuilder(id, ComponentType.BUTTON)
            .bounds(x, y, w, h).props(props).build());
        return this;
    }

    /** Add a text component with simple text. Props: text, text_key, color, shadow, wrap_width, max_lines */
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

    /** Add an inventory grid. Additional props: locked_above, slot_style */
    public ScreenBuilder inventoryGrid(String id, int x, int y, int rows, int cols, int startSlot) {
        this.components.add(new ComponentBuilder(id, ComponentType.INVENTORY_GRID)
            .bounds(x, y, cols * 18, rows * 18)
            .prop("rows", String.valueOf(rows))
            .prop("cols", String.valueOf(cols))
            .prop("start_slot", String.valueOf(startSlot))
            .build());
        return this;
    }

    /** Add an item icon. itemId is a registry path e.g. "minecraft:red_shrub". count > 1 shows stack overlay. */
    public ScreenBuilder itemIcon(String id, int x, int y, String itemId, int count) {
        this.components.add(new ComponentBuilder(id, ComponentType.ITEM_ICON)
            .bounds(x, y, 16, 16)
            .prop(ComponentType.PROP_ITEM_ID, itemId)
            .prop(ComponentType.PROP_ITEM_COUNT, String.valueOf(count))
            .build());
        return this;
    }

    /** Add a sprite (colored rectangle). Props: color */
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
            Optional.ofNullable(containerDef)
        );
    }
}
