package justfatlard.pandorical.screen;

import justfatlard.pandorical.protocol.OpenScreenS2C;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Dynamic container menu for Pandorical screens.
 *
 * Two construction paths:
 * - Client: PandoricalMenu(int, Inventory) via MenuType factory — creates max slots, screen def set later
 * - Server: PandoricalMenu(MenuType, int, Inventory, Container, readOnlySlots, screenDef)
 */
public class PandoricalMenu extends AbstractContainerMenu {
    // Max slots we'll ever need (9x6 mod slots + 36 player inv)
    private static final int MAX_MOD_SLOTS = 54;

    private OpenScreenS2C screenDef;
    private final Container modContainer;
    private final Set<Integer> readOnlySlots;
    private Runnable slotChangeCallback;
    private Runnable removedCallback;

    /**
     * Client constructor — called by MenuType factory.
     * Creates max slots backed by a temp container. Vanilla sync will populate them.
     * The actual slot count and positions come from screenDef set later.
     */
    public PandoricalMenu(int syncId, Inventory playerInventory) {
        super(justfatlard.pandorical.Pandorical.MENU_TYPE, syncId);
        this.modContainer = new SimpleContainer(MAX_MOD_SLOTS);
        this.readOnlySlots = Set.of();

        // Create max mod slots — vanilla will sync the ones the server actually uses
        for (int i = 0; i < MAX_MOD_SLOTS; i++) {
            this.addSlot(new PandoricalSlot(modContainer, i, -1000, -1000, true));
        }

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, -1000, -1000));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, -1000, -1000));
        }
    }

    /**
     * Server constructor — called by Pandorical.createMenu().
     */
    public PandoricalMenu(MenuType<?> menuType, int syncId, Inventory playerInventory,
                          Container serverContainer, Set<Integer> readOnlySlots, OpenScreenS2C screenDef) {
        super(menuType, syncId);
        this.screenDef = screenDef;
        this.modContainer = serverContainer;
        this.readOnlySlots = readOnlySlots != null ? readOnlySlots : Set.of();

        int slotCount = screenDef.container().map(c -> c.slotCount()).orElse(0);
        boolean includePlayerInv = screenDef.container().map(c -> c.includePlayerInventory()).orElse(false);

        // Mod slots backed by the server container
        for (int i = 0; i < slotCount; i++) {
            boolean editable = !this.readOnlySlots.contains(i);
            this.addSlot(new PandoricalSlot(modContainer, i, -1000, -1000, editable));
        }

        if (includePlayerInv) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, -1000, -1000));
                }
            }
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col, -1000, -1000));
            }
        }
    }

    /**
     * Set screen definition on client side. Called after the pending def is received.
     */
    public void setScreenDef(OpenScreenS2C screenDef) {
        this.screenDef = screenDef;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) return ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int modSlotCount = screenDef != null ? screenDef.container().map(c -> c.slotCount()).orElse(0) : MAX_MOD_SLOTS;

        if (slotIndex < modSlotCount) {
            if (readOnlySlots.contains(slotIndex)) return ItemStack.EMPTY;
            if (!this.moveItemStackTo(stack, modSlotCount, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.moveItemStackTo(stack, 0, modSlotCount, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (slotChangeCallback != null) slotChangeCallback.run();
        return original;
    }

    @Override
    public void clicked(int slotIndex, int button, net.minecraft.world.inventory.ContainerInput actionType, Player player) {
        if (slotIndex >= 0 && slotIndex < this.slots.size() && readOnlySlots.contains(slotIndex)) {
            return;
        }
        super.clicked(slotIndex, button, actionType, player);
        if (slotChangeCallback != null) slotChangeCallback.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (removedCallback != null) removedCallback.run();
    }

    public OpenScreenS2C getScreenDef() { return screenDef; }
    public Container getModContainer() { return modContainer; }

    public void setSlotChangeCallback(Runnable callback) { this.slotChangeCallback = callback; }
    public void setRemovedCallback(Runnable callback) { this.removedCallback = callback; }

    public void repositionSlot(int slotIndex, int x, int y) {
        if (slotIndex >= 0 && slotIndex < this.slots.size()) {
            ((IMutableSlot) this.slots.get(slotIndex)).pandorical$setPosition(x, y);
        }
    }

    public static class PandoricalSlot extends Slot {
        private final boolean editable;

        public PandoricalSlot(Container container, int index, int x, int y, boolean editable) {
            super(container, index, x, y);
            this.editable = editable;
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return editable; }

        @Override
        public boolean mayPickup(Player player) { return editable; }
    }
}
