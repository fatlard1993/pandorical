package justfatlard.pandorical.client.structure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.RelPos;
import justfatlard.pandorical.structure.DryCells;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Draws structures as vanilla moving blocks, as falling blocks and pistons are drawn, under one
 * pose per structure. A moving block sees nothing around it, so faces between blocks of the same
 * structure are not culled; a block that cannot be seen at all, opaque and walled in on every side
 * by opaque full blocks of its own structure, is left out. A structure wholly off screen is not
 * drawn.
 */
public final class StructureRenderer {
    private StructureRenderer() {}

    /** How far a block's model may reach past its own cell. */
    private static final double MODEL_REACH = 1.0;

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
        Frustum frustum = context.levelState().cameraRenderState.cullFrustum;
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();

        for (StructureManager.ClientStructure structure : structures) {
            if (!structure.visible) continue;
            renderStructure(structure, level, camPos, frustum, poseStack, collector, partialTick);
        }
    }

    private static void renderStructure(StructureManager.ClientStructure structure, ClientLevel level, Vec3 camPos,
                                         Frustum frustum, PoseStack poseStack, SubmitNodeCollector collector, float partialTick) {
        DrawList drawn = structure.drawList(level);
        if (drawn.empty()) return;

        StructureManager.StructurePoseSnapshot pose = structure.interpolated(partialTick);

        double yawRad = Math.toRadians(pose.yaw());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        float scale = structure.scale;

        if (!frustum.isVisible(drawn.worldBounds(pose, cos, sin, scale))) return;

        // The context's pose stack is camera-relative.
        poseStack.pushPose();
        poseStack.translate(pose.x() - camPos.x, pose.y() - camPos.y, pose.z() - camPos.z);
        poseStack.rotateDegrees(Axis.YP, -pose.yaw());
        if (scale != 1.0F) poseStack.scale(scale, scale, scale);

        for (Shown shown : drawn.blocks) {
            poseStack.pushPose();
            poseStack.translate(shown.x, shown.y, shown.z);
            shown.place(level, pose, cos, sin, scale);
            collector.submitMovingBlock(poseStack, shown.state, EntityRenderState.NO_OUTLINE);
            poseStack.popPose();
        }

        StructureFluids.submit(drawn.fluids, level, poseStack, collector, pose, cos, sin, scale);
        StructureHolds.submitMask(structure, poseStack, collector);

        poseStack.popPose();
    }

    /**
     * Approximate, for light and biome sampling only; the pose stack places the block. Scaled with
     * the structure, so a model's blocks take their light from where they are drawn, not from where
     * they would stand at full size.
     */
    static void worldBlockPos(StructureManager.StructurePoseSnapshot pose, int relX, int relY, int relZ,
                              double cos, double sin, float scale, BlockPos.MutableBlockPos into) {
        double localX = (relX + 0.5) * scale;
        double localZ = (relZ + 0.5) * scale;
        double rotatedX = localX * cos - localZ * sin;
        double rotatedZ = localX * sin + localZ * cos;
        into.set(Mth.floor(pose.x() + rotatedX), Mth.floor(pose.y() + (relY + 0.5) * scale), Mth.floor(pose.z() + rotatedZ));
    }

    /**
     * One drawn block, kept from frame to frame: its render state is filled once, and only its
     * place in the world, and the biome there, follow the structure about.
     */
    private static final class Shown {
        final int x, y, z;
        final MovingBlockRenderState state = new MovingBlockRenderState();
        private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        private final BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();

        Shown(StructureManager.RelPosKey rel, BlockState block) {
            this.x = rel.x();
            this.y = rel.y();
            this.z = rel.z();
            state.blockState = block;
            // Seeded by relative position, so model variants hold still while the structure moves.
            state.randomSeedPos = new BlockPos(x, y, z);
            state.blockPos = at;
        }

        void place(ClientLevel level, StructureManager.StructurePoseSnapshot pose, double cos, double sin, float scale) {
            worldBlockPos(pose, x, y, z, cos, sin, scale, next);
            if (state.biome == null || !next.equals(at)) {
                at.set(next);
                state.biome = level.getBiome(at);
            }
            state.cardinalLighting = level.cardinalLighting();
            state.lightEngine = level.getLightEngine();
        }
    }

    /** What of a structure is drawn, worked out again only when its blocks, its dry cells or the world change. */
    static final class DrawList {
        final int revision;
        final DryCells dry;
        final ClientLevel level;
        final List<Shown> blocks;
        final List<StructureFluids.Wet> fluids;
        /** Relative bounds of everything drawn, models' reach included; min > max when nothing is. */
        private final double minX, minY, minZ, maxX, maxY, maxZ;

        DrawList(StructureManager.ClientStructure structure, int revision, ClientLevel level) {
            this.revision = revision;
            this.dry = structure.dry;
            this.level = level;
            Map<StructureManager.RelPosKey, BlockState> all = structure.blocks;
            List<Shown> shown = new ArrayList<>();
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (Map.Entry<StructureManager.RelPosKey, BlockState> entry : all.entrySet()) {
                BlockState state = entry.getValue();
                if (state.isAir()) continue;
                StructureManager.RelPosKey rel = entry.getKey();
                x0 = Math.min(x0, rel.x()); y0 = Math.min(y0, rel.y()); z0 = Math.min(z0, rel.z());
                x1 = Math.max(x1, rel.x()); y1 = Math.max(y1, rel.y()); z1 = Math.max(z1, rel.z());
                if (!walledIn(all, rel, state)) shown.add(new Shown(rel, state));
            }
            for (RelPos cell : dry.tops()) {
                x0 = Math.min(x0, cell.x()); y0 = Math.min(y0, cell.y()); z0 = Math.min(z0, cell.z());
                x1 = Math.max(x1, cell.x()); y1 = Math.max(y1, cell.y()); z1 = Math.max(z1, cell.z());
            }
            this.blocks = shown;
            this.fluids = StructureFluids.wet(all);
            this.minX = x0 - MODEL_REACH; this.minY = y0 - MODEL_REACH; this.minZ = z0 - MODEL_REACH;
            this.maxX = x1 + 1 + MODEL_REACH; this.maxY = y1 + 1 + MODEL_REACH; this.maxZ = z1 + 1 + MODEL_REACH;
        }

        boolean empty() {
            return minX > maxX;
        }

        /** The relative bounds turned and scaled as the pose stack places them, as a box in the world. */
        AABB worldBounds(StructureManager.StructurePoseSnapshot pose, double cos, double sin, float scale) {
            double wx0 = Double.MAX_VALUE, wz0 = Double.MAX_VALUE, wx1 = -Double.MAX_VALUE, wz1 = -Double.MAX_VALUE;
            for (int corner = 0; corner < 4; corner++) {
                double lx = ((corner & 1) == 0 ? minX : maxX) * scale;
                double lz = ((corner & 2) == 0 ? minZ : maxZ) * scale;
                double wx = lx * cos - lz * sin;
                double wz = lx * sin + lz * cos;
                wx0 = Math.min(wx0, wx); wx1 = Math.max(wx1, wx);
                wz0 = Math.min(wz0, wz); wz1 = Math.max(wz1, wz);
            }
            return new AABB(pose.x() + wx0, pose.y() + minY * scale, pose.z() + wz0,
                pose.x() + wx1, pose.y() + maxY * scale, pose.z() + wz1);
        }

        private static boolean walledIn(Map<StructureManager.RelPosKey, BlockState> blocks,
                                        StructureManager.RelPosKey rel, BlockState state) {
            if (!state.isSolidRender()) return false;
            for (Direction side : Direction.values()) {
                BlockState next = blocks.get(new StructureManager.RelPosKey(
                    rel.x() + side.getStepX(), rel.y() + side.getStepY(), rel.z() + side.getStepZ()));
                if (next == null || !next.isSolidRender()) return false;
            }
            return true;
        }
    }
}
