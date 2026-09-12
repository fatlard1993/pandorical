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
 * Draws structures as vanilla moving blocks, as falling blocks and pistons are drawn, under one
 * pose per structure. A moving block answers every neighbour query with itself, so faces between
 * blocks of the same structure are not culled.
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

        // COLLECT_SUBMITS gets no DeltaTracker.
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

        // The context's pose stack is camera-relative.
        poseStack.pushPose();
        poseStack.translate(pose.x() - camPos.x, pose.y() - camPos.y, pose.z() - camPos.z);
        poseStack.rotateDegrees(Axis.YP, -pose.yaw());

        for (Map.Entry<StructureManager.RelPosKey, BlockState> entry : structure.blocks.entrySet()) {
            StructureManager.RelPosKey rel = entry.getKey();
            BlockState state = entry.getValue();
            if (state.isAir()) continue;

            poseStack.pushPose();
            poseStack.translate(rel.x(), rel.y(), rel.z());

            MovingBlockRenderState renderState = new MovingBlockRenderState();
            renderState.blockState = state;
            // Seeded by relative position, so model variants hold still while the structure moves.
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

    /** Approximate, for light and biome sampling only; the pose stack places the block. */
    private static BlockPos worldBlockPos(StructureManager.StructurePoseSnapshot pose,
                                           StructureManager.RelPosKey rel, double cos, double sin) {
        double localX = rel.x() + 0.5;
        double localZ = rel.z() + 0.5;
        double rotatedX = localX * cos - localZ * sin;
        double rotatedZ = localX * sin + localZ * cos;
        return BlockPos.containing(pose.x() + rotatedX, pose.y() + rel.y() + 0.5, pose.z() + rotatedZ);
    }
}
