package justfatlard.pandorical.screen;

import justfatlard.pandorical.protocol.OpenScreenS2C;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.function.IntSupplier;
import justfatlard.pandorical.Pandorical;
import net.minecraft.world.inventory.ContainerInput;

/**
 * Dynamic container menu for Pandorical screens.
 *
 * Two construction paths:
 * - Client: PandoricalMenu(int, Inventory) via MenuType factory: creates max slots, screen def set later
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
     * How many mod slots the menu about to be built will have.
     *
     * <p>Answered on the client by the screen definition, which is always sent immediately
     * before the menu is opened. Left alone on the server, where the count is passed in
     * directly. A negative answer means nobody knows, and the constructor falls back.
     */
    private static IntSupplier incomingModSlots = () -> -1;

    /** Installed once by the client, which is the only side that can see the pending definition. */
    public static void setIncomingModSlots(IntSupplier supplier) {
        incomingModSlots = supplier == null ? () -> -1 : supplier;
    }

    /**
     * Client constructor, called by the MenuType factory.
     *
     * <p>The slot count has to match the server's exactly. Vanilla addresses slots by index
     * and nothing reconciles the two lists: with a different number of mod slots here, every
     * index the server sends lands on the wrong slot, and since the player inventory slots on
     * this side are backed by the real {@link Inventory}, the contents of the server's slots
     * get written straight into it. That is not a display fault - opening a container with a
     * mismatched count rearranges the player's own inventory.
     *
     * <p>So the count comes from the screen definition, which openContainer sends before it
     * opens the menu. Only when there is no definition to read does this fall back to the
     * old fixed maximum.
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

    /** Whether this menu told its container it was opened, so it knows to say when it closes. */
    private boolean ranContainerLifecycle = false;

    public PandoricalMenu(MenuType<?> menuType, int syncId, Inventory playerInventory,
                          Container serverContainer, Set<Integer> readOnlySlots, OpenScreenS2C screenDef) {
        super(menuType, syncId);
        this.screenDef = screenDef;
        this.modContainer = serverContainer;
        this.readOnlySlots = readOnlySlots != null ? readOnlySlots : Set.of();

        // A container a mod built is entitled to know it has been opened.
        //
        // Containers rely on this: loot-ender notices a copy came back empty here, and it is
        // where a mod gets to draw a conclusion about what is left. Wrapping somebody's
        // Container without running its lifecycle skipped all of it, so a loot chest emptied
        // through one of these screens was never marked spent and its clasp stayed bright.
        //
        // Never where the container is a block's own, though. A chest's opener count is
        // vanilla's, and vanilla keeps it honest by periodically recounting the players it can
        // see holding that container open - through menus it recognises, which this is not.
        // Telling it we opened a chest it then cannot find an opener for leaves the count
        // fighting itself, and the lid ends up showing the opposite of the truth. Whatever owns
        // the block owns its lid.
        if (playerInventory.player instanceof ServerPlayer opener
                && serverContainer != null && !ownsItsOwnLid(serverContainer)) {
            serverContainer.startOpen(opener);
            this.ranContainerLifecycle = true;
        }

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

    // Shift-click from inventory into the mod container. Vanilla's moveItemStackTo only
    // consults Slot#mayPlace on its empty-slot pass, not its stack-merge pass, so a single
    // moveItemStackTo(stack, 0, modSlotCount) would let a merge top off an existing stack in a
    // read-only slot. Move only across the editable sub-ranges so read-only slots are never a
    // merge or place target.
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

    /**
     * Whether this container belongs to a block that is already keeping its own open state.
     *
     * <p>Testing for a {@link BlockEntity} alone is the single-chest half of the question. A
     * DOUBLE chest is handed over as a {@link CompoundContainer} wrapping the two halves, which is
     * not a block entity and sailed straight past that test into vanilla's opener count - so
     * double chests opened through a Pandorical screen had their lids driven by a count vanilla
     * could not see an opener for, and left them standing open.
     */
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

        // The other half, and only where we ran the first half.
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
     * The client's copy of the mod's slots, which keeps whatever the server put in them.
     *
     * <p>{@code SimpleContainer.setItem} ends with {@code stack.limitSize(getMaxStackSize(stack))},
     * and on a client the answer to that is sixty-four: the mod that lifts stack limits runs on
     * the server only, so the client has vanilla's numbers. Every oversized stack the server sent
     * was therefore trimmed the moment it arrived, and a slot holding a hundred and ten of
     * something drew as sixty-four while the screen's own text, computed server-side, correctly
     * said a hundred and ten.
     *
     * <p>Nothing is decided here - the server owns what is in its container - so the honest thing
     * for this side to do is carry the number across unaltered rather than second-guess it with a
     * limit it is not the authority on.
     */
    private static class KeepsWhatItIsGiven extends SimpleContainer {
        /**
         * Not {@link Integer#MAX_VALUE}: vanilla multiplies a stack limit by a hundred in places
         * and that overflows into a negative. A hundredth of it is still past any real stack.
         */
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

        /**
         * Read-only slots refuse everything; the rest ask the container.
         *
         * <p>Vanilla's own slot delegates to {@code canPlaceItem} and this one did not, so a mod
         * handing over a Container had no way to refuse an item short of taking it back
         * afterwards. Anything that wants to be picky - a builder's table that only accepts what
         * the next build needs - can just say so now, the way it would to any other menu.
         */
        @Override
        public boolean mayPlace(ItemStack stack) {
            return editable && this.container.canPlaceItem(this.getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) { return editable; }
    }
}
