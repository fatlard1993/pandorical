package justfatlard.pandorical.screen;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.OpenScreenS2C;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Set;
import java.util.function.IntSupplier;

public class PandoricalMenu extends AbstractContainerMenu {
    private static final int MAX_MOD_SLOTS = 54;

    private OpenScreenS2C screenDef;
    private final Container modContainer;
    private final Set<Integer> readOnlySlots;
    private Runnable slotChangeCallback;
    private Runnable removedCallback;

    /** Client only: the slot count of the definition sent just before the menu; negative if unknown. */
    private static IntSupplier incomingModSlots = () -> -1;

    public static void setIncomingModSlots(IntSupplier supplier) {
        incomingModSlots = supplier == null ? () -> -1 : supplier;
    }

    /**
     * Client side. The slot count must match the server's exactly: slots are addressed by index,
     * and the player slots here are backed by the real {@link Inventory}, so a mismatch writes the
     * server's slots into the player's own inventory.
     */
    public PandoricalMenu(int syncId, Inventory playerInventory) {
        super(Pandorical.MENU_TYPE, syncId);
        int declared = incomingModSlots.getAsInt();
        int modSlots = declared < 0 ? MAX_MOD_SLOTS : Math.min(declared, MAX_MOD_SLOTS);

        this.modContainer = new KeepsWhatItIsGiven(Math.max(modSlots, 1));
        this.readOnlySlots = Set.of();

        for (int i = 0; i < modSlots; i++) {
            this.addSlot(new PandoricalSlot(modContainer, i, -1000, -1000, true));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, -1000, -1000));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, -1000, -1000));
        }
    }

    private boolean ranContainerLifecycle = false;

    public PandoricalMenu(MenuType<?> menuType, int syncId, Inventory playerInventory,
                          Container serverContainer, Set<Integer> readOnlySlots, OpenScreenS2C screenDef) {
        super(menuType, syncId);
        this.screenDef = screenDef;
        this.modContainer = serverContainer;
        this.readOnlySlots = readOnlySlots != null ? readOnlySlots : Set.of();

        // A mod's container is told it was opened and closed; a block's own is not. Vanilla
        // recounts a chest's openers through menus it recognises, which this is not, and the lid
        // count would fight itself.
        if (playerInventory.player instanceof ServerPlayer opener
                && serverContainer != null && !ownsItsOwnLid(serverContainer)) {
            serverContainer.startOpen(opener);
            this.ranContainerLifecycle = true;
        }

        int slotCount = screenDef.container().map(c -> c.slotCount()).orElse(0);
        boolean includePlayerInv = screenDef.container().map(c -> c.includePlayerInventory()).orElse(false);

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
            if (!moveIntoEditableModSlots(stack, modSlotCount)) {
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

    // Vanilla's moveItemStackTo consults Slot#mayPlace only on its empty-slot pass, not its merge
    // pass, so moving across the whole range would top off stacks in read-only slots.
    private boolean moveIntoEditableModSlots(ItemStack stack, int modSlotCount) {
        boolean moved = false;
        int i = 0;
        while (i < modSlotCount && !stack.isEmpty()) {
            if (readOnlySlots.contains(i)) { i++; continue; }
            int start = i;
            while (i < modSlotCount && !readOnlySlots.contains(i)) i++;
            if (this.moveItemStackTo(stack, start, i, false)) moved = true;
        }
        return moved;
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        if (slotIndex >= 0 && slotIndex < this.slots.size() && readOnlySlots.contains(slotIndex)) {
            return;
        }
        super.clicked(slotIndex, button, actionType, player);
        if (slotChangeCallback != null) slotChangeCallback.run();
    }

    /** A double chest arrives as a {@link CompoundContainer}, which is not a block entity. */
    private static boolean ownsItsOwnLid(Container container) {
        return container instanceof BlockEntity || container instanceof CompoundContainer;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        if (this.ranContainerLifecycle && player instanceof ServerPlayer closer) {
            modContainer.stopOpen(closer);
        }

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

    /**
     * {@code SimpleContainer.setItem} trims a stack to {@code getMaxStackSize}, and a client has
     * vanilla's limits even where the server lifts them, so this keeps the server's count as sent.
     */
    private static class KeepsWhatItIsGiven extends SimpleContainer {
        /** Not {@link Integer#MAX_VALUE}: vanilla multiplies a stack limit by a hundred in places. */
        private static final int NO_LIMIT = Integer.MAX_VALUE / 100;

        KeepsWhatItIsGiven(int size) {
            super(size);
        }

        @Override
        public int getMaxStackSize() {
            return NO_LIMIT;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return NO_LIMIT;
        }
    }

    public static class PandoricalSlot extends Slot {
        private final boolean editable;

        public PandoricalSlot(Container container, int index, int x, int y, boolean editable) {
            super(container, index, x, y);
            this.editable = editable;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return editable && this.container.canPlaceItem(this.getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) { return editable; }
    }
}
