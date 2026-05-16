package justfatlard.pandorical.api;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.OpenScreenS2C;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Set;

/**
 * MenuProvider that creates a PandoricalMenu with server-side container backing.
 * Used by PandoricalApi.screens().openContainer().
 */
public class PandoricalMenuProvider implements MenuProvider {
    private final OpenScreenS2C screenDef;
    private final Container serverContainer;
    private final Set<Integer> readOnlySlots;
    private final Runnable slotChangeCallback;
    private final Runnable removedCallback;

    public PandoricalMenuProvider(OpenScreenS2C screenDef, Container serverContainer,
                                  Set<Integer> readOnlySlots, Runnable slotChangeCallback,
                                  Runnable removedCallback) {
        this.screenDef = screenDef;
        this.serverContainer = serverContainer;
        this.readOnlySlots = readOnlySlots;
        this.slotChangeCallback = slotChangeCallback;
        this.removedCallback = removedCallback;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(screenDef.title());
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return Pandorical.createMenu(syncId, playerInventory, serverContainer, readOnlySlots,
            screenDef, slotChangeCallback, removedCallback);
    }
}
