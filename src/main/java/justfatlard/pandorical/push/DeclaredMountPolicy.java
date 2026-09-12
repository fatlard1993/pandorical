package justfatlard.pandorical.push;

import justfatlard.pandorical.MountPolicy;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.MountApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.MountPolicyS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class DeclaredMountPolicy implements MountApi {
	public static final DeclaredMountPolicy INSTANCE = new DeclaredMountPolicy();

	private static boolean doubleRiders = false;
	private static boolean freeLook = false;

	private DeclaredMountPolicy() {}

	@Override
	public void doubleRiders(boolean allow) {
		doubleRiders = allow;
		apply();
	}

	@Override
	public void freeLook(boolean enable) {
		freeLook = enable;
		apply();
	}

	private static void apply() {
		MountPolicy.set(doubleRiders, freeLook);
	}

	public static void sendTo(ServerPlayer player) {
		if (!doubleRiders && !freeLook) return;
		if (!PandoricalApi.hasCapability(player, Capabilities.MOUNT_POLICY)) return;

		ServerPlayNetworking.send(player, new MountPolicyS2C(doubleRiders, freeLook));
	}
}
