package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.ChestOverlayS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side store of server-pushed chest textures, keyed by packed block
 * position. Read during render state extraction, which happens for every chest
 * in view every frame, so lookups have to stay a plain map hit.
 *
 * <p>Cleared on connection init and disconnect. Positions are only meaningful
 * within the dimension they were sent for, so a dimension change clears too and
 * waits for the server to say it again: keeping them would paint chests in the
 * Nether at whatever coordinates a village happened to occupy in the overworld.
 */
@Environment(EnvType.CLIENT)
public final class ChestOverlayStore {
	private ChestOverlayStore() {}

	private static final Map<Long, Identifier> overlays = new ConcurrentHashMap<>();

	public static void handle(ChestOverlayS2C payload) {
		if (payload.op() == ChestOverlayS2C.OP_REMOVE) {
			for (long packed : payload.positions()) overlays.remove(packed);
			return;
		}

		Identifier texture = Identifier.tryParse(payload.texture());
		if (texture == null) {
			Pandorical.LOGGER.warn("Ignoring chest overlay with invalid texture id '{}'", payload.texture());
			return;
		}

		if (payload.op() == ChestOverlayS2C.OP_REPLACE) {
			overlays.values().removeIf(texture::equals);
		}
		for (long packed : payload.positions()) overlays.put(packed, texture);
	}

	/** The sprite base for a chest, or null when it is an ordinary one. */
	public static Identifier get(BlockPos pos) {
		return overlays.isEmpty() ? null : overlays.get(pos.asLong());
	}

	public static void clear() {
		overlays.clear();
	}
}
