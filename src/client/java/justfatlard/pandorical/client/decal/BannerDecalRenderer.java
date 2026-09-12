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
 * Banner patterns laid flat on blocks: vanilla's flag model on its back, scaled to the server's
 * rectangle, drawn through vanilla's pattern-layer submission. The base layer is not drawn.
 */
public final class BannerDecalRenderer {
	private BannerDecalRenderer() {}

	// The flag model is 20x40 pixels, hung from 44 pixels up the pole.
	private static final float FLAG_WIDE = 20 / 16F;
	private static final float FLAG_LONG = 40 / 16F;
	private static final float FLAG_TOP = 44 / 16F;
	/** Thin, but with its face above the block's. */
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
			// The server clears a decal whose block is gone; until then it is not drawn.
			if (!level.hasChunkAt(pos) || level.getBlockState(pos).isAir()) continue;
			if (pos.distSqr(BlockPos.containing(cam)) > 64 * 64) continue;

			Direction toHead = Direction.from3DDataValue(decal.toHead());
			int light = LightCoordsUtil.getLightCoords(level, pos.above());

			poseStack.pushPose();
			poseStack.translate(pos.getX() + 0.5 - cam.x, pos.getY() + decal.lift() - cam.y, pos.getZ() + 0.5 - cam.z);
			// Local -z points at the head after this turn.
			poseStack.rotateDegrees(Axis.YP, 180 - toHead.toYRot());
			poseStack.translate(0, 0, -0.5 + decal.fromHead());
			poseStack.rotateDegrees(Axis.XP, 90);
			poseStack.scale(decal.width() / FLAG_WIDE, decal.length() / FLAG_LONG, FLATTEN);
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

	private static BannerFlagModel flagModel(Minecraft mc) {
		EntityModelSet models = mc.getEntityModels();
		if (flag == null || modelsFrom != models) {
			flag = new BannerFlagModel(models.bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
			modelsFrom = models;
		}
		return flag;
	}
}
