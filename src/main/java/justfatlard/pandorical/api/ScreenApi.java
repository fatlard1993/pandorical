package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.OpenScreenS2C;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.Set;
import java.util.function.Consumer;

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
                       Container serverContainer, Set<Integer> readOnlySlots);

    /**
     * Send partial property updates to a live screen.
     *
     * <p><b>The id, not the screen type.</b> {@link ScreenBuilder} mints a fresh id for every
     * opening and the client matches on it, so the type constant a mod keeps as a field is the
     * wrong argument here and the update goes nowhere. Hold on to {@code screenBuilder.screenId()}
     * when the screen is opened and pass that back. Getting it wrong is logged rather than
     * dropped in silence, but the compiler cannot tell the two strings apart.
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
    void onClose(String screenType, Consumer<ServerPlayer> handler);

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
     * Register a handler for a recipe book asking this station to lay a recipe out in its grid.
     *
     * <p>Vanilla's own route cannot serve these screens. {@code ServerboundPlaceRecipePacket} is
     * answered only for a {@code RecipeBookMenu}, and a Pandorical menu is not one - so a book
     * opened on a station could show a recipe and never fill it, which is exactly how it behaved
     * before this existed. The station owns its container and its recipe type, so the station is
     * what places the ingredients; this is only the doorbell.
     *
     * <p>Reaches the server as an ordinary screen action on the reserved component id
     * {@link #PLACE_RECIPE_COMPONENT}, so no packet is anybody's own.
     */
    void onPlaceRecipe(String screenType, PlaceRecipeHandler handler);

    /**
     * The reserved component id a client-side recipe book sends to ask for a recipe to be laid
     * out, carrying {@link #PLACE_RECIPE_DATA_RECIPE} and {@link #PLACE_RECIPE_DATA_ALL}.
     */
    String PLACE_RECIPE_COMPONENT = "_place_recipe";
    /**
     * The recipe's display index, as a decimal string.
     *
     * <p>A display id and not a registry name, because a display id is the only handle a client
     * has: recipes reach it through the display sync, keyed by an index the server assigned.
     * Pandorical turns it back into the recipe before the station ever sees it.
     */
    String PLACE_RECIPE_DATA_RECIPE = "recipe";
    /** "true" to fill the grid as far as the ingredients allow, "false" for a single craft. */
    String PLACE_RECIPE_DATA_ALL = "all";

    @FunctionalInterface
    interface PlaceRecipeHandler {
        /**
         * @param recipe      the recipe the book asked for, already resolved; a station should
         *                    check it is one of its own type and ignore anything else
         * @param useMaxItems fill the grid as far as the ingredients allow, rather than once
         */
        void placeRecipe(ServerPlayer player, RecipeHolder<?> recipe, boolean useMaxItems);
    }

    /**
     * Register a handler for container menu removal (player closes screen, disconnects, etc).
     * Use this to return items to the player.
     */
    void onContainerRemoved(String screenType, Consumer<ServerPlayer> handler);

    @FunctionalInterface
    interface SlotChangeHandler {
        void onSlotChange(ServerPlayer player, int slotIndex, ItemStack stack);
    }
}
