package justfatlard.pandorical.mixin;

import justfatlard.pandorical.rail.RailCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Vanilla's rails offer the floor {@link RailCollision} describes, on both sides. The server
 * holds a player up with it and the client predicts the same, which is the whole of why the
 * rule is synced rather than server-side alone.
 */
@Mixin(BaseRailBlock.class)
public abstract class RailCollisionMixin extends Block {
	protected RailCollisionMixin(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		VoxelShape floor = RailCollision.shapeFor(state, context);
		return floor != null ? floor : super.getCollisionShape(state, level, pos, context);
	}
}
