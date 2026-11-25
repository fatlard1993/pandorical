package justfatlard.pandorical.push;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import justfatlard.pandorical.api.EntityOrientationApi;
import justfatlard.pandorical.protocol.EntityOrientationS2C;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Which entities are turned and how, told to the players who can see them as they come to see
 * them; and where a turned entity's riders sit, on either side.
 */
public final class EntityOrientations implements EntityOrientationApi {
	public static final EntityOrientations INSTANCE = new EntityOrientations();

	/** How far it turns, in radians of quaternion angle, before watchers are told again: a degree. */
	private static final float RETELL = (float) Math.toRadians(1);

	private record Turned(Entity entity, Quaternionf orientation, Quaternionf told) {}

	private static final Map<Integer, Turned> TURNED = new ConcurrentHashMap<>();

	/** The client's orientations, by network id, for seating riders on the client. Set by the client. */
	public static volatile IntFunction<@Nullable Quaternionfc> client = id -> null;

	private EntityOrientations() {}

	@Override
	public void orient(Entity entity, Quaternionfc orientation) {
		Quaternionf unit = new Quaternionf(orientation).normalize();
		if (!unit.isFinite()) {
			clear(entity);
			return;
		}
		Turned turned = TURNED.get(entity.getId());
		if (turned != null && turned.entity() == entity) {
			turned.orientation().set(unit);
			if (angle(turned.told(), unit) < RETELL) return;
			turned.told().set(unit);
		} else {
			TURNED.put(entity.getId(), new Turned(entity, unit, new Quaternionf(unit)));
		}
		broadcast(entity, payload(entity.getId(), unit));
	}

	@Override
	public void clear(Entity entity) {
		Turned turned = TURNED.get(entity.getId());
		if (turned == null || turned.entity() != entity) return;
		TURNED.remove(entity.getId(), turned);
		broadcast(entity, EntityOrientationS2C.cleared(entity.getId()));
	}

	@Override
	public boolean canOrient(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, EntityOrientationS2C.TYPE);
	}

	private static float angle(Quaternionfc a, Quaternionfc b) {
		return 2F * (float) Math.acos(Math.min(1F, Math.abs(a.dot(b))));
	}

	private static EntityOrientationS2C payload(int id, Quaternionfc q) {
		return new EntityOrientationS2C(id, true, q.x(), q.y(), q.z(), q.w());
	}

	private static void broadcast(Entity entity, EntityOrientationS2C payload) {
		if (!(entity.level() instanceof ServerLevel)) return;
		for (ServerPlayer player : PlayerLookup.tracking(entity)) {
			if (ServerPlayNetworking.canSend(player, EntityOrientationS2C.TYPE)) ServerPlayNetworking.send(player, payload);
		}
	}

	/** @hidden */
	public static void handleStartTracking(Entity entity, ServerPlayer player) {
		Turned turned = TURNED.get(entity.getId());
		if (turned == null || turned.entity() != entity) return;
		if (ServerPlayNetworking.canSend(player, EntityOrientationS2C.TYPE)) {
			ServerPlayNetworking.send(player, payload(entity.getId(), turned.orientation()));
		}
	}

	/** @hidden */
	public static void sendAllTo(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, EntityOrientationS2C.TYPE)) return;
		for (Turned turned : TURNED.values()) {
			if (turned.entity().level() instanceof ServerLevel && PlayerLookup.tracking(turned.entity()).contains(player)) {
				ServerPlayNetworking.send(player, payload(turned.entity().getId(), turned.orientation()));
			}
		}
	}

	/** @hidden */
	public static void forget(Entity entity) {
		Turned turned = TURNED.get(entity.getId());
		if (turned != null && turned.entity() == entity) TURNED.remove(entity.getId(), turned);
	}

	// ---- Riders, on either side ----

	/** How this entity is turned, on whichever side it lives, or null if it is not. */
	public static @Nullable Quaternionfc of(Entity entity) {
		if (entity.level() instanceof ServerLevel) {
			Turned turned = TURNED.get(entity.getId());
			return turned != null && turned.entity() == entity ? turned.orientation() : null;
		}
		return client.apply(entity.getId());
	}

	/**
	 * The turn that takes an upright entity heading the same way to this orientation: what a rider
	 * is turned by. Upside down along a ceiling it is a roll about the line of travel, and up a
	 * wall a pitch back, so a rider keeps facing the way they faced.
	 *
	 * <p>The heading is the entity's length laid flat; standing on end it has none, and the heading
	 * it came up with is the one whose upright frame turns into this one without a mirror, which is
	 * the length laid flat less the top laid flat as far as the length points up.
	 */
	public static Quaternionf tilt(Quaternionfc orientation) {
		Vector3f length = orientation.transform(new Vector3f(1, 0, 0));
		Vector3f top = orientation.transform(new Vector3f(0, 1, 0));
		Vector3f heading = new Vector3f(length.x - length.y * top.x, 0, length.z - length.y * top.z);
		if (heading.lengthSquared() < 1.0e-8F) heading.set(1, 0, 0);
		heading.normalize();
		Vector3f side = new Vector3f(heading).cross(0, 1, 0);
		Quaternionf upright = new Matrix3f(heading, new Vector3f(0, 1, 0), side).getNormalizedRotation(new Quaternionf());
		return new Quaternionf(orientation).mul(upright.conjugate());
	}

	/**
	 * Where a rider of a turned vehicle sits: with their eyes where an upright rider's eyes would
	 * be, turned with the vehicle about its middle, and their box hanging from their eyes upright
	 * as ever. Returned as vanilla's riding position, which the rider's own attachment point is
	 * taken from to find their feet.
	 *
	 * <p>The eyes and not the middle of the box, because a seated rider's box is a standing one
	 * sunk into the seat: its middle is about level with the vehicle's, so turned upside down it
	 * still reached up through the ceiling the cart hung from, and the rider choked. The eyes are
	 * what the game chokes, and what the view is taken from, so they are what is put right; the
	 * drawing is turned about the rider's feet as they would be carried round, which lands the
	 * drawn head on the eyes.
	 */
	public static Vec3 seat(Entity vehicle, Entity passenger, Vec3 uprightRidingPosition, Quaternionfc orientation) {
		Vec3 attach = passenger.getVehicleAttachmentPoint(vehicle);
		double eyes = passenger.getEyeHeight();
		Vec3 pivot = vehicle.position().add(0, vehicle.getBbHeight() / 2.0, 0);
		Vec3 upright = uprightRidingPosition.subtract(attach).add(0, eyes, 0).subtract(pivot);
		Vector3f turned = tilt(orientation).transform(new Vector3f((float) upright.x, (float) upright.y, (float) upright.z));
		return pivot.add(turned.x, turned.y, turned.z).subtract(0, eyes, 0).add(attach);
	}
}
