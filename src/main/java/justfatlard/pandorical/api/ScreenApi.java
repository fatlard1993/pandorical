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
    String FALLBACK_COMPONENT_ID_KEY = "_componentId";
    /**
     * For a screen without slots; one built with {@link ScreenBuilder#container} needs
     * {@link #openContainer}.
     */
    void open(ServerPlayer player, OpenScreenS2C screen);

    /**
     * @param serverContainer the server-side container backing the mod slots
     * @param readOnlySlots slot indices that the player cannot modify
     */
    void openContainer(ServerPlayer player, OpenScreenS2C screen,
                       Container serverContainer, Set<Integer> readOnlySlots);

    /**
     * Only the listed props change.
     *
     * <p><b>The id, not the screen type.</b> {@link ScreenBuilder} mints a fresh id for every
     * opening and the client matches on it: keep {@code screenBuilder.screenId()} from the
     * opening and pass that. A type here is logged and the update goes nowhere.
     */
    void update(ServerPlayer player, String screenId, List<ComponentUpdate> updates);

    void close(ServerPlayer player, String screenId);

    void onAction(String screenType, String componentId, BiConsumer<ServerPlayer, Map<String, String>> handler);

    void onClose(String screenType, Consumer<ServerPlayer> handler);

    /**
     * Called when no {@link #onAction} handler matches the component; the component id is in the
     * data under {@link #FALLBACK_COMPONENT_ID_KEY}.
     */
    void onActionFallback(String screenType, BiConsumer<ServerPlayer, Map<String, String>> handler);

    /** Called after any slot click in a Pandorical container screen. */
    void onSlotChange(String screenType, SlotChangeHandler handler);

    /**
     * A recipe book asks this station to lay a recipe out in its grid; the handler places the
     * ingredients itself. Vanilla answers {@code ServerboundPlaceRecipePacket} only for a
     * {@code RecipeBookMenu}, which a Pandorical menu is not.
     *
     * <p>Arrives as a screen action on {@link #PLACE_RECIPE_COMPONENT}.
     */
    void onPlaceRecipe(String screenType, PlaceRecipeHandler handler);

    /**
     * Reserved component id; its data carries {@link #PLACE_RECIPE_DATA_RECIPE} and
     * {@link #PLACE_RECIPE_DATA_ALL}.
     */
    String PLACE_RECIPE_COMPONENT = "_place_recipe";
    /** The recipe's display index, as a decimal string. */
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

    /** When the container menu goes, by close or disconnect: return items to the player here. */
    void onContainerRemoved(String screenType, Consumer<ServerPlayer> handler);

    @FunctionalInterface
    interface SlotChangeHandler {
        void onSlotChange(ServerPlayer player, int slotIndex, ItemStack stack);
    }
}
