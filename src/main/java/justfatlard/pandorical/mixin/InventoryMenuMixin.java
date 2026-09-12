package justfatlard.pandorical.mixin;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.PlayerInventoryApi;
import justfatlard.pandorical.api.PlayerInventoryApiImpl;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
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
import java.util.function.BiConsumer;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    protected InventoryMenuMixin() {
        super(null, 0);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void pandorical$addExtraSlots(Inventory playerInventory, boolean active, Player player,
                                          CallbackInfo ci) {
        // The client's slots are added by its own mixin.
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        PlayerInventoryApiImpl impl = PandoricalApi.playerInventoryImpl();
        List<PlayerInventoryApi.SlotRegistration> registrations = impl.getRegistrations();
        if (registrations.isEmpty()) return;

        impl.ensureSlotLists(serverPlayer);

        for (PlayerInventoryApi.SlotRegistration reg : registrations) {
            List<PlayerInventoryApi.SlotEntry> slotEntries = reg.slots();
            int size = slotEntries.size();
            if (size == 0) continue;

            Map<String, List<ItemStack>> allSlots = impl.getMutableSlots(serverPlayer);
            List<ItemStack> storedItems = allSlots.getOrDefault(reg.namespace().toString(),
                Collections.emptyList());

            NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
            for (int i = 0; i < size && i < storedItems.size(); i++) {
                ItemStack stored = storedItems.get(i);
                if (stored != null && !stored.isEmpty()) {
                    items.set(i, stored.copy());
                }
            }

            String namespaceKey = reg.namespace().toString();
            int baseMenuSlot = this.slots.size();
            PersistingContainer container = new PersistingContainer(items,
                () -> {
                    Map<String, List<ItemStack>> map = impl.getMutableSlots(serverPlayer);
                    List<ItemStack> updated = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        updated.add(items.get(i).copy());
                    }
                    map.put(namespaceKey, updated);
                    serverPlayer.setAttached(PlayerInventoryApiImpl.EXTRA_SLOTS, map);
                },
                (localSlot, newStack) -> impl.fireSlotChangeListeners(
                    serverPlayer, baseMenuSlot + localSlot, newStack)
            );

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

    private static final class PersistingContainer implements Container {
        private final NonNullList<ItemStack> items;
        private final Runnable onChanged;
        private final BiConsumer<Integer, ItemStack> onItemChanged;

        PersistingContainer(NonNullList<ItemStack> items, Runnable onChanged,
                            BiConsumer<Integer, ItemStack> onItemChanged) {
            this.items = items;
            this.onChanged = onChanged;
            this.onItemChanged = onItemChanged;
        }

        @Override public int getContainerSize() { return items.size(); }
        @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getItem(int slot) { return items.get(slot); }
        @Override public ItemStack removeItem(int slot, int amount) {
            ItemStack result = ContainerHelper.removeItem(items, slot, amount);
            if (!result.isEmpty()) setChanged();
            return result;
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(items, slot);
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
