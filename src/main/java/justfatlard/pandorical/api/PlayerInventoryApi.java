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
     * Return the item currently in a player's extra slot.
     *
     * @param player    the server-side player
     * @param namespace the namespace used when registering the slot group
     * @param slotIndex slot index within that namespace's group
     * @return the current {@link ItemStack}, or {@link ItemStack#EMPTY}
     */
    ItemStack getSlot(ServerPlayer player, Identifier namespace, int slotIndex);

    /**
     * Replace the item in a player's extra slot. Does not trigger
     * {@link #onSlotChange} listeners — use this for programmatic writes during initialisation
     * or reward logic where you do not want to re-trigger your own listener.
     *
     * @param player    the server-side player
     * @param namespace the namespace used when registering the slot group
     * @param slotIndex slot index within that namespace's group
     * @param stack     the new {@link ItemStack} to place in the slot
     */
    void setSlot(ServerPlayer player, Identifier namespace, int slotIndex, ItemStack stack);

    /**
     * Register a callback invoked after any extra-slot change is processed on the server.
     * Multiple listeners for the same namespace are supported (they are appended in order).
     *
     * @param namespace the namespace whose slot changes should be reported
     * @param handler   called with (player, slotIndex, newStack)
     */
    void onSlotChange(Identifier namespace, BiConsumer<ServerPlayer, SlotChangeEvent> handler);

    /**
     * Data class passed to {@link #onSlotChange} listeners.
     *
     * @param slotIndex the index within the namespace's slot group that changed
     * @param newStack  the new contents of the slot
     */
    record SlotChangeEvent(int slotIndex, ItemStack newStack) {}
}
