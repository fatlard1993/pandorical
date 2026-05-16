package justfatlard.pandorical.client.component;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.ComponentType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Maps component type strings to factory functions.
 * Built-in types are registered at startup; mods can add custom types.
 */
public final class ComponentRegistry {
    private ComponentRegistry() {}

    private static final Map<String, Supplier<PandoricalComponent>> FACTORIES = new HashMap<>();

    public static void register(String type, Supplier<PandoricalComponent> factory) {
        FACTORIES.put(type, factory);
    }

    public static PandoricalComponent create(String type) {
        Supplier<PandoricalComponent> factory = FACTORIES.get(type);
        if (factory == null) {
            Pandorical.LOGGER.warn("Unknown component type '{}' — rendering as empty panel. " +
                "Check spelling or ensure the component is registered.", type);
            return new PanelComponent();
        }
        return factory.get();
    }

    public static void registerDefaults() {
        register(ComponentType.PANEL, PanelComponent::new);
        register(ComponentType.BUTTON, ButtonComponent::new);
        register(ComponentType.TEXT, TextComponent::new);
        register(ComponentType.TEXT_INPUT, TextInputComponent::new);
        register(ComponentType.ITEM_SLOT, ItemSlotComponent::new);
        register(ComponentType.INVENTORY_GRID, InventoryGridComponent::new);
        register(ComponentType.SCROLL_PANEL, ScrollPanelComponent::new);
        register(ComponentType.SPRITE, SpriteComponent::new);
        register(ComponentType.MAP, MapComponent::new);
    }
}
