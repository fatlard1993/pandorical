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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import justfatlard.pandorical.protocol.InventoryButtonsS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

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

    /**
     * Carried across a death, which the default for a persistent attachment is not.
     *
     * <p>A respawn builds a new player rather than loading the old one, so without this the
     * extra slots were simply gone - and gone silently, because a mod keeping its own mirror of
     * a slot (map-plus-plus does) copies the mirror across and then disagrees with the empty
     * store behind it. An equipped map went on driving the minimap while the slot that held it
     * read empty.
     *
     * <p>Emptying these on death, where that is what should happen, is the business of whatever
     * takes the items: dead-heads clears them into the head it leaves behind, and what is copied
     * across is then correctly nothing.
     */
    public static final AttachmentType<Map<String, List<ItemStack>>> EXTRA_SLOTS =
        AttachmentRegistry.<Map<String, List<ItemStack>>>builder()
            .persistent(SLOTS_CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier.fromNamespaceAndPath(Pandorical.MOD_ID, "extra_slots"));

    // --- PlayerInventoryApi implementation ---

    /** Buttons mods have asked for on the inventory screen, in registration order. */
    private final List<InventoryButtonsS2C.Button> buttons =
        new ArrayList<>();

    private final Map<String, Consumer<ServerPlayer>> buttonHandlers =
        new ConcurrentHashMap<>();

    @Override
    public void registerButton(Identifier namespace, String id, int x, int y, int size, String glyph) {
        buttons.add(new InventoryButtonsS2C.Button(
            namespace.toString(), id, x, y, size, glyph));
        Pandorical.LOGGER.info("[pandorical] Registered inventory button '{}' for namespace '{}'",
            id, namespace);
    }

    @Override
    public void onButton(Identifier namespace, String id, Consumer<ServerPlayer> handler) {
        buttonHandlers.put(namespace + "/" + id, handler);
    }

    /**
     * Where this namespace's slot sits in the inventory menu, or -1 if it is not registered.
     *
     * <p>The inverse of the walk {@link #fireSlotChangeListeners} does: extras follow vanilla's
     * own 46, in registration order, each group as long as it declared itself.
     */
    private int menuSlotOf(Identifier namespace, int slotIndex) {
        int offset = 0;
        for (SlotRegistration reg : registrations) {
            if (reg.namespace().equals(namespace)) {
                if (slotIndex < 0 || slotIndex >= reg.slots().size()) return -1;
                return VANILLA_INVENTORY_MENU_SLOT_COUNT + offset + slotIndex;
            }
            offset += reg.slots().size();
        }
        return -1;
    }

    /** Faces a player has been shown instead of the registered one, keyed namespace/id. */
    private final Map<UUID, Map<String, String>> glyphs =
        new ConcurrentHashMap<>();

    @Override
    public void setButtonGlyph(ServerPlayer player, Identifier namespace, String id, String glyph) {
        glyphs.computeIfAbsent(player.getUUID(), key -> new ConcurrentHashMap<>())
            .put(namespace + "/" + id, glyph);

        if (!ServerPlayNetworking.canSend(
                player, InventoryButtonsS2C.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(player,
            new InventoryButtonsS2C(buttonsFor(player.getUUID())));
    }

    /** Everything registered, for the packet sent during configuration. */
    public List<InventoryButtonsS2C.Button> declaredButtons() {
        return List.copyOf(buttons);
    }

    /** The same buttons, wearing whatever faces this player has been switched to. */
    public List<InventoryButtonsS2C.Button> buttonsFor(UUID player) {
        Map<String, String> mine = glyphs.get(player);
        if (mine == null || mine.isEmpty()) return declaredButtons();

        List<InventoryButtonsS2C.Button> shown =
            new ArrayList<>(buttons.size());
        for (var button : buttons) {
            String glyph = mine.get(button.namespace() + "/" + button.id());
            shown.add(glyph == null ? button
                : new InventoryButtonsS2C.Button(
                    button.namespace(), button.id(), button.screenX(), button.screenY(),
                    button.size(), glyph));
        }
        return List.copyOf(shown);
    }

    /** Dropped on disconnect: whoever set them will set them again on the next join. */
    public void forgetButtonGlyphs(UUID player) {
        glyphs.remove(player);
    }

    /** Route a press back to whoever asked for the button. */
    public void handleButton(ServerPlayer player, String namespace, String id) {
        var handler = buttonHandlers.get(namespace + "/" + id);
        if (handler == null) return;
        try {
            handler.accept(player);
        } catch (Exception e) {
            Pandorical.LOGGER.error("[pandorical] Exception in inventory button '{}/{}': {}",
                namespace, id, e.getMessage(), e);
        }
    }

    @Override
    public void registerSlots(Identifier namespace, List<SlotEntry> slots) {
        // Defensive copy so callers cannot mutate after registration.
        registrations.add(new SlotRegistration(namespace, List.copyOf(slots)));
        Pandorical.LOGGER.info("[pandorical] Registered {} extra inventory slot(s) for namespace '{}'",
            slots.size(), namespace);
    }

    /**
     * Re-reads the persisted attachment into the live inventory menu.
     *
     * <p>The menu copies the attachment out when the {@code ServerPlayer} is constructed -
     * which is before the player's saved data has loaded, so on a fresh join the copy is
     * always empty. The attachment catches up when the NBT loads, but nothing pushed it
     * back into the menu, and the client faithfully mirrored the stale empty copy: the
     * minimap (fed from mod-side state) showed an equipped map in a slot the player could
     * see was empty. Called on JOIN, after the load, writing through the menu slot so the
     * change listeners fire and {@code broadcastChanges} carries it to the client.
     */
    public void syncMenuFromAttachment(ServerPlayer player) {
        Map<String, List<ItemStack>> map = player.getAttached(EXTRA_SLOTS);
        if (map == null) return;

        var menu = player.inventoryMenu;
        boolean changed = false;
        for (SlotRegistration reg : registrations) {
            List<ItemStack> stored = map.get(reg.namespace().toString());
            if (stored == null) continue;
            for (int i = 0; i < reg.slots().size() && i < stored.size(); i++) {
                int menuSlot = menuSlotOf(reg.namespace(), i);
                if (menuSlot < 0 || menuSlot >= menu.slots.size()) continue;
                ItemStack want = stored.get(i) != null ? stored.get(i) : ItemStack.EMPTY;
                if (ItemStack.matches(want, menu.getSlot(menuSlot).getItem())) continue;
                menu.getSlot(menuSlot).set(want.copy());
                changed = true;
            }
        }
        if (changed) menu.broadcastChanges();
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
        // Through the open menu where there is one, because the menu does not read this store -
        // it copies out of it when it is built and its slots answer from that copy ever after.
        // Writing the store alone left the two disagreeing: broadcastChanges asks each slot
        // whether it has changed, the slot answered from the stale copy and said no, and nothing
        // was sent. A death compass handed to the compass slot drove the minimap from a square
        // the player could see was empty, until a relog rebuilt the menu and it appeared.
        //
        // The menu's own container writes back here and fires the listeners on the way, so this
        // is the same path a player dragging an item into the slot takes.
        int menuSlot = menuSlotOf(namespace, slotIndex);
        if (menuSlot >= 0 && player.inventoryMenu != null && menuSlot < player.inventoryMenu.slots.size()) {
            player.inventoryMenu.getSlot(menuSlot).set(stack == null ? ItemStack.EMPTY : stack);
            player.inventoryMenu.broadcastChanges();
            return;
        }

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
    
        notifyListeners(player, namespace, slotIndex, stack);
    }

    @Override
    public void onSlotChange(Identifier namespace, BiConsumer<ServerPlayer, SlotChangeEvent> handler) {
        listeners.computeIfAbsent(namespace.toString(), k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    // --- Package-private helpers used by the InventoryMenuMixin ---

    @Override
    public List<SlotRegistration> registeredSlots() {
        return getRegistrations();
    }

    /** All registrations, in order. Called by the server-side mixin. */
    public List<SlotRegistration> getRegistrations() {
        return Collections.unmodifiableList(registrations);
    }

    /**
     * Tell this namespace's listeners what a slot now holds.
     *
     * <p>Guarded against a listener that writes back into the slot it was told about: without
     * that, a mirror kept in step by one of these would answer its own notification for ever.
     */
    private void notifyListeners(ServerPlayer player, Identifier namespace, int slotIndex,
            ItemStack newStack) {
        String key = namespace.toString();
        String guard = player.getUUID() + "/" + key + "/" + slotIndex;
        if (!notifying.add(guard)) return;

        try {
            List<BiConsumer<ServerPlayer, SlotChangeEvent>> handlers = listeners.get(key);
            if (handlers == null) return;

            SlotChangeEvent event = new SlotChangeEvent(slotIndex, newStack.copy());
            for (BiConsumer<ServerPlayer, SlotChangeEvent> h : handlers) {
                try {
                    h.accept(player, event);
                } catch (Exception e) {
                    Pandorical.LOGGER.error("[pandorical] Exception in slot-change listener for namespace '{}': {}",
                        key, e.getMessage(), e);
                }
            }
        } finally {
            notifying.remove(guard);
        }
    }

    /** Slots a notification is already in flight for. See {@link #notifyListeners}. */
    private final Set<String> notifying =
        Collections.newSetFromMap(new ConcurrentHashMap<>());

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
     * The returned map is owned by this call: mutate it and then re-attach.
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
