package justfatlard.pandorical.push;

import justfatlard.pandorical.api.DisplaySpinApi;
import justfatlard.pandorical.protocol.DisplaySpinS2C;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** What each display is turning by, told to the players who can see it as they come to see it. */
public final class DisplaySpins implements DisplaySpinApi {
	public static final DisplaySpins INSTANCE = new DisplaySpins();

	/** @param startedAt server wall-clock millis */
	private record Spinning(Entity entity, Vector3fc pivot, Vector3fc axis, float degreesPerTick, int ticks, long startedAt) {
		DisplaySpinS2C now() {
			return new DisplaySpinS2C(entity.getId(), pivot.x(), pivot.y(), pivot.z(), axis.x(), axis.y(), axis.z(),
				degreesPerTick, ticks, Math.max(0L, System.currentTimeMillis() - startedAt));
		}
	}

	private static final Map<Integer, Spinning> SPINNING = new ConcurrentHashMap<>();

	private DisplaySpins() {}

	@Override
	public void spin(Display display, Vector3fc pivot, Vector3fc axis, float degreesPerTick, int ticks) {
		if (degreesPerTick == 0F || !Float.isFinite(degreesPerTick) || axis.lengthSquared() < 1.0e-12F) {
			stop(display);
			return;
		}
		var spinning = new Spinning(display, new Vector3f(pivot), new Vector3f(axis).normalize(), degreesPerTick,
			Math.max(0, ticks), System.currentTimeMillis());
		SPINNING.put(display.getId(), spinning);
		broadcast(display, spinning.now());
	}

	@Override
	public void stop(Display display) {
		if (SPINNING.remove(display.getId()) == null) return;
		broadcast(display, DisplaySpinS2C.stop(display.getId()));
	}

	@Override
	public boolean canSpin(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, DisplaySpinS2C.TYPE);
	}

	private static void broadcast(Entity entity, DisplaySpinS2C payload) {
		if (!(entity.level() instanceof ServerLevel)) return;
		for (ServerPlayer player : PlayerLookup.tracking(entity)) {
			if (ServerPlayNetworking.canSend(player, DisplaySpinS2C.TYPE)) ServerPlayNetworking.send(player, payload);
		}
	}

	/**
	 * As a player comes into view of an entity.
	 *
	 * @hidden
	 */
	public static void handleStartTracking(Entity entity, ServerPlayer player) {
		Spinning spinning = SPINNING.get(entity.getId());
		if (spinning == null || spinning.entity() != entity) return;
		if (!ServerPlayNetworking.canSend(player, DisplaySpinS2C.TYPE)) return;
		ServerPlayNetworking.send(player, spinning.now());
	}

	/**
	 * As a player's client says what it reads: tracking began before that, so what it can already
	 * see is sent now.
	 *
	 * @hidden
	 */
	public static void sendAllTo(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, DisplaySpinS2C.TYPE)) return;
		for (Spinning spinning : SPINNING.values()) {
			if (spinning.entity().level() instanceof ServerLevel
					&& PlayerLookup.tracking(spinning.entity()).contains(player)) {
				ServerPlayNetworking.send(player, spinning.now());
			}
		}
	}

	/** @hidden */
	public static void forget(Entity entity) {
		Spinning spinning = SPINNING.get(entity.getId());
		if (spinning != null && spinning.entity() == entity) SPINNING.remove(entity.getId(), spinning);
	}
}
