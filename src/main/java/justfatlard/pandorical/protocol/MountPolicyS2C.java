package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * How this server wants mounts to behave.
 *
 * <p>Sent because riding is simulated on the rider's own client as well as the server: both ends
 * have to agree about how a horse turns, or they spend every tick correcting each other.
 */
public record MountPolicyS2C(boolean doubleRiders, boolean freeLook) implements CustomPacketPayload {

	public static final Type<MountPolicyS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "mount_policy"));

	public static final StreamCodec<ByteBuf, MountPolicyS2C> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, MountPolicyS2C::doubleRiders,
		ByteBufCodecs.BOOL, MountPolicyS2C::freeLook,
		MountPolicyS2C::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
