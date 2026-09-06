package justfatlard.pandorical.client.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SingleQuadParticle.class)
public interface SingleQuadParticleAccessor {
	@Accessor("rCol") float pandorical$rCol();
	@Accessor("gCol") float pandorical$gCol();
	@Accessor("bCol") float pandorical$bCol();
}
