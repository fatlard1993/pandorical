package justfatlard.pandorical.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import justfatlard.pandorical.client.entitymodel.ClientEntityModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An item in a hand an assigned model resized is held at its own size, once it is in the hand. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", shift = At.Shift.AFTER))
	private void pandorical$ownSize(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack,
			HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		ClientEntityModels.heldItem(state, arm, poseStack);
	}
}
