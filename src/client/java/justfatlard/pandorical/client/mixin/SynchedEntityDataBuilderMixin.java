package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import justfatlard.pandorical.client.entitykind.BodyData;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A body's data has room for every value the server says it syncs. The game sizes it by the
 * values its class declares, which for a generic mob are only its own class's.
 */
@Mixin(SynchedEntityData.Builder.class)
public abstract class SynchedEntityDataBuilderMixin {

	@ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/util/ClassTreeIdRegistry;getCount(Ljava/lang/Class;)I"))
	private int pandorical$roomForABody(int count, @Local(argsOnly = true) SyncedDataHolder entity) {
		return BodyData.count(entity, count);
	}
}
