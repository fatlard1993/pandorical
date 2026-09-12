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

/**
 * An item display whose item is marked hidden is not drawn.
 *
 * <p>For a mod that draws something better through Pandorical and keeps the item display as
 * what vanilla clients see: the display is the record and the fallback, and on a Pandorical
 * client it would be drawn twice.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class HiddenDisplayMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void pandorical$hideMarkedDisplays(Entity entity, Frustum frustum, double x, double y, double z,
			float partialTick, CallbackInfoReturnable<Boolean> cir) {
		if (!(entity instanceof Display.ItemDisplay display)) return;
		ItemStack item = display.getSlot(0).get();
		CustomData data = item.get(DataComponents.CUSTOM_DATA);
		// Read in place: this runs for every item display every frame, and copyTag copies the lot
		if (data != null && ((CustomDataAccessor) (Object) data).pandorical$tag().contains(BannerDecalApi.HIDDEN_ITEM_KEY)) {
			cir.setReturnValue(false);
		}
	}
}
