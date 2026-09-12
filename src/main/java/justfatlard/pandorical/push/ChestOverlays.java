package justfatlard.pandorical.push;

import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.ChestOverlayApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.ChestOverlayS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public final class ChestOverlays implements ChestOverlayApi {
	public static final ChestOverlays INSTANCE = new ChestOverlays();

	private ChestOverlays() {}

	@Override
	public void replace(ServerPlayer player, Identifier texture, Collection<BlockPos> positions) {
		send(player, ChestOverlayS2C.OP_REPLACE, texture, positions);
	}

	@Override
	public void add(ServerPlayer player, Identifier texture, Collection<BlockPos> positions) {
		if (positions.isEmpty()) return;
		send(player, ChestOverlayS2C.OP_ADD, texture, positions);
	}

	@Override
	public void remove(ServerPlayer player, Collection<BlockPos> positions) {
		if (positions.isEmpty()) return;
		// The client ignores a removal's texture.
		send(player, ChestOverlayS2C.OP_REMOVE, Identifier.fromNamespaceAndPath("pandorical", "none"), positions);
	}

	private static void send(ServerPlayer player, byte op, Identifier texture, Collection<BlockPos> positions) {
		if (!PandoricalApi.hasCapability(player, Capabilities.CHEST_OVERLAYS)) return;

		long[] packed = new long[positions.size()];
		int i = 0;
		for (BlockPos pos : positions) packed[i++] = pos.asLong();

		ServerPlayNetworking.send(player, new ChestOverlayS2C(op, texture.toString(), packed));
	}
}
