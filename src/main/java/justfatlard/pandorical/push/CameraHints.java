package justfatlard.pandorical.push;

import justfatlard.pandorical.api.CameraApi;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.ClientKind;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.login.ClientKinds;
import justfatlard.pandorical.protocol.CameraHintS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public final class CameraHints implements CameraApi {
	public static final CameraHints INSTANCE = new CameraHints();

	private volatile CameraApi bedrock;

	private CameraHints() {}

	/** Hints for Bedrock players go here instead, since they can't take Pandorical's packets. */
	public void bridgeBedrock(CameraApi camera) {
		this.bedrock = camera;
	}

	private CameraApi bedrockFor(ServerPlayer player) {
		CameraApi camera = bedrock;
		return camera != null && ClientKinds.of(player.getUUID()) == ClientKind.BEDROCK ? camera : null;
	}

	@Override
	public void setDistance(ServerPlayer player, float distance) {
		CameraApi bridged = bedrockFor(player);
		if (bridged != null) {
			bridged.setDistance(player, distance);
			return;
		}
		if (!PandoricalApi.hasCapability(player, Capabilities.CAMERA)) return;
		ServerPlayNetworking.send(player, new CameraHintS2C("distance",
			Map.of("distance", String.valueOf(distance))));
	}

	@Override
	public void setPerspective(ServerPlayer player, String perspective) {
		CameraApi bridged = bedrockFor(player);
		if (bridged != null) {
			bridged.setPerspective(player, perspective);
			return;
		}
		if (!PandoricalApi.hasCapability(player, Capabilities.CAMERA)) return;
		// Empty says "hand it back": Map.of would throw on a null, and the javadoc promises null works.
		ServerPlayNetworking.send(player, new CameraHintS2C("perspective",
			Map.of("mode", perspective == null ? "" : perspective)));
	}

	@Override
	public void zoom(ServerPlayer player, float factor) {
		CameraApi bridged = bedrockFor(player);
		if (bridged != null) {
			bridged.zoom(player, factor);
			return;
		}
		if (!PandoricalApi.hasCapability(player, Capabilities.CAMERA)) return;
		ServerPlayNetworking.send(player, new CameraHintS2C("zoom",
			Map.of("factor", String.valueOf(factor))));
	}

	@Override
	public void reset(ServerPlayer player) {
		CameraApi bridged = bedrockFor(player);
		if (bridged != null) {
			bridged.reset(player);
			return;
		}
		if (!PandoricalApi.isAvailable(player)) return;
		ServerPlayNetworking.send(player, new CameraHintS2C("reset", Map.of()));
	}
}
