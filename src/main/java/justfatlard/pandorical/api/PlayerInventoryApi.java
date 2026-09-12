package justfatlard.pandorical.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Consumer;

/**
 * Extra slots and buttons in the vanilla inventory screen. The slots are real container slots,
 * synced by vanilla.
 *
 * <p>Slot contents persist across restarts and are carried across death: a mod that takes them
 * on death empties the slots itself.
 */
public interface PlayerInventoryApi {

    /**
     * Describes a single extra inventory slot contributed by a namespace.
     *
     * @param slotIndex        unique index within this namespace's slot group (0-based)
     * @param screenX          x pixel position on the vanilla inventory screen (176x166 coordinate space)
     * @param screenY          y pixel position on the vanilla inventory screen
     * @param backgroundSprite sprite id drawn instead of the grey beveled background, or {@code null}
     */
    record SlotEntry(int slotIndex, int screenX, int screenY, Predicate<ItemStack> validator,
                     @Nullable String backgroundSprite) {

        SlotEntry(int slotIndex, int screenX, int screenY, Predicate<ItemStack> validator) {
            this(slotIndex, screenX, screenY, validator, null);
        }
    }

    record SlotRegistration(Identifier namespace, List<SlotEntry> slots) {}

    /**
     * Call during server initialisation, before any player connects: registrations reach clients
     * in the handshake.
     *
     * @param namespace the storage key in player data
     * @param slots     ordered by {@link SlotEntry#slotIndex()}
     */
    void registerSlots(Identifier namespace, List<SlotEntry> slots);

    /** In registration order. */
    List<SlotRegistration> registeredSlots();

    /** Never null: {@link ItemStack#EMPTY} for an empty or unknown slot. */
    ItemStack getSlot(ServerPlayer player, Identifier namespace, int slotIndex);

    /**
     * Tells {@link #onSlotChange} listeners too. A listener writing back into the slot it was
     * just told about is ignored rather than looped.
     */
    void setSlot(ServerPlayer player, Identifier namespace, int slotIndex, ItemStack stack);

    /** Called on the server after any change to the namespace's slots, listeners in order. */
    void onSlotChange(Identifier namespace, BiConsumer<ServerPlayer, SlotChangeEvent> handler);

    /**
     * Put a button on the player's own inventory screen.
     *
     * @param x     offset from the inventory panel's top-left corner, in gui pixels
     * @param size  width and height; buttons here are square
     * @param glyph a character, or anything with a colon as a GUI atlas sprite id filling the
     *              face, e.g. {@code "mymod:icon_sort"} for
     *              {@code assets/mymod/textures/gui/sprites/icon_sort.png}
     */
    void registerButton(Identifier namespace, String id, int x, int y, int size, String glyph);

    void onButton(Identifier namespace, String id, Consumer<ServerPlayer> handler);

    /**
     * Change what one player sees on a button, e.g. to show a switch's state. Takes effect on an
     * open screen; a client too old for it keeps the registered glyph. Forgotten on disconnect,
     * so set it again each session.
     */
    void setButtonGlyph(ServerPlayer player, Identifier namespace, String id, String glyph);

    record SlotChangeEvent(int slotIndex, ItemStack newStack) {}
}
