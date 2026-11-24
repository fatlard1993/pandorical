package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import justfatlard.pandorical.client.render.DynamicLights;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every block light lookup that draws, vanilla's chunk meshes, Sodium's, block entities and
 * Pandorical's own decals and pictures, passes through this method, so dynamic light added here
 * reaches all of them.
 */
@Mixin(LightCoordsUtil.class)
public abstract class DynamicLightsBlockMixin {

	@ModifyReturnValue(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
		at = @At("RETURN"))
	private static int pandorical$addDynamicLight(int packed, LightCoordsUtil.BrightnessGetter getter,
			BlockAndLightGetter level, BlockState state, BlockPos pos) {
		if (packed == LightCoordsUtil.FULL_BRIGHT || !DynamicLights.active()) return packed;
		int dynamic = DynamicLights.levelAt(pos);
		return dynamic > LightCoordsUtil.block(packed) ? LightCoordsUtil.withBlock(packed, dynamic) : packed;
	}
}
