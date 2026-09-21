package justfatlard.pandorical.client.component;

import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.NotUnderstood;
import justfatlard.pandorical.client.ClientNotices;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ComponentRegistry {
    private ComponentRegistry() {}

    private static final Map<String, Supplier<PandoricalComponent>> FACTORIES = new HashMap<>();

    public static void register(String type, Supplier<PandoricalComponent> factory) {
        FACTORIES.put(type, factory);
    }

    /** {@code fallback} is the server's {@link ComponentType#PROP_FALLBACK}, or null. */
    public static PandoricalComponent create(String type, String fallback) {
        Supplier<PandoricalComponent> factory = FACTORIES.get(type);
        if (factory != null) return factory.get();
        Supplier<PandoricalComponent> instead = fallback == null ? null : FACTORIES.get(fallback);
        ClientNotices.report(NotUnderstood.COMPONENT_TYPE, type);
        return instead != null ? instead.get() : new UnknownComponent();
    }

    public static void registerDefaults() {
        register(ComponentType.PANEL, PanelComponent::new);
        register(ComponentType.BUTTON, ButtonComponent::new);
        register(ComponentType.TEXT, TextComponent::new);
        register(ComponentType.TEXT_INPUT, TextInputComponent::new);
        register(ComponentType.ITEM_SLOT, ItemSlotComponent::new);
        register(ComponentType.ITEM_ICON, ItemIconComponent::new);
        register(ComponentType.INVENTORY_GRID, InventoryGridComponent::new);
        register(ComponentType.SCROLL_PANEL, ScrollPanelComponent::new);
        register(ComponentType.SPRITE, SpriteComponent::new);
        register(ComponentType.MAP, MapComponent::new);
        register(ComponentType.RADAR, RadarComponent::new);
        register(ComponentType.PARTICLE_BURST, ParticleBurstComponent::new);
        register(ComponentType.DIAL, DialComponent::new);
        register(ComponentType.PIXEL_CANVAS, PixelCanvasComponent::new);
        register(ComponentType.PLAYER_FACE, PlayerFaceComponent::new);
    }
}
