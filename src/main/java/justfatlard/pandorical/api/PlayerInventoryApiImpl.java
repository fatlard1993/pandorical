package justfatlard.pandorical.api;

import com.mojang.serialization.Codec;
import justfatlard.pandorical.Pandorical;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Server-side implementation of {@link PlayerInventoryApi}.
 *
 * <p>Extra slot contents are stored on the player via a Fabric data attachment that is
 * persisted automatically by Fabric (it serialises the codec on player save/load).
 */
public final class PlayerInventoryApiImpl implements PlayerInventoryApi {

    // --- Static slot registry (populated during mod init, read-only after that) ---

    private final List<SlotRegistration> registrations = new ArrayList<>();

    // Map: namespace string → list of change-listeners
    private final Map<String, List<BiConsumer<ServerPlayer, SlotChangeEvent>>> listeners =
        new HashMap<>();

    // --- Fabric attachment: Map<namespace-string, List<ItemStack>> per player ---

    /**
     * Codec for {@code Map<String, List<ItemStack>>}.
     * Each namespace maps to an ordered list of ItemStack (one per registered slot).
     */
    private static final Codec<Map<String, List<ItemStack>>> SLOTS_CODEC =
        Codec.unboundedMap(
            Codec.STRING,
            ItemStack.OPTIONAL_CODEC.listOf()
        );

    public static final AttachmentType<Map<String, List<ItemStack>>> EXTRA_SLOTS =
        AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(Pandorical.MOD_ID, "extra_slots"),
            SLOTS_CODEC
        );

    // --- PlayerInventoryApi implementation ---

    @Override
    public void registerSlots(Identifier namespace, List<SlotEntry> slots) {
        // Defensive copy so callers cannot mutate after registration.
        registrations.add(new SlotRegistration(namespace, List.copyOf(slots)));
        Pandorical.LOGGER.info("[pandorical] Registered {} extra inventory slot(s) for namespace '{}'",
            slots.size(), namespace);
    }

    @Override
    public ItemStack getSlot(ServerPlayer player, Identifier namespace, int slotIndex) {
        Map<String, List<ItemStack>> map = player.getAttached(EXTRA_SLOTS);
        if (map == null) return ItemStack.EMPTY;
        List<ItemStack> list = map.get(namespace.toString());
        if (list == null || slotIndex < 0 || slotIndex >= list.size()) return ItemStack.EMPTY;
        ItemStack stack = list.get(slotIndex);
        return stack != null ? stack : ItemStack.EMPTY;
    }

    @Override
    public void setSlot(ServerPlayer player, Identifier namespace, int slotIndex, ItemStack stack) {
        Map<String, List<ItemStack>> map = getMutableSlots(player);
        String key = namespace.toString();
        List<ItemStack> list = map.computeIfAbsent(key, k -> {
            // Size from registration
            int size = registrations.stream()
                .filter(r -> r.namespace().equals(namespace))
                .mapToInt(r -> r.slots().size())
                .findFirst()
                .orElse(slotIndex + 1);
            return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
        });
        // Grow if needed (edge case: called before registration size known)
        while (list.size() <= slotIndex) list.add(ItemStack.EMPTY);
        list.set(slotIndex, stack == null ? ItemStack.EMPTY : stack);

        // Re-attach the (potentially mutated) map
        player.setAttached(EXTRA_SLOTS, map);

        // Sync via vanilla container mechanism
        player.inventoryMenu.broadcastChanges();
    }

    @Override
    public void onSlotChange(Identifier namespace, BiConsumer<ServerPlayer, SlotChangeEvent> handler) {
        listeners.computeIfAbsent(namespace.toString(), k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    // --- Package-private helpers used by the InventoryMenuMixin ---

    /** All registrations, in order. Called by the server-side mixin. */
    public List<SlotRegistration> getRegistrations() {
        return Collections.unmodifiableList(registrations);
    }

    /**
     * Called by the server-side mixin after a slot click has been processed.
     * Reads the current contents from the player's attachment and fires listeners
     * for any namespace whose slots are included in the menu.
     */
    public void fireSlotChangeListeners(ServerPlayer player, int menuSlotIndex, ItemStack newStack) {
        // Determine which namespace + local slot this menu index corresponds to.
        // The slots start at vanilla's 46 (result + craft + armor + inv + hotbar + shield).
        int extra = menuSlotIndex - VANILLA_INVENTORY_MENU_SLOT_COUNT;
        if (extra < 0) return;

        int offset = 0;
        for (SlotRegistration reg : registrations) {
            int size = reg.slots().size();
            if (extra < offset + size) {
                int localIndex = extra - offset;
                String key = reg.namespace().toString();
                List<BiConsumer<ServerPlayer, SlotChangeEvent>> handlers = listeners.get(key);
                if (handlers != null) {
                    SlotChangeEvent event = new SlotChangeEvent(localIndex, newStack.copy());
                    for (BiConsumer<ServerPlayer, SlotChangeEvent> h : handlers) {
                        try {
                            h.accept(player, event);
                        } catch (Exception e) {
                            Pandorical.LOGGER.error("[pandorical] Exception in slot-change listener for namespace '{}': {}",
                                key, e.getMessage(), e);
                        }
                    }
                }
                return;
            }
            offset += size;
        }
    }

    /**
     * Vanilla InventoryMenu slot count before our extra slots.
     * result(1) + craft(4) + armor(4) + main-inv(27) + hotbar(9) + shield(1) = 46
     */
    public static final int VANILLA_INVENTORY_MENU_SLOT_COUNT = 46;

    /**
     * Ensure the player has a mutable slots map and return it.
     * The returned map is owned by this call — mutate it and then re-attach.
     */
    public Map<String, List<ItemStack>> getMutableSlots(ServerPlayer player) {
        Map<String, List<ItemStack>> existing = player.getAttached(EXTRA_SLOTS);
        if (existing == null) return new HashMap<>();
        // Return a mutable copy (the attachment may return an unmodifiable view)
        Map<String, List<ItemStack>> mutable = new HashMap<>();
        for (var e : existing.entrySet()) {
            mutable.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return mutable;
    }

    /**
     * Ensure the player's attachment has correctly-sized lists for every registration.
     * Called from the server-side mixin after adding slots so the backing store is ready.
     */
    public void ensureSlotLists(ServerPlayer player) {
        Map<String, List<ItemStack>> map = getMutableSlots(player);
        boolean changed = false;
        for (SlotRegistration reg : registrations) {
            String key = reg.namespace().toString();
            List<ItemStack> list = map.get(key);
            if (list == null) {
                list = new ArrayList<>(Collections.nCopies(reg.slots().size(), ItemStack.EMPTY));
                map.put(key, list);
                changed = true;
            } else if (list.size() < reg.slots().size()) {
                while (list.size() < reg.slots().size()) list.add(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            player.setAttached(EXTRA_SLOTS, map);
        }
    }
}
