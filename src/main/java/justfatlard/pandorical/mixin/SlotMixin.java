package justfatlard.pandorical.mixin;

import justfatlard.pandorical.screen.IMutableSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Slot.class)
public abstract class SlotMixin implements IMutableSlot {

    @Mutable
    @Shadow
    public int x;

    @Mutable
    @Shadow
    public int y;

    @Override
    public void pandorical$setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
