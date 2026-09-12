package justfatlard.pandorical.push;

import java.util.Map;
import justfatlard.pandorical.api.CameraApi;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.CameraHintS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class CameraHints implements CameraApi {
	public static final CameraHints INSTANCE = new CameraHints();

	private CameraHints() {}

	@Override
	public void setDistance(ServerPlayer player, float distance) {
		if (!PandoricalApi.hasCapability(player, Capabilities.CAMERA)) return;
		ServerPlayNetworking.send(player, new CameraHintS2C("distance",
			Map.of("distance", String.valueOf(distance))));
	}

	@Override
	public void setPerspective(ServerPlayer player, String perspective) {
		if (!PandoricalApi.hasCapability(player, Capabilities.CAMERA)) return;
		ServerPlayNetworking.send(player, new CameraHintS2C("perspective",
			Map.of("mode", perspective)));
	}

	@Override
	public void zoom(ServerPlayer player, float factor) {
		if (!PandoricalApi.hasCapability(player, Capabilities.CAMERA)) return;
		ServerPlayNetworking.send(player, new CameraHintS2C("zoom",
			Map.of("factor", String.valueOf(factor))));
	}

	@Override
	public void reset(ServerPlayer player) {
		if (!PandoricalApi.isAvailable(player)) return;
		ServerPlayNetworking.send(player, new CameraHintS2C("reset", Map.of()));
	}
}
