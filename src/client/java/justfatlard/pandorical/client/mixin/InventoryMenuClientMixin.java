package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.inventory.ClientInventorySlotRegistry;
import justfatlard.pandorical.protocol.PlayerInventoryRegistrationsS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.SimpleContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side mixin that mirrors the extra inventory slots added by the server-side
 * {@code InventoryMenuMixin}. The client uses a dummy {@link SimpleContainer} as the backing
 * store; actual item content is populated automatically by vanilla's slot synchronisation
 * ({@code ClientboundContainerSetSlotPacket}).
 *
 * <p>Only activates when the owning player is a {@link LocalPlayer}. This prevents any
 * interference with integrated-server {@link net.minecraft.server.level.ServerPlayer}
 * instances that also call the same constructor on the logical-server side.
 */
@Environment(EnvType.CLIENT)
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuClientMixin extends AbstractContainerMenu {

    protected InventoryMenuClientMixin() {
        super(null, 0);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void pandorical$addClientExtraSlots(Inventory playerInventory, boolean active, Player player,
                                                CallbackInfo ci) {
        // Only run for the local player on the client.
        if (!(player instanceof LocalPlayer)) return;

        for (PlayerInventoryRegistrationsS2C.SlotGroup group : ClientInventorySlotRegistry.getGroups()) {
            int size = group.slots().size();
            if (size == 0) continue;

            // Dummy container — content is synced from the server automatically.
            SimpleContainer dummyContainer = new SimpleContainer(size);

            for (PlayerInventoryRegistrationsS2C.SlotPosition pos : group.slots()) {
                this.addSlot(new Slot(dummyContainer, pos.slotIndex(), pos.screenX(), pos.screenY()));
            }
        }
    }
}
