package justfatlard.pandorical.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import justfatlard.pandorical.portal.ImmersivePortals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalWaitMixin {
	@ModifyReturnValue(method = "getPortalTransitionTime", at = @At("RETURN"))
	private int pandorical$stepThrough(int vanilla, ServerLevel level, Entity entity) {
		return ImmersivePortals.wait(level.getServer(), vanilla);
	}
}
