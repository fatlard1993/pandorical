package justfatlard.pandorical.client.animation;

import justfatlard.pandorical.protocol.DisplaySpinS2C;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** What each display turns by, by network id, worked out from the clock as it is drawn. */
public final class ClientDisplaySpins {
	private ClientDisplaySpins() {}

	private static final float MILLIS_PER_TICK = 50F;

	/** @param startedAt client wall-clock millis */
	private record Spin(float pivotX, float pivotY, float pivotZ, float axisX, float axisY, float axisZ,
			float degreesPerTick, int ticks, long startedAt) {}

	private static final Map<Integer, Spin> SPINNING = new ConcurrentHashMap<>();

	public static void handle(DisplaySpinS2C payload) {
		float length = (float) Math.sqrt(payload.axisX() * payload.axisX() + payload.axisY() * payload.axisY()
			+ payload.axisZ() * payload.axisZ());
		if (payload.degreesPerTick() == 0F || !Float.isFinite(payload.degreesPerTick()) || !(length > 1.0e-6F)) {
			SPINNING.remove(payload.entityId());
			return;
		}
		SPINNING.put(payload.entityId(), new Spin(payload.pivotX(), payload.pivotY(), payload.pivotZ(),
			payload.axisX() / length, payload.axisY() / length, payload.axisZ() / length,
			payload.degreesPerTick(), Math.max(0, payload.ticks()),
			System.currentTimeMillis() - Math.max(0L, payload.elapsedMillis())));
	}

	/** Degrees it has turned by now, or NaN when it is not turning. */
	public static float angle(int entityId) {
		Spin spin = SPINNING.get(entityId);
		return spin == null ? Float.NaN : angle(spin, System.currentTimeMillis());
	}

	private static float angle(Spin spin, long now) {
		float ticks = (now - spin.startedAt()) / MILLIS_PER_TICK;
		if (spin.ticks() > 0) ticks = Math.min(ticks, spin.ticks());
		// Whole turns dropped, so a turn left going for hours keeps its precision.
		return (spin.degreesPerTick() * ticks) % 360F;
	}

	/** The turn to draw the display's drawing through, after its own transformation; null for none. */
	public static Matrix4f turn(int entityId) {
		Spin spin = SPINNING.get(entityId);
		if (spin == null) return null;
		float radians = (float) Math.toRadians(angle(spin, System.currentTimeMillis()));
		return new Matrix4f()
			.translate(spin.pivotX(), spin.pivotY(), spin.pivotZ())
			.rotate(radians, spin.axisX(), spin.axisY(), spin.axisZ())
			.translate(-spin.pivotX(), -spin.pivotY(), -spin.pivotZ());
	}

	/** The display left this client's world; the server says it again as it comes back. */
	public static void forget(int entityId) {
		SPINNING.remove(entityId);
	}

	public static void clearAll() {
		SPINNING.clear();
	}
}
