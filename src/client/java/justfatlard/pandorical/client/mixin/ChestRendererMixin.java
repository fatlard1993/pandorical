package justfatlard.pandorical.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import justfatlard.pandorical.client.renderer.ChestOverlayHolder;
import justfatlard.pandorical.client.renderer.ChestOverlayStore;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a server say that particular chests are drawn with a different texture.
 *
 * <p>Two halves, because the two facts arrive in different places. Extraction is
 * the only point that knows which block this is, and submit is the only point
 * that picks a sprite, so extraction writes the texture onto the render state
 * and submit reads it back off.
 *
 * <p>The sprite is swapped rather than the material, because the material is a
 * closed enum. Vanilla resolves it through a single static call, which is the
 * one thing this needs to intervene in.
 */
@Mixin(ChestRenderer.class)
public class ChestRendererMixin {

	// require = 1: the client config defaults to 0, which turns a target that no
	// longer matches into a feature that silently stops existing.
	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 1)
	private void pandorical$captureOverlay(BlockEntity blockEntity, ChestRenderState state, float partialTick,
			Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo ci) {
		// Written every time, null included: states are pooled, and a stale value
		// would dress the next ordinary chest that reused this one.
		((ChestOverlayHolder) state).pandorical$setChestOverlay(
			ChestOverlayStore.get(blockEntity.getBlockPos()));
	}

	@Redirect(
		require = 1,
		method = "submit",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/Sheets;chooseSprite(Lnet/minecraft/client/renderer/blockentity/state/ChestRenderState$ChestMaterialType;Lnet/minecraft/world/level/block/state/properties/ChestType;)Lnet/minecraft/client/resources/model/sprite/SpriteId;"))
	private SpriteId pandorical$chooseSprite(ChestRenderState.ChestMaterialType material, ChestType chestType,
			ChestRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		Identifier base = ((ChestOverlayHolder) state).pandorical$getChestOverlay();
		if (base == null) return Sheets.chooseSprite(material, chestType);

		// Vanilla splits a double chest across two textures and names them by the
		// half they are, so the same suffixes apply to a replacement.
		String suffix = switch (chestType) {
			case LEFT -> "_left";
			case RIGHT -> "_right";
			default -> "";
		};

		return new SpriteId(Sheets.CHEST_SHEET,
			Identifier.fromNamespaceAndPath(base.getNamespace(), base.getPath() + suffix));
	}
}
