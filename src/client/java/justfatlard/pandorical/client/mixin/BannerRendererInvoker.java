package justfatlard.pandorical.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BannerRenderer.class)
public interface BannerRendererInvoker {
	@Invoker("submitPatternLayer")
	static <S> void pandorical$submitPatternLayer(SpriteGetter sprites, PoseStack poseStack,
			OrderedSubmitNodeCollector collector, int light, int overlay, Model<S> model, S state,
			SpriteId sprite, DyeColor color) {
		throw new AssertionError();
	}
}
