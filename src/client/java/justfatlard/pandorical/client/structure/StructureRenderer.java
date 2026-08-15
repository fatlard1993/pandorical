package justfatlard.pandorical.client.structure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import justfatlard.pandorical.Pandorical;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Map;

/**
 * Renders active structures each frame as a batch of vanilla block models, positioned and
 * rotated as one unit via {@link PoseStack}, not one entity per block.
 *
 * <p>Hooks {@code LevelRenderEvents.COLLECT_SUBMITS}, the fabric-rendering-v1 hook for the
 * submit-node renderer architecture: it exposes a {@code SubmitNodeCollector} and a
 * camera-relative {@code PoseStack} for arbitrary world-space content. (The older
 * {@code WorldRenderEvents} class no longer exists on this Minecraft version.)
 *
 * <p>Per block, this uses {@code SubmitNodeCollector.submitMovingBlock(PoseStack,
 * MovingBlockRenderState, int)}: the same mechanism vanilla's own
 * {@code FallingBlockRenderer} and piston moving-block rendering use to draw a block state's
 * model at an arbitrary transformed position with real lighting/AO, rather than at its actual
 * placed position in a chunk. Model/texture/tint resolution is therefore entirely vanilla's
 * own responsibility; block model baking is never touched here.
 *
 * <p><b>Known simplifications</b> (acceptable for a first version per the design brief):
 * <ul>
 *   <li>{@code MovingBlockRenderState} always reports its own single block state for any
 *       neighbor query, so faces between two blocks placed adjacently within the <em>same</em>
 *       structure are not culled against each other (both render in full). Vanilla's own
 *       moving-block rendering (e.g. piston heads) behaves the same way; not a new
 *       limitation introduced here.</li>
 *   <li>Per-block world light/biome is sampled at that block's approximate rotated world
 *       position (cheap: the same {@code lightEngine}/{@code cardinalLighting} objects are
 *       reused for every block, only the queried {@code BlockPos} differs), which is more
 *       accurate than a single anchor-position sample. The manual yaw rotation used only for
 *       choosing that sample position is a best-effort match to the PoseStack's rotation.
 *       It affects lighting/biome-tint sampling only, never block placement, which is driven
 *       solely by the vanilla-composed PoseStack transform.</li>
 * </ul>
 */
public final class StructureRenderer {
    private StructureRenderer() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(StructureRenderer::onCollectSubmits);
        Pandorical.LOGGER.info("Pandorical structure renderer registered");
    }

    private static void onCollectSubmits(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Collection<StructureManager.ClientStructure> structures = StructureManager.getActive();
        if (structures.isEmpty()) return;

        // COLLECT_SUBMITS runs on the submission (post-extraction) phase, which doesn't hand us
        // a DeltaTracker directly. Reuse the globally-accessible partial tick source HudRenderer
        // already uses rather than introducing a second per-frame hook via LevelExtractionEvents;
        // a simplification accepted for this first version.
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        Vec3 camPos = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();

        for (StructureManager.ClientStructure structure : structures) {
            if (!structure.visible) continue;
            renderStructure(structure, level, camPos, poseStack, collector, partialTick);
        }
    }

    private static void renderStructure(StructureManager.ClientStructure structure, ClientLevel level, Vec3 camPos,
                                         PoseStack poseStack, SubmitNodeCollector collector, float partialTick) {
        StructureManager.StructurePoseSnapshot pose = structure.interpolated(partialTick);

        double yawRad = Math.toRadians(pose.yaw());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        // poseStack from LevelRenderContext is camera-relative world space (standard MC
        // convention; entity submit() likewise applies only local offsets on top of an
        // already camera-relative incoming PoseStack).
        poseStack.pushPose();
        poseStack.translate(pose.x() - camPos.x, pose.y() - camPos.y, pose.z() - camPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-pose.yaw()));

        for (Map.Entry<StructureManager.RelPosKey, BlockState> entry : structure.blocks.entrySet()) {
            StructureManager.RelPosKey rel = entry.getKey();
            BlockState state = entry.getValue();
            if (state.isAir()) continue;

            poseStack.pushPose();
            poseStack.translate(rel.x(), rel.y(), rel.z());

            MovingBlockRenderState renderState = new MovingBlockRenderState();
            renderState.blockState = state;
            // Stable per-block seed independent of the structure's movement, so position-seeded
            // model/texture variants don't flicker as the structure moves.
            renderState.randomSeedPos = new BlockPos(rel.x(), rel.y(), rel.z());
            renderState.blockPos = worldBlockPos(pose, rel, cos, sin);
            renderState.biome = level.getBiome(renderState.blockPos);
            renderState.cardinalLighting = level.cardinalLighting();
            renderState.lightEngine = level.getLightEngine();

            collector.submitMovingBlock(poseStack, renderState, EntityRenderState.NO_OUTLINE);

            poseStack.popPose();
        }

        poseStack.popPose();
    }

    /**
     * Approximate world-space block position for lighting/biome sampling only; see the
     * class-level known-simplifications note. Never used for the visual transform.
     */
    private static BlockPos worldBlockPos(StructureManager.StructurePoseSnapshot pose,
                                           StructureManager.RelPosKey rel, double cos, double sin) {
        double localX = rel.x() + 0.5;
        double localZ = rel.z() + 0.5;
        double rotatedX = localX * cos - localZ * sin;
        double rotatedZ = localX * sin + localZ * cos;
        return BlockPos.containing(pose.x() + rotatedX, pose.y() + rel.y() + 0.5, pose.z() + rotatedZ);
    }
}
