package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.NotUnderstood;
import justfatlard.pandorical.client.ClientNotices;
import justfatlard.pandorical.protocol.ChestOverlayS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Read for every chest in view every frame. Positions carry no dimension. */
@Environment(EnvType.CLIENT)
public final class ChestOverlayStore {
	private ChestOverlayStore() {}

	private static final Map<Long, Identifier> overlays = new ConcurrentHashMap<>();

	public static void handle(ChestOverlayS2C payload) {
		if (payload.op() != ChestOverlayS2C.OP_REPLACE && payload.op() != ChestOverlayS2C.OP_ADD
				&& payload.op() != ChestOverlayS2C.OP_REMOVE) {
			// Treating an unknown op as "add" drew overlays the server never asked for.
			ClientNotices.report(NotUnderstood.CHEST_OP, String.valueOf(payload.op()));
			return;
		}
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

	public static Identifier get(BlockPos pos) {
		return overlays.isEmpty() ? null : overlays.get(pos.asLong());
	}

	/** Each overlay's sprites for a single chest and either half of a double one, made once. */
	private static final Map<Identifier, SpriteId[]> sprites = new ConcurrentHashMap<>();

	public static SpriteId sprite(Identifier base, ChestType type) {
		SpriteId[] made = sprites.computeIfAbsent(base, id -> new SpriteId[] {
			new SpriteId(Sheets.CHEST_SHEET, id),
			new SpriteId(Sheets.CHEST_SHEET, id.withSuffix("_left")),
			new SpriteId(Sheets.CHEST_SHEET, id.withSuffix("_right"))});
		return switch (type) {
			case LEFT -> made[1];
			case RIGHT -> made[2];
			default -> made[0];
		};
	}

	public static void clear() {
		overlays.clear();
		sprites.clear();
	}
}
