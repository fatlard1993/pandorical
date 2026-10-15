package justfatlard.pandorical.client.mixin;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A vanilla type's own constructor, to build its class for a registered type standing on it. */
@Mixin(EntityType.class)
public interface EntityTypeAccessor {
	@Accessor("factory")
	EntityType.EntityFactory<?> pandorical$factory();
}
