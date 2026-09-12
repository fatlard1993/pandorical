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
 * Only extraction knows the block position and only submit picks the sprite, so the texture
 * travels between them on the render state. The sprite is swapped because the material is a
 * closed enum.
 */
@Mixin(ChestRenderer.class)
public class ChestRendererMixin {

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void pandorical$captureOverlay(BlockEntity blockEntity, ChestRenderState state, float partialTick,
			Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo ci) {
		// Written every time, null included: render states are pooled and reused.
		((ChestOverlayHolder) state).pandorical$setChestOverlay(
			ChestOverlayStore.get(blockEntity.getBlockPos()));
	}

	@Redirect(
		method = "submit",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/Sheets;chooseSprite(Lnet/minecraft/client/renderer/blockentity/state/ChestRenderState$ChestMaterialType;Lnet/minecraft/world/level/block/state/properties/ChestType;)Lnet/minecraft/client/resources/model/sprite/SpriteId;"))
	private SpriteId pandorical$chooseSprite(ChestRenderState.ChestMaterialType material, ChestType chestType,
			ChestRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		Identifier base = ((ChestOverlayHolder) state).pandorical$getChestOverlay();
		if (base == null) return Sheets.chooseSprite(material, chestType);

		String suffix = switch (chestType) {
			case LEFT -> "_left";
			case RIGHT -> "_right";
			default -> "";
		};

		return new SpriteId(Sheets.CHEST_SHEET,
			Identifier.fromNamespaceAndPath(base.getNamespace(), base.getPath() + suffix));
	}
}
