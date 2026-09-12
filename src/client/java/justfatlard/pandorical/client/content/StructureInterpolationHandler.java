package justfatlard.pandorical.client.content;

import justfatlard.pandorical.client.structure.StructureManager;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.phys.Vec3;

/**
 * Moves an entity the way {@link StructureManager} moves a structure, so the two stay together.
 *
 * <p>An entity that rides on a structure - big-boats' ship anchor with the pilot on it, a cushion
 * with somebody sat on it - is positioned by the server every tick, and on the client both it and
 * the deck under it blend towards each new position rather than jumping. Vanilla's blends and the
 * structure's differ in how far behind the latest position they sit, and the difference is what
 * the rider sees: a pilot who stands a block off the helm at speed and slides across the deck in
 * every turn. Same blend, same lag, same place.
 *
 * <p>The rule: each new position starts a fresh blend from wherever the entity is now, and the
 * blend runs over {@link StructureManager#INTERPOLATION_TICKS} ticks. Predicted movement from
 * velocity is ignored: where the deck says a thing is, is where it is.
 */
public final class StructureInterpolationHandler implements InterpolationHandler {
	private static final int TICKS = StructureManager.INTERPOLATION_TICKS;

	private final Entity entity;
	private Vec3 from;
	private Vec3 to;
	private float fromYaw;
	private float toYaw;
	private float fromPitch;
	private float toPitch;
	private int ticks = TICKS;

	public StructureInterpolationHandler(Entity entity) {
		this.entity = entity;
	}

	@Override
	public PositionAndRotation target() {
		return to == null
			? PositionAndRotation.of(entity.position(), entity.getYRot(), entity.getXRot())
			: PositionAndRotation.of(to, toYaw, toPitch);
	}

	@Override
	public boolean interpolateTo(PositionPath path, float yaw, float pitch, boolean withRotation) {
		from = entity.position();
		fromYaw = entity.getYRot();
		fromPitch = entity.getXRot();
		// A turn without a move carries no path: the game passes null and means stay where you
		// are. Docking a boat sends exactly that, and reading the path crashed the client.
		to = path != null ? path.endPosition() : (to != null ? to : from);
		toYaw = withRotation ? yaw : fromYaw;
		toPitch = withRotation ? pitch : fromPitch;
		ticks = 0;
		return true;
	}

	@Override
	public void interpolate() {
		if (ticks >= TICKS) return;
		ticks++;
		float t = ticks / (float) TICKS;
		entity.setPos(from.lerp(to, t));
		entity.setYRot(Mth.rotLerp(t, fromYaw, toYaw));
		entity.setXRot(Mth.lerp(t, fromPitch, toPitch));
	}

	@Override
	public void applyPredictedMovement(Vec3 movement) {
	}

	@Override
	public boolean hasActiveInterpolation() {
		return ticks < TICKS;
	}

	@Override
	public void cancel() {
		ticks = TICKS;
	}
}
