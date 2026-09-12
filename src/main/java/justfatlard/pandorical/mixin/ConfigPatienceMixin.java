package justfatlard.pandorical.mixin;

import justfatlard.pandorical.config.ConfigPatience;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The keep-alive held still while a client takes in the content sync. See {@link ConfigPatience}.
 *
 * <p>Held rather than kept going: a client frozen in a reload answers every keep-alive it was sent
 * once it wakes, and vanilla ends a connection that answers any but the latest. So no new
 * challenge goes out while it is busy, and the clock that would time out the one already out is
 * not allowed to run. Pings go instead, which it answers late and harmlessly.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ConfigPatienceMixin {
	@Unique
	private static final long PING_EVERY_MILLIS = 10_000L;

	@Shadow private long keepAliveTime;
	@Shadow @Final protected Connection connection;

	@Unique private long pandorical$lastPing;

	@Inject(method = "keepConnectionAlive", at = @At("HEAD"), cancellable = true)
	private void pandorical$patience(CallbackInfo ci) {
		if (!((Object) this instanceof ServerConfigurationPacketListenerImpl config)) return;
		if (!ConfigPatience.isWaiting(config)) return;

		long now = Util.getMillis();
		this.keepAliveTime = now;
		if (now - this.pandorical$lastPing >= PING_EVERY_MILLIS) {
			this.pandorical$lastPing = now;
			this.connection.send(new ClientboundPingPacket((int) now));
		}
		ci.cancel();
	}
}
