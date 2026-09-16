package justfatlard.pandorical.client.mixin;

import com.google.common.collect.ImmutableList;
import justfatlard.pandorical.client.structure.StructureDecks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
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
 * Walkable structures are solid to the local player.
 *
 * <p>Entity collisions are where movement, stepping up, sneaking at an edge and every "is there
 * room" check all ask, so a deck added here is a floor to all of them. ClientLevel inherits this
 * method from an interface default and declares none of its own, so this is an override rather
 * than an injection; the body up to the deck is the default's, unchanged.
 */
@Mixin(ClientLevel.class)
public abstract class StructureDeckCollisionMixin {

	public List<VoxelShape> getEntityCollisions(@Nullable Entity source, AABB testArea) {
		if (testArea.getSize() < 1.0E-7) return List.of();

		Predicate<Entity> canCollide = source == null
			? EntitySelector.CAN_BE_COLLIDED_WITH
			: EntitySelector.NO_SPECTATORS.and(source::canCollideWith);
		List<Entity> colliding = ((Level) (Object) this).getEntities(source, testArea.inflate(1.0E-7), canCollide);
		List<VoxelShape> decks = source instanceof LocalPlayer ? StructureDecks.collisions(testArea) : List.of();
		if (colliding.isEmpty() && decks.isEmpty()) return List.of();

		ImmutableList.Builder<VoxelShape> shapes = ImmutableList.builderWithExpectedSize(colliding.size() + decks.size());
		for (Entity entity : colliding) shapes.add(Shapes.create(entity.getBoundingBox()));
		shapes.addAll(decks);
		return shapes.build();
	}
}
