package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.EntityOverlayS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side store of server-pushed entity overlays, keyed by entity network
 * id. Read every frame from the render state extraction hook; cleared on
 * connection init and disconnect. Entries for entities that despawn without an
 * explicit clear are harmless: network ids are unique per server run, so they
 * can never attach to a different entity within the session.
 */
@Environment(EnvType.CLIENT)
public final class EntityOverlayStore {
	private EntityOverlayStore() {}

	private static final Map<Integer, Identifier> overlays = new ConcurrentHashMap<>();

	/** Apply a set/clear payload. An empty texture string clears. */
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

	/** The overlay texture for an entity id, or null when none is set. */
	public static Identifier get(int entityId) {
		return overlays.get(entityId);
	}

	public static void clear() {
		overlays.clear();
	}
}
