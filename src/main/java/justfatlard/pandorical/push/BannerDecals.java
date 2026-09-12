package justfatlard.pandorical.push;

import java.util.Collection;
import java.util.List;
import justfatlard.pandorical.api.BannerDecalApi;
import justfatlard.pandorical.protocol.BannerDecalsS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/** Banner decals sent to one player at a time and kept by nobody but that player's client. */
public final class BannerDecals implements BannerDecalApi {
	public static final BannerDecals INSTANCE = new BannerDecals();

	private BannerDecals() {}

	@Override
	public void send(ServerPlayer player, Collection<Decal> decals) {
		if (decals.isEmpty()) return;
		post(player, decals.stream().map(decal -> new BannerDecalsS2C.Entry(
			decal.pos().asLong(), (byte) decal.toHead().get3DDataValue(), decal.lift(), decal.fromHead(),
			decal.length(), decal.width(), decal.layers())).toList());
	}

	@Override
	public void clear(ServerPlayer player, Collection<BlockPos> positions) {
		if (positions.isEmpty()) return;
		post(player, positions.stream().map(pos -> new BannerDecalsS2C.Entry(
			pos.asLong(), (byte) 0, 0F, 0F, 0F, 0F, BannerPatternLayers.EMPTY)).toList());
	}

	private void post(ServerPlayer player, List<BannerDecalsS2C.Entry> entries) {
		if (!ServerPlayNetworking.canSend(player, BannerDecalsS2C.TYPE)) return;
		ServerPlayNetworking.send(player, new BannerDecalsS2C(entries));
	}
}
