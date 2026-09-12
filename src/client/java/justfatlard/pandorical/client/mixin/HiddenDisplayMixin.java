package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.api.BannerDecalApi;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The marked item display is the vanilla-client fallback for something Pandorical draws. */
@Mixin(EntityRenderDispatcher.class)
public abstract class HiddenDisplayMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void pandorical$hideMarkedDisplays(Entity entity, Frustum frustum, double x, double y, double z,
			float partialTick, CallbackInfoReturnable<Boolean> cir) {
		if (!(entity instanceof Display.ItemDisplay display)) return;
		ItemStack item = display.getSlot(0).get();
		CustomData data = item.get(DataComponents.CUSTOM_DATA);
		// Not copyTag: this runs for every item display every frame.
		if (data != null && ((CustomDataAccessor) (Object) data).pandorical$tag().contains(BannerDecalApi.HIDDEN_ITEM_KEY)) {
			cir.setReturnValue(false);
		}
	}
}
