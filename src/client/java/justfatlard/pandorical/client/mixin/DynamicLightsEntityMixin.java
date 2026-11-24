package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import justfatlard.pandorical.client.render.DynamicLights;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Entities take their block light here rather than through the block lookup, so they are lit too. */
@Mixin(EntityRenderer.class)
public abstract class DynamicLightsEntityMixin<T extends Entity> {

	@ModifyReturnValue(method = "getBlockLightLevel", at = @At("RETURN"))
	private int pandorical$addDynamicLight(int level, T entity, BlockPos pos) {
		if (level >= 15 || !DynamicLights.active()) return level;
		return Math.max(level, DynamicLights.levelAt(pos));
	}
}
