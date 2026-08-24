package justfatlard.pandorical.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * API for registering extra slots that appear in the vanilla inventory screen.
 *
 * <p>Server-side mods call {@link #registerSlots} during mod initialisation. Pandorical patches
 * {@code InventoryMenu} on both the server and the client so the declared slots show up in the
 * player's inventory screen and behave like real container slots (the server syncs their content
 * automatically through vanilla's container synchronisation mechanism).
 *
 * <p>Slot content is persisted automatically via Fabric data attachments and survives server
 * restarts.
 *
 * <p>Obtain the singleton via {@link PandoricalApi#playerInventory()}.
 */
public interface PlayerInventoryApi {

    /**
     * Describes a single extra inventory slot contributed by a namespace.
     *
     * @param slotIndex        unique index within this namespace's slot group (0-based)
     * @param screenX          x pixel position on the vanilla inventory screen (176×166 coordinate space)
     * @param screenY          y pixel position on the vanilla inventory screen
     * @param validator        returns {@code true} if the given {@link ItemStack} may be placed in this slot;
     *                         use {@code stack -> true} to allow any item
     * @param backgroundSprite optional sprite identifier (e.g. {@code "map-plus-plus:empty_map_slot"})
     *                         to draw instead of the generic grey beveled background; {@code null} uses default
     */
    record SlotEntry(int slotIndex, int screenX, int screenY, Predicate<ItemStack> validator,
                     @Nullable String backgroundSprite) {

        /** Convenience constructor without a custom background sprite. */
        SlotEntry(int slotIndex, int screenX, int screenY, Predicate<ItemStack> validator) {
            this(slotIndex, screenX, screenY, validator, null);
        }
    }

    /**
     * Immutable snapshot of a namespace's slot registration.
     *
     * @param namespace unique {@link Identifier} for the registering mod (e.g. {@code map-plus-plus-justfatlard:slots})
     * @param slots     ordered list of slots declared by the namespace
     */
    record SlotRegistration(Identifier namespace, List<SlotEntry> slots) {}

    /**
     * Register extra inventory slots for a namespace. Must be called during server initialisation
     * (before any players connect). Registrations are sent to clients during the handshake.
     *
     * @param namespace unique identifier for the slot group; used as the storage key in player data
     * @param slots     the slots to add, ordered by {@link SlotEntry#slotIndex()}
     */
    void registerSlots(Identifier namespace, List<SlotEntry> slots);

    /**
     * Return the item currently in a player's extra slot,
     * or {@link ItemStack#EMPTY}.
     */
    ItemStack getSlot(ServerPlayer player, Identifier namespace, int slotIndex);

    /**
     * Replace the item in a player's extra slot, and tell {@link #onSlotChange} listeners.
     *
     * <p>It used to stay quiet, on the grounds that a programmatic write should not re-trigger
     * its own listener. What that actually bought was a slot with two readers and only one of
     * them told: a mod writing here updated what the inventory screen showed and not the mirror
     * another mod was reading, so a death compass placed in map-plus-plus's slot left its needle
     * pointing at spawn. A store that informs half its readers is worse than one that informs
     * none, because the disagreement is invisible.
     *
     * <p>Re-entrancy is handled where it belongs: a listener writing back into the slot it was
     * just told about is ignored rather than looped.
     */
    void setSlot(ServerPlayer player, Identifier namespace, int slotIndex, ItemStack stack);

    /**
     * Register a callback invoked after any extra-slot change is processed on the server.
     * Multiple listeners for the same namespace are supported (appended in order).
     */
    void onSlotChange(Identifier namespace, BiConsumer<ServerPlayer, SlotChangeEvent> handler);

    /**
     * Put a button on the player's own inventory screen.
     *
     * <p>For the actions that belong to a player rather than to a container: sorting your own
     * pack is wanted while standing in a field, not only while looking into a chest.
     *
     * @param x     offset from the inventory panel's top-left corner, in gui pixels
     * @param size  width and height; buttons here are square
     * @param glyph the character to draw on it - no texture to ship, and it reads at 16 pixels
     *              where a word does not
     */
    void registerButton(Identifier namespace, String id, int x, int y, int size, String glyph);

    /** Called when somebody presses one of {@link #registerButton}'s buttons. */
    void onButton(Identifier namespace, String id, java.util.function.Consumer<ServerPlayer> handler);

    /**
     * Change what one player sees drawn on a button.
     *
     * <p>For a button that is a switch rather than a command. A switch has to say which way it is
     * set, and the only surface it has to say it on is its own face - so the glyph is the state,
     * and it is per player because the state is.
     *
     * <p>Takes effect on an inventory screen already open. A client too old to be told simply
     * keeps the glyph it was given at registration.
     */
    void setButtonGlyph(ServerPlayer player, Identifier namespace, String id, String glyph);

    record SlotChangeEvent(int slotIndex, ItemStack newStack) {}
}
