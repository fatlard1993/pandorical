package justfatlard.pandorical.mixin;

import java.util.List;

import justfatlard.pandorical.entitykind.EntityKinds;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An entity with its own body is paired with every value it syncs. The game sends only what has
 * moved off its default, which the client knows from its own class; the generic class a client
 * builds a body as knows nothing of the entity's defaults, and would read a value never sent as
 * nothing at all.
 */
@Mixin(SynchedEntityData.class)
public abstract class EntityBodyDataMixin {

	@Shadow @Final private SyncedDataHolder entity;

	@Inject(method = "getNonDefaultValues", at = @At("HEAD"), cancellable = true)
	private void pandorical$everyValueForABody(CallbackInfoReturnable<List<SynchedEntityData.DataValue<?>>> cir) {
		if (this.entity instanceof Entity e && EntityKinds.isBody(e.getType())) {
			cir.setReturnValue(EntityKinds.allValues((SynchedEntityData) (Object) this));
		}
	}
}
