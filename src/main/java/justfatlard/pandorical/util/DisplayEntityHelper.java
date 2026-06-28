package justfatlard.pandorical.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Lightweight utility for managing vanilla Display.BlockDisplay entities server-side.
 * Replaces Polymer's ElementHolder/BlockDisplayElement pattern.
 *
 * Display entities are vanilla — clients render them natively without any mod.
 * The server creates, positions, and removes them; vanilla sync handles the rest.
 */
public final class DisplayEntityHelper {
    private DisplayEntityHelper() {}

    /**
     * Create a BlockDisplay entity at the given position.
     * Does NOT add it to the world — call world.addFreshEntity() after configuration.
     */
    public static Display.BlockDisplay createBlockDisplay(ServerLevel world, BlockState state, Vec3 position) {
        Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, world);
        display.setPos(position);
        display.setBlockState(state);
        return display;
    }

    /**
     * Spawn a BlockDisplay entity into the world.
     */
    public static Display.BlockDisplay spawnBlockDisplay(ServerLevel world, BlockState state, Vec3 position) {
        Display.BlockDisplay display = createBlockDisplay(world, state, position);
        world.addFreshEntity(display);
        return display;
    }

    /**
     * Update a display entity's position and rotation with smooth interpolation.
     */
    public static void setPositionAndRotation(Display.BlockDisplay display, Vec3 position,
                                               float yRot, float xRot, int interpolationTicks) {
        display.setPosRotInterpolationDuration(interpolationTicks);
        display.teleportTo(position.x, position.y, position.z);
        display.setYRot(yRot);
        display.setXRot(xRot);
    }

    /**
     * Set the transformation offset of a display entity (position relative to entity origin).
     * Uses the entity's transformation system for sub-entity positioning.
     */
    public static void setOffset(Display.BlockDisplay display, Vec3 offset, Quaternionf rotation,
                                  int interpolationTicks) {
        display.setTransformationInterpolationDuration(interpolationTicks);

        // Build transformation: translation + rotation
        com.mojang.math.Transformation transformation = new com.mojang.math.Transformation(
            new org.joml.Vector3f((float) offset.x, (float) offset.y, (float) offset.z),
            rotation,
            new org.joml.Vector3f(1, 1, 1), // scale
            null // right rotation
        );
        display.setTransformation(transformation);
    }

    /**
     * Update the block state of a display entity.
     */
    public static void setBlockState(Display.BlockDisplay display, BlockState state) {
        display.setBlockState(state);
    }

    /**
     * Make a display entity invisible by setting scale to 0.
     */
    public static void hide(Display.BlockDisplay display) {
        com.mojang.math.Transformation transformation = new com.mojang.math.Transformation(
            new org.joml.Vector3f(0, 0, 0),
            null,
            new org.joml.Vector3f(0, 0, 0), // zero scale = invisible
            null
        );
        display.setTransformation(transformation);
    }

    /**
     * Remove a display entity from the world.
     */
    public static void remove(Display.BlockDisplay display) {
        display.discard();
    }
}
