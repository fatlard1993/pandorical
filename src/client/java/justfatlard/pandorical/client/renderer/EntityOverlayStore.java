package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.EntityOverlayS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Dropped as an entity leaves the client's world: the server sends it again when tracking starts again. */
@Environment(EnvType.CLIENT)
public final class EntityOverlayStore {
	private EntityOverlayStore() {}

	private static final Map<Integer, Identifier> overlays = new ConcurrentHashMap<>();

	public static void handle(EntityOverlayS2C payload) {
		if (payload.texture().isEmpty()) {
			overlays.remove(payload.entityId());
			return;
		}
		Identifier texture = Identifier.tryParse(payload.texture());
		if (texture == null) {
			Pandorical.LOGGER.warn("Ignoring entity overlay with invalid texture id '{}'", payload.texture());
			return;
		}
		overlays.put(payload.entityId(), texture);
	}

	public static Identifier get(int entityId) {
		return overlays.get(entityId);
	}

	public static void forget(int entityId) {
		overlays.remove(entityId);
	}

	public static void clear() {
		overlays.clear();
	}
}
