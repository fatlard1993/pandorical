package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.skin.SkinOverrides;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Player heads resolve their skin here, not through {@code AbstractClientPlayer.getSkin}. The
 * skull renderer and head item call {@code getOrDefault}; GUI faces go through {@code lookup}.
 */
@Mixin(PlayerSkinRenderCache.class)
public abstract class PlayerSkinRenderCacheMixin {

	@Inject(method = "getOrDefault", at = @At("RETURN"), cancellable = true)
	private void pandorical$wearOverride(ResolvableProfile profile,
			CallbackInfoReturnable<PlayerSkinRenderCache.RenderInfo> cir) {
		PlayerSkinRenderCache.RenderInfo worn = SkinOverrides.forHead(
			(PlayerSkinRenderCache) (Object) this, profile, cir.getReturnValue());
		if (worn != null) cir.setReturnValue(worn);
	}

	@Inject(method = "lookup", at = @At("RETURN"), cancellable = true)
	private void pandorical$wearOverrideLater(ResolvableProfile profile,
			CallbackInfoReturnable<CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>>> cir) {
		if (!SkinOverrides.dresses(profile)) return;

		PlayerSkinRenderCache cache = (PlayerSkinRenderCache) (Object) this;
		cir.setReturnValue(cir.getReturnValue().thenApply(found -> found.map(theirs -> {
			PlayerSkinRenderCache.RenderInfo worn = SkinOverrides.forHead(cache, profile, theirs);
			return worn != null ? worn : theirs;
		})));
	}
}
