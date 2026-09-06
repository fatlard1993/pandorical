package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.renderer.PositionalTintStore;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Each particle made while a painted block animates is coloured with its paint. */
@Mixin(ParticleEngine.class)
public abstract class ParticleTintMixin {
	@Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
		at = @At("RETURN"))
	private void pandorical$paintParticle(ParticleOptions options, double x, double y, double z,
			double dx, double dy, double dz, CallbackInfoReturnable<Particle> cir) {
		if (cir.getReturnValue() != null) PositionalTintStore.tintParticle(cir.getReturnValue());
	}
}
