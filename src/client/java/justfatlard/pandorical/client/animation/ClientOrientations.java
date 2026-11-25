package justfatlard.pandorical.client.animation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.client.settings.ClientPrefs;
import justfatlard.pandorical.client.settings.ClientSettings;
import justfatlard.pandorical.protocol.EntityOrientationS2C;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

/**
 * How each turned entity is turned, by network id, eased from where it was drawn to where it was
 * last told over a tick, since the server tells only when it has turned by a degree or more.
 */
public final class ClientOrientations {
	private ClientOrientations() {}

	private static final String ROLL_KEY = "rider_view_turns";
	private static final float MILLIS_PER_TICK = 50F;

	/** @param at client wall-clock millis the turn toward {@code to} began */
	private record Turn(Quaternionf from, Quaternionf to, long at) {
		Quaternionf at(long now) {
			float done = Math.clamp((now - at) / MILLIS_PER_TICK, 0F, 1F);
			return new Quaternionf(from).slerp(to, done);
		}
	}

	private static final Map<Integer, Turn> TURNED = new ConcurrentHashMap<>();
	private static Boolean viewTurns;

	public static void register() {
		ClientSettings.INSTANCE.group("pandorical", "Pandorical")
			.toggle(ROLL_KEY, "Riders' view turns",
				"Riding something turned upside down or on end, the view turns with it; off keeps it level",
				ClientOrientations::viewTurns, ClientOrientations::setViewTurns);
	}

	public static boolean viewTurns() {
		if (viewTurns == null) viewTurns = ClientPrefs.getBoolean(ROLL_KEY, true);
		return viewTurns;
	}

	private static void setViewTurns(boolean on) {
		viewTurns = on;
		ClientPrefs.set(ROLL_KEY, on);
	}

	public static void handle(EntityOrientationS2C payload) {
		if (!payload.oriented()) {
			TURNED.remove(payload.entityId());
			return;
		}
		Quaternionf to = new Quaternionf(payload.x(), payload.y(), payload.z(), payload.w()).normalize();
		if (!to.isFinite()) return;
		long now = System.currentTimeMillis();
		Turn was = TURNED.get(payload.entityId());
		TURNED.put(payload.entityId(), new Turn(was == null ? new Quaternionf(to) : was.at(now), to, now));
	}

	/** How it is turned as of now, or null if it is not. */
	public static @Nullable Quaternionfc now(int entityId) {
		Turn turn = TURNED.get(entityId);
		return turn == null ? null : turn.at(System.currentTimeMillis());
	}

	public static void forget(int entityId) {
		TURNED.remove(entityId);
	}

	public static void clearAll() {
		TURNED.clear();
	}
}
