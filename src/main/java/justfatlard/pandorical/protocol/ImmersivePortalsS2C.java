package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ImmersivePortalsS2C(boolean on) implements CustomPacketPayload {

	public static final Type<ImmersivePortalsS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "immersive_portals"));

	public static final StreamCodec<ByteBuf, ImmersivePortalsS2C> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, ImmersivePortalsS2C::on,
		ImmersivePortalsS2C::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
