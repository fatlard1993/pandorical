package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.content.StructureInterpolationHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A cushion blends between positions the way a structure does.
 *
 * <p>Vanilla never moves a cushion, so it never blends: a position update snaps it. Big-boats
 * carries cushions on a sailing deck and pushes their position every tick, and a cushion that
 * snaps while the deck blends has its rider a tick ahead of the boards under them. A cushion
 * that never moves is unaffected; it has nothing to blend towards.
 */
@Mixin(Cushion.class)
public abstract class CushionInterpolationMixin extends Entity {
	protected CushionInterpolationMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return new StructureInterpolationHandler(this);
	}
}
