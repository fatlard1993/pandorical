package justfatlard.pandorical.client.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Which of a body's values its class has already defined, so the rest can be. */
@Mixin(SynchedEntityData.Builder.class)
public interface SynchedEntityDataBuilderAccessor {
	@Accessor("itemsById")
	SynchedEntityData.DataItem<?>[] pandorical$itemsById();
}
