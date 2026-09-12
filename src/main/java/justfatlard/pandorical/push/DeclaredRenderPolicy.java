package justfatlard.pandorical.push;

import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.RenderApi;
import justfatlard.pandorical.protocol.RenderPolicyS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class DeclaredRenderPolicy implements RenderApi {
	public static final DeclaredRenderPolicy INSTANCE = new DeclaredRenderPolicy();

	private static volatile boolean cullLeaves = false;

	private DeclaredRenderPolicy() {}

	/** Declared at mod init, before any player exists: no broadcast, {@link #sendTo} tells arrivals. */
	@Override
	public void cullLeaves(boolean enforce) {
		cullLeaves = enforce;
	}

	public static void sendTo(ServerPlayer player) {
		if (!cullLeaves) return;
		if (!PandoricalApi.hasCapability(player, Capabilities.RENDER_POLICY)) return;
		ServerPlayNetworking.send(player, new RenderPolicyS2C(cullLeaves));
	}
}
