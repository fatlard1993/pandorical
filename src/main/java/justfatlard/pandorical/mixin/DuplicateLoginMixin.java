package justfatlard.pandorical.mixin;

import com.mojang.authlib.GameProfile;
import justfatlard.pandorical.Pandorical;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Turns away a login under a name already playing, where vanilla kicks the one playing. Without
 * online-mode nothing proves who owns a name, so the newcomer has the weaker claim; one from another
 * address than the session playing is warned of as a possible impostor.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class DuplicateLoginMixin {
	@Shadow @Final private MinecraftServer server;
	@Shadow @Final private Connection connection;

	@Shadow public abstract void disconnect(Component component);

	@Inject(
		method = "verifyLoginAndFinishConnectionSetup",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;disconnectAllPlayersWithProfile(Ljava/util/UUID;)Z"),
		cancellable = true
	)
	private void pandorical$keepThePlayerOn(GameProfile profile, CallbackInfo ci) {
		ServerPlayer playing = this.server.getPlayerList().getPlayer(profile.id());
		if (playing == null) return;

		Connection playingConnection = ((ServerCommonConnectionAccessor) playing.connection).pandorical$connection();
		boolean logIps = this.server.logIPs();
		String incoming = this.connection.getLoggableAddress(logIps);
		String existing = playingConnection.getLoggableAddress(logIps);

		if (sameHost(this.connection.getRemoteAddress(), playingConnection.getRemoteAddress())) {
			Pandorical.LOGGER.info("Refused a second login as {} from {}, the address already playing as {}",
				profile.name(), incoming, existing);
		} else {
			Pandorical.LOGGER.warn("Refused a login as {} from {} while {} plays from {}: someone else may be using this name",
				profile.name(), incoming, profile.name(), existing);
		}

		this.disconnect(Component.literal(profile.name()
			+ " is already playing on this server. If your game just closed, wait a moment and join again."));
		ci.cancel();
	}

	private static boolean sameHost(SocketAddress a, SocketAddress b) {
		return a instanceof InetSocketAddress inetA && b instanceof InetSocketAddress inetB
			&& inetA.getAddress() != null && inetA.getAddress().equals(inetB.getAddress());
	}
}
