package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.inventory.ClientInventorySlotRegistry;
import justfatlard.pandorical.protocol.PlayerInventoryRegistrationsS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors the slots the server-side {@code InventoryMenuMixin} adds; vanilla slot sync fills the
 * placeholder containers. {@link LocalPlayer} only: the integrated server runs this constructor
 * for its {@code ServerPlayer} too.
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
        if (!(player instanceof LocalPlayer)) return;

        for (PlayerInventoryRegistrationsS2C.SlotGroup group : ClientInventorySlotRegistry.getGroups()) {
            int size = group.slots().size();
            if (size == 0) continue;

            SimpleContainer dummyContainer = new SimpleContainer(size);

            for (PlayerInventoryRegistrationsS2C.SlotPosition pos : group.slots()) {
                this.addSlot(new Slot(dummyContainer, pos.slotIndex(), pos.screenX(), pos.screenY()));
            }
        }
    }
}
