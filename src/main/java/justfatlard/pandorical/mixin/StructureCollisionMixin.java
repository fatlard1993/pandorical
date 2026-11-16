package justfatlard.pandorical.mixin;

import com.google.common.collect.ImmutableList;
import justfatlard.pandorical.structure.StructureRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.function.Predicate;

/**
 * Walkable structures are solid to what the server moves; see {@link StructureRegistry#collisions}.
 *
 * <p>Entity collisions are where movement, stepping up and every "is there room" check ask, so a
 * deck added here is a floor to all of them. ServerLevel inherits this method from an interface
 * default and declares none of its own, so this is an override rather than an injection; the body
 * up to the deck is the default's, unchanged.
 */
@Mixin(ServerLevel.class)
public abstract class StructureCollisionMixin {

	public List<VoxelShape> getEntityCollisions(@Nullable Entity source, AABB testArea) {
		if (testArea.getSize() < 1.0E-7) return List.of();

		Predicate<Entity> canCollide = source == null
			? EntitySelector.CAN_BE_COLLIDED_WITH
			: EntitySelector.NO_SPECTATORS.and(source::canCollideWith);
		Level level = (Level) (Object) this;
		List<Entity> colliding = level.getEntities(source, testArea.inflate(1.0E-7), canCollide);
		List<VoxelShape> decks = StructureRegistry.INSTANCE.collisions(level, source, testArea);
		if (colliding.isEmpty() && decks.isEmpty()) return List.of();

		ImmutableList.Builder<VoxelShape> shapes = ImmutableList.builderWithExpectedSize(colliding.size() + decks.size());
		for (Entity entity : colliding) shapes.add(Shapes.create(entity.getBoundingBox()));
		shapes.addAll(decks);
		return shapes.build();
	}
}
