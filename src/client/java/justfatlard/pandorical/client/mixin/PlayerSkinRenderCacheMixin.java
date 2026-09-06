package justfatlard.pandorical.client.mixin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import justfatlard.pandorical.client.skin.SkinOverrides;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Draw the skin the server asked for on heads too.
 *
 * <p>A player entity asks {@code AbstractClientPlayer.getSkin} what it looks like; a player head
 * does not. A skull block and a head item both resolve their face here, from the profile the head
 * carries, so an override that stopped at the player would leave their head wearing Steve - which
 * on an offline server is every head there is.
 *
 * <p>Both entry points are covered: {@code getOrDefault} is what the skull renderer and the head
 * item ask, and {@code lookup} is behind the deferred form used by GUI faces.
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
