package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.pandorical.client.entitymodel.ClientEntityModels;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where every submitted model is posed just before it is drawn - an entity's body, its armour, its
 * clothes - and so where an assigned model's part transforms go on, over the pose its animation gave.
 */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {

	@WrapOperation(method = "prepareModel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
	private void pandorical$partTransforms(Model<?> model, Object state, Operation<Void> original) {
		original.call(model, state);
		ClientEntityModels.afterPose(model, state);
	}
}
