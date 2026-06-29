package justfatlard.pandorical.mixin;

import justfatlard.pandorical.api.PlayerInventoryApi;
import justfatlard.pandorical.api.PlayerInventoryApiImpl;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Server-side mixin that adds extra inventory slots to {@link InventoryMenu} for
 * each {@link PlayerInventoryApi.SlotRegistration} registered via {@link PandoricalApi#playerInventory()}.
 *
 * <p>Only activates when the owning player is a {@link ServerPlayer} (not creative menu or
 * spectator, which also create InventoryMenu instances). Slots are backed by a
 * lightweight {@link PersistingContainer} whose contents are persisted to the player's
 * Fabric data attachment whenever {@code setChanged()} is called.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    protected InventoryMenuMixin() {
        super(null, 0);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void pandorical$addExtraSlots(Inventory playerInventory, boolean active, Player player,
                                          CallbackInfo ci) {
        // Only run on the server side — LocalPlayer check is in the client mixin.
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        PlayerInventoryApiImpl impl = PandoricalApi.playerInventoryImpl();
        List<PlayerInventoryApi.SlotRegistration> registrations = impl.getRegistrations();
        if (registrations.isEmpty()) return;

        // Ensure the player's attachment has correctly-sized lists.
        impl.ensureSlotLists(serverPlayer);

        for (PlayerInventoryApi.SlotRegistration reg : registrations) {
            List<PlayerInventoryApi.SlotEntry> slotEntries = reg.slots();
            int size = slotEntries.size();
            if (size == 0) continue;

            // Retrieve current items from the attachment so the container is pre-populated.
            Map<String, List<ItemStack>> allSlots = impl.getMutableSlots(serverPlayer);
            List<ItemStack> storedItems = allSlots.getOrDefault(reg.namespace().toString(),
                Collections.emptyList());

            // Build a backing container pre-populated with stored items.
            NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
            for (int i = 0; i < size && i < storedItems.size(); i++) {
                ItemStack stored = storedItems.get(i);
                if (stored != null && !stored.isEmpty()) {
                    items.set(i, stored.copy());
                }
            }

            String namespaceKey = reg.namespace().toString();
            // Record base menu slot index so we can compute the absolute menu slot in the listener.
            int baseMenuSlot = this.slots.size();
            PersistingContainer container = new PersistingContainer(items,
                // onChanged: persist to attachment
                () -> {
                    Map<String, List<ItemStack>> map = impl.getMutableSlots(serverPlayer);
                    List<ItemStack> updated = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        updated.add(items.get(i).copy());
                    }
                    map.put(namespaceKey, updated);
                    serverPlayer.setAttached(PlayerInventoryApiImpl.EXTRA_SLOTS, map);
                },
                // onItemChanged: fire registered slot-change listeners
                (localSlot, newStack) -> impl.fireSlotChangeListeners(
                    serverPlayer, baseMenuSlot + localSlot, newStack)
            );

            // Add Slot objects to the menu at the declared screen positions.
            for (PlayerInventoryApi.SlotEntry entry : slotEntries) {
                int idx = entry.slotIndex();
                PlayerInventoryApi.SlotEntry capturedEntry = entry;
                this.addSlot(new Slot(container, idx, entry.screenX(), entry.screenY()) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return capturedEntry.validator().test(stack);
                    }
                });
            }
        }
    }

    // --- Inner container implementation ---

    /**
     * Minimal {@link Container} backed by a {@link NonNullList} that persists its contents
     * to the player's data attachment every time {@link #setChanged()} is called.
     */
    private static final class PersistingContainer implements Container {
        private final NonNullList<ItemStack> items;
        private final Runnable onChanged;
        private final java.util.function.BiConsumer<Integer, ItemStack> onItemChanged;

        PersistingContainer(NonNullList<ItemStack> items, Runnable onChanged,
                            java.util.function.BiConsumer<Integer, ItemStack> onItemChanged) {
            this.items = items;
            this.onChanged = onChanged;
            this.onItemChanged = onItemChanged;
        }

        @Override public int getContainerSize() { return items.size(); }
        @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getItem(int slot) { return items.get(slot); }
        @Override public ItemStack removeItem(int slot, int amount) {
            ItemStack result = net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
            if (!result.isEmpty()) setChanged();
            return result;
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            return net.minecraft.world.ContainerHelper.takeItem(items, slot);
        }
        @Override public void setItem(int slot, ItemStack stack) {
            items.set(slot, stack == null ? ItemStack.EMPTY : stack);
            if (onItemChanged != null) onItemChanged.accept(slot, items.get(slot));
            setChanged();
        }
        @Override public void setChanged() { onChanged.run(); }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() {
            items.replaceAll(s -> ItemStack.EMPTY);
            setChanged();
        }
    }
}
