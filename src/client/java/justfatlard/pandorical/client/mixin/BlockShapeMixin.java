package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.renderer.ClientBlockShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A position the server gave a shape of its own is that shape on this client: walked against,
 * stood on and aimed at. The integrated server runs in this same process and asks these too, so
 * only the client's own thread is answered. Air is never given one: a block this client has
 * already predicted broken must not stay solid until the server's word arrives.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockShapeMixin {
	@Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("HEAD"), cancellable = true)
	private void pandorical$shape(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		pandorical$answer(this, pos, cir);
	}

	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("HEAD"), cancellable = true)
	private void pandorical$collision(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		pandorical$answer(this, pos, cir);
	}

	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("HEAD"), cancellable = true)
	private void pandorical$plainCollision(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
		pandorical$answer(this, pos, cir);
	}

	private static void pandorical$answer(Object self, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
		if (!ClientBlockShapes.any || ((BlockBehaviour.BlockStateBase) self).isAir()) return;
		VoxelShape shape = ClientBlockShapes.at(pos);
		if (shape != null) cir.setReturnValue(shape);
	}
}
