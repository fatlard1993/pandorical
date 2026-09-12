package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.content.StructureInterpolationHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Vanilla never moves a cushion, so its position updates snap. A cushion carried on a moving
 * structure must blend like the structure or its rider leads the deck by a tick.
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
