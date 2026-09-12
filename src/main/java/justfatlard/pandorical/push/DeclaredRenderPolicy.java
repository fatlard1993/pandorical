package justfatlard.pandorical.push;

import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.RenderApi;
import justfatlard.pandorical.protocol.RenderPolicyS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** The rendering policy a mod declared at startup, told to each client as it arrives. */
public final class DeclaredRenderPolicy implements RenderApi {
	public static final DeclaredRenderPolicy INSTANCE = new DeclaredRenderPolicy();

	/**
	 * The policy in force, kept so a player joining later is told the same thing as everyone
	 * already here. A mod declares this once at startup and never again.
	 */
	private static volatile boolean cullLeaves = false;

	private DeclaredRenderPolicy() {}

	/**
	 * Declared once, at mod initialize, before any server or player exists - so this only
	 * records the answer and {@link #sendTo} does the telling as each client arrives. There is
	 * deliberately no broadcast: a rendering policy is a property of the server's content, not
	 * something that flips while people are looking at it.
	 */
	@Override
	public void cullLeaves(boolean enforce) {
		cullLeaves = enforce;
	}

	/** Tell one arriving client what this server asks for. */
	public static void sendTo(ServerPlayer player) {
		if (!cullLeaves) return;
		if (!PandoricalApi.hasCapability(player, Capabilities.RENDER_POLICY)) return;
		ServerPlayNetworking.send(player, new RenderPolicyS2C(cullLeaves));
	}
}
