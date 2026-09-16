package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import justfatlard.pandorical.client.maprelief.ClientMapReliefs;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A relief stands up to two blocks out of a frame a sixteenth thick, so the frame's own box
 * would have it culled while its top was still on screen.
 */
@Mixin(EntityRenderer.class)
public class ReliefCullingMixin {
    @ModifyReturnValue(method = "getBoundingBoxForCulling", at = @At("RETURN"))
    private AABB pandorical$reliefReach(AABB box, @Local(argsOnly = true) Entity entity) {
        if (!(entity instanceof ItemFrame frame)) return box;
        ClientMapReliefs.Relief relief = ClientMapReliefs.of(frame);
        if (relief == null) return box;
        Direction out = frame.getDirection();
        return box.expandTowards(out.getStepX() * relief.rise, out.getStepY() * relief.rise, out.getStepZ() * relief.rise);
    }
}
