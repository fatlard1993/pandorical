package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.OpenScreenS2C;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public interface ScreenApi {
    /**
     * Key injected into the data map passed to
     * {@link #onActionFallback} handlers so they can identify which component
     * triggered the action. The value is the component's ID string.
     */
    String FALLBACK_COMPONENT_ID_KEY = "_componentId";
    /**
     * Open a non-container declarative screen for the player.
     */
    void open(ServerPlayer player, OpenScreenS2C screen);

    /**
     * Open a container screen with slot management.
     * Creates a PandoricalMenu with the specified slots, opens via player.openMenu(),
     * and sends the component layout via OpenScreenS2C.
     *
     * @param player the player to open the screen for
     * @param screen the screen definition (must have container() present)
     * @param serverContainer the server-side container backing the mod slots
     * @param readOnlySlots slot indices that the player cannot modify (e.g., "their offer")
     */
    void openContainer(ServerPlayer player, OpenScreenS2C screen,
                       Container serverContainer, java.util.Set<Integer> readOnlySlots);

    /**
     * Send partial property updates to a live screen.
     */
    void update(ServerPlayer player, String screenId, List<ComponentUpdate> updates);

    /**
     * Close a screen by ID.
     */
    void close(ServerPlayer player, String screenId);

    /**
     * Register a handler for screen actions.
     * Matched by screenType + componentId from ScreenActionC2S.
     */
    void onAction(String screenType, String componentId, BiConsumer<ServerPlayer, Map<String, String>> handler);

    /**
     * Register a handler for screen close events.
     */
    void onClose(String screenType, java.util.function.Consumer<ServerPlayer> handler);

    /**
     * Register a catch-all handler for actions with dynamic component IDs.
     * Called when no specific componentId handler matches.
     * The componentId is passed in the data map as "_componentId".
     */
    void onActionFallback(String screenType, BiConsumer<ServerPlayer, Map<String, String>> handler);

    /**
     * Register a handler for when a container slot changes.
     * Called after any slot click in a Pandorical container screen.
     */
    void onSlotChange(String screenType, SlotChangeHandler handler);

    /**
     * Register a handler for container menu removal (player closes screen, disconnects, etc).
     * Use this to return items to the player.
     */
    void onContainerRemoved(String screenType, java.util.function.Consumer<ServerPlayer> handler);

    @FunctionalInterface
    interface SlotChangeHandler {
        void onSlotChange(ServerPlayer player, int slotIndex, ItemStack stack);
    }
}
