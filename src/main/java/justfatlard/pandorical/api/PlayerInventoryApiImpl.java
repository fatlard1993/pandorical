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

public final class PlayerInventoryApiImpl implements PlayerInventoryApi {

    private final List<SlotRegistration> registrations = new ArrayList<>();

    private final Map<String, List<BiConsumer<ServerPlayer, SlotChangeEvent>>> listeners =
        new HashMap<>();

    private static final Codec<Map<String, List<ItemStack>>> SLOTS_CODEC =
        Codec.unboundedMap(
            Codec.STRING,
            ItemStack.OPTIONAL_CODEC.listOf()
        );

    public static final AttachmentType<Map<String, List<ItemStack>>> EXTRA_SLOTS =
        AttachmentRegistry.<Map<String, List<ItemStack>>>builder()
            .persistent(SLOTS_CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier.fromNamespaceAndPath(Pandorical.MOD_ID, "extra_slots"));

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

    public List<InventoryButtonsS2C.Button> declaredButtons() {
        return List.copyOf(buttons);
    }

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

    public void forgetButtonGlyphs(UUID player) {
        glyphs.remove(player);
    }

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
        registrations.add(new SlotRegistration(namespace, List.copyOf(slots)));
        Pandorical.LOGGER.info("[pandorical] Registered {} extra inventory slot(s) for namespace '{}'",
            slots.size(), namespace);
    }

    /**
     * The menu copies the attachment when the {@code ServerPlayer} is constructed, before saved
     * data loads, so on a fresh join its copy is empty. Call on JOIN, after the load; it writes
     * through the menu slots so listeners fire and {@code broadcastChanges} reaches the client.
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
        // Through the menu where there is one: its slots answer from a copy taken when it was
        // built, and its container writes back to the attachment and fires the listeners.
        int menuSlot = menuSlotOf(namespace, slotIndex);
        if (menuSlot >= 0 && player.inventoryMenu != null && menuSlot < player.inventoryMenu.slots.size()) {
            player.inventoryMenu.getSlot(menuSlot).set(stack == null ? ItemStack.EMPTY : stack);
            player.inventoryMenu.broadcastChanges();
            return;
        }

        Map<String, List<ItemStack>> map = getMutableSlots(player);
        String key = namespace.toString();
        List<ItemStack> list = map.computeIfAbsent(key, k -> {
            int size = registrations.stream()
                .filter(r -> r.namespace().equals(namespace))
                .mapToInt(r -> r.slots().size())
                .findFirst()
                .orElse(slotIndex + 1);
            return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
        });
        while (list.size() <= slotIndex) list.add(ItemStack.EMPTY);
        list.set(slotIndex, stack == null ? ItemStack.EMPTY : stack);

        player.setAttached(EXTRA_SLOTS, map);

        player.inventoryMenu.broadcastChanges();
    
        notifyListeners(player, namespace, slotIndex, stack);
    }

    @Override
    public void onSlotChange(Identifier namespace, BiConsumer<ServerPlayer, SlotChangeEvent> handler) {
        listeners.computeIfAbsent(namespace.toString(), k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public List<SlotRegistration> registeredSlots() {
        return getRegistrations();
    }

    public List<SlotRegistration> getRegistrations() {
        return Collections.unmodifiableList(registrations);
    }

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

    /** Slots a notification is in flight for, so a listener writing back does not recurse. */
    private final Set<String> notifying =
        Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void fireSlotChangeListeners(ServerPlayer player, int menuSlotIndex, ItemStack newStack) {
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

    /** Result 1, craft 4, armor 4, main 27, hotbar 9, shield 1; the extra slots follow. */
    public static final int VANILLA_INVENTORY_MENU_SLOT_COUNT = 46;

    /** A mutable copy: changes reach the player only when re-attached. */
    public Map<String, List<ItemStack>> getMutableSlots(ServerPlayer player) {
        Map<String, List<ItemStack>> existing = player.getAttached(EXTRA_SLOTS);
        if (existing == null) return new HashMap<>();
        Map<String, List<ItemStack>> mutable = new HashMap<>();
        for (var e : existing.entrySet()) {
            mutable.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return mutable;
    }

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
