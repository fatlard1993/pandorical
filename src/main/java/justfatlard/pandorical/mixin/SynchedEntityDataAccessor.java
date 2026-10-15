package justfatlard.pandorical.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How many values an entity syncs, to hold a registered entity to its base's layout. */
@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessor {
	@Accessor("itemsById")
	SynchedEntityData.DataItem<?>[] pandorical$itemsById();
}
