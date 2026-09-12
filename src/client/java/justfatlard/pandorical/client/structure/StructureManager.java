package justfatlard.pandorical.client.structure;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.DespawnStructureS2C;
import justfatlard.pandorical.protocol.SetStructureVisibleS2C;
import justfatlard.pandorical.protocol.SpawnStructureS2C;
import justfatlard.pandorical.protocol.StructureBlockEntry;
import justfatlard.pandorical.protocol.StructureRelPos;
import justfatlard.pandorical.protocol.UpdateStructureBlocksS2C;
import justfatlard.pandorical.protocol.UpdateStructurePoseS2C;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client structures. Each server pose is blended in over {@link #INTERPOLATION_TICKS} ticks, and
 * a frame is drawn between the last two ticked poses like an entity. Riders blend by the same
 * rule ({@code StructureInterpolationHandler}) so they are drawn where the deck is.
 */
public final class StructureManager {
    private StructureManager() {}

    /** Fixed, not derived from the update interval; suits updates about once per server tick. */
    public static final int INTERPOLATION_TICKS = 3;

    private static final Map<String, ClientStructure> structures = new ConcurrentHashMap<>();

    public static void handleSpawn(SpawnStructureS2C payload) {
        Map<RelPosKey, BlockState> blocks = new LinkedHashMap<>();
        for (StructureBlockEntry entry : payload.blocks()) {
            blocks.put(new RelPosKey(entry.x(), entry.y(), entry.z()), entry.state());
        }
        StructurePoseSnapshot pose = new StructurePoseSnapshot(payload.x(), payload.y(), payload.z(), payload.yaw());
        structures.put(payload.structureId(), new ClientStructure(blocks, pose, payload.visible()));
        Pandorical.LOGGER.debug("Structure '{}' spawned with {} blocks", payload.structureId(), blocks.size());
    }

    public static void handleUpdatePose(UpdateStructurePoseS2C payload) {
        ClientStructure structure = structures.get(payload.structureId());
        if (structure == null) return;
        structure.pushPose(new StructurePoseSnapshot(payload.x(), payload.y(), payload.z(), payload.yaw()));
    }

    public static void handleUpdateBlocks(UpdateStructureBlocksS2C payload) {
        ClientStructure structure = structures.get(payload.structureId());
        if (structure == null) return;

        for (StructureBlockEntry entry : payload.added()) {
            structure.blocks.put(new RelPosKey(entry.x(), entry.y(), entry.z()), entry.state());
        }
        for (StructureRelPos pos : payload.removed()) {
            structure.blocks.remove(new RelPosKey(pos.x(), pos.y(), pos.z()));
        }
        for (StructureBlockEntry entry : payload.changed()) {
            structure.blocks.put(new RelPosKey(entry.x(), entry.y(), entry.z()), entry.state());
        }
    }

    public static void handleSetVisible(SetStructureVisibleS2C payload) {
        ClientStructure structure = structures.get(payload.structureId());
        if (structure == null) return;
        structure.visible = payload.visible();
    }

    public static void handleDespawn(DespawnStructureS2C payload) {
        structures.remove(payload.structureId());
    }

    /** Once per client tick. */
    public static void tick() {
        for (ClientStructure structure : structures.values()) {
            structure.tick();
        }
    }

    public static Collection<ClientStructure> getActive() {
        return structures.values();
    }

    public static void clear() {
        structures.clear();
    }

    public record RelPosKey(int x, int y, int z) {}

    public record StructurePoseSnapshot(double x, double y, double z, float yaw) {}

    public static final class ClientStructure {
        public final Map<RelPosKey, BlockState> blocks;
        public boolean visible;

        private StructurePoseSnapshot previousPose;
        private StructurePoseSnapshot targetPose;
        private int ticksSinceUpdate = INTERPOLATION_TICKS;

        private StructurePoseSnapshot lastTick;
        private StructurePoseSnapshot thisTick;

        ClientStructure(Map<RelPosKey, BlockState> blocks, StructurePoseSnapshot initialPose, boolean visible) {
            this.blocks = blocks;
            this.previousPose = initialPose;
            this.targetPose = initialPose;
            this.lastTick = initialPose;
            this.thisTick = initialPose;
            this.visible = visible;
        }

        void pushPose(StructurePoseSnapshot newPose) {
            this.previousPose = thisTick;
            this.targetPose = newPose;
            this.ticksSinceUpdate = 0;
        }

        void tick() {
            lastTick = thisTick;
            if (ticksSinceUpdate < INTERPOLATION_TICKS) ticksSinceUpdate++;
            thisTick = blended(ticksSinceUpdate / (float) INTERPOLATION_TICKS);
        }

        public StructurePoseSnapshot interpolated(float partialTick) {
            if (partialTick >= 1.0f) return thisTick;
            if (partialTick <= 0.0f) return lastTick;
            return between(lastTick, thisTick, partialTick);
        }

        private StructurePoseSnapshot blended(float t) {
            if (t >= 1.0f) return targetPose;
            if (t <= 0.0f) return previousPose;
            return between(previousPose, targetPose, t);
        }

        private static StructurePoseSnapshot between(StructurePoseSnapshot from, StructurePoseSnapshot to, float t) {
            return new StructurePoseSnapshot(
                lerp(t, from.x(), to.x()),
                lerp(t, from.y(), to.y()),
                lerp(t, from.z(), to.z()),
                lerpAngle(t, from.yaw(), to.yaw()));
        }

        private static double lerp(float t, double start, double end) {
            return start + t * (end - start);
        }

        private static float lerpAngle(float t, float start, float end) {
            return start + t * wrapDegrees(end - start);
        }

        private static float wrapDegrees(float degrees) {
            float wrapped = degrees % 360.0f;
            if (wrapped >= 180.0f) wrapped -= 360.0f;
            if (wrapped < -180.0f) wrapped += 360.0f;
            return wrapped;
        }
    }
}
