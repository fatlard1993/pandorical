package justfatlard.pandorical.client.decal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import justfatlard.pandorical.client.mixin.BannerRendererInvoker;
import justfatlard.pandorical.protocol.BannerDecalsS2C;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.Vec3;

/**
 * Draws banner patterns flat on blocks.
 *
 * <p>The banner's own flag model, laid on its back and stretched to the rectangle the server
 * asked for, drawn through vanilla's pattern-layer submission so every pattern, colour and
 * sprite is exactly the banner's. The base layer is left out: the block underneath is the
 * ground, and a pattern over a bed's own blanket is what a patterned bed looks like, where a
 * flat sheet of dye colour over it is a banner lying on a bed.
 *
 * <p>The flag is twenty by forty pixels hung from its top, so it is turned to lie along the
 * rectangle with its top at the head, scaled from its own size to the rectangle's, and pressed
 * almost flat: a few thousandths of a block thick, enough to stay above the face it lies on.
 */
public final class BannerDecalRenderer {
	private BannerDecalRenderer() {}

	/** The flag's size in blocks: twenty by forty pixels at the model's sixteen to the block. */
	private static final float FLAG_WIDE = 20 / 16F;
	private static final float FLAG_LONG = 40 / 16F;
	/** Where the flag's top hangs in its model, in blocks: forty-four pixels up the pole. */
	private static final float FLAG_TOP = 44 / 16F;
	/** Pressed to a few thousandths of a block, with its face just above the block's. */
	private static final float FLATTEN = 0.08F;

	private static EntityModelSet modelsFrom;
	private static BannerFlagModel flag;

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(BannerDecalRenderer::onCollectSubmits);
	}

	private static void onCollectSubmits(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || BannerDecalStore.all().isEmpty()) return;

		Vec3 cam = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		BannerFlagModel model = flagModel(mc);

		for (BannerDecalsS2C.Entry decal : BannerDecalStore.all()) {
			BlockPos pos = BlockPos.of(decal.pos());
			// A decal outlives what it lies on only until somebody looks: no chunk, or no block,
			// and it is not drawn. The server clears it properly when it notices.
			if (!level.hasChunkAt(pos) || level.getBlockState(pos).isAir()) continue;
			if (pos.distSqr(BlockPos.containing(cam)) > 64 * 64) continue;

			Direction toHead = Direction.from3DDataValue(decal.toHead());
			int light = LightCoordsUtil.getLightCoords(level, pos.above());

			poseStack.pushPose();
			poseStack.translate(pos.getX() + 0.5 - cam.x, pos.getY() + decal.lift() - cam.y, pos.getZ() + 0.5 - cam.z);
			// Local -z points at the head after this turn.
			poseStack.rotateDegrees(Axis.YP, 180 - toHead.toYRot());
			// The top edge sits fromHead in from the head-side face, which is half a block out.
			poseStack.translate(0, 0, -0.5 + decal.fromHead());
			// On its back: the flag's down-the-pole axis becomes along the block, its face up.
			poseStack.rotateDegrees(Axis.XP, 90);
			poseStack.scale(decal.width() / FLAG_WIDE, decal.length() / FLAG_LONG, FLATTEN);
			// The flag hangs from FLAG_TOP; bring its top to the origin.
			poseStack.translate(0, FLAG_TOP, 0);

			BannerPatternLayers layers = decal.layers();
			for (int i = 0; i < layers.layers().size(); i++) {
				BannerPatternLayers.Layer layer = layers.layers().get(i);
				BannerRendererInvoker.pandorical$submitPatternLayer(mc.getAtlasManager(), poseStack,
					collector.order(i), light, OverlayTexture.NO_OVERLAY, model, 0F,
					Sheets.getBannerSprite(layer.pattern()), layer.color());
			}
			poseStack.popPose();
		}
	}

	/** Vanilla's standing flag, rebaked whenever the entity models are. */
	private static BannerFlagModel flagModel(Minecraft mc) {
		EntityModelSet models = mc.getEntityModels();
		if (flag == null || modelsFrom != models) {
			flag = new BannerFlagModel(models.bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
			modelsFrom = models;
		}
		return flag;
	}
}
