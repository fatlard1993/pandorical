package justfatlard.pandorical.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import justfatlard.pandorical.drops.DropsPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dropped stacks merge from further apart, but never through a block. See DropsPolicy. */
@Mixin(ItemEntity.class)
public abstract class ItemMergeRadiusMixin {

	/** Widens vanilla's box rather than replacing the search, which Lithium redirects. */
	@ModifyExpressionValue(method = "mergeWithNeighbours", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/phys/AABB;inflate(DDD)Lnet/minecraft/world/phys/AABB;"))
	private AABB pandorical$widerReach(AABB reach) {
		if (!(((ItemEntity) (Object) this).level() instanceof ServerLevel level)) return reach;
		int tenths = DropsPolicy.mergeRadius(level.getServer());
		if (tenths <= DropsPolicy.VANILLA_MERGE_TENTHS) return reach;
		double more = (tenths - DropsPolicy.VANILLA_MERGE_TENTHS) / 10.0;
		return reach.inflate(more, 0, more);
	}

	@Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
	private void pandorical$notThroughBlocks(ItemEntity other, CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (!(self.level() instanceof ServerLevel level) || !DropsPolicy.widerMerge(level.getServer())) return;
		if (!ItemEntity.areMergable(self.getItem(), other.getItem())) return;
		ClipContext between = new ClipContext(self.getBoundingBox().getCenter(), other.getBoundingBox().getCenter(),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self);
		if (level.clip(between).getType() != HitResult.Type.MISS) ci.cancel();
	}
}
