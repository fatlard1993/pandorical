package justfatlard.pandorical.client.content;

import justfatlard.pandorical.client.structure.StructureManager;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.phys.Vec3;

/**
 * Blends as {@link StructureManager} blends a structure, a fresh blend from the current position
 * per update, so an entity riding one lags by the same amount as the deck under it.
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
		// A turn without a move passes a null path, meaning stay put.
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
