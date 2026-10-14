package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One slice of a jar {@link ClientModsOfferS2C} described, each jar's slices sent in order. */
public record ClientModPartS2C(String id, int index, int total, byte[] data) implements CustomPacketPayload {
	public static final Type<ClientModPartS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "client_mod_part"));

	public static final StreamCodec<ByteBuf, ClientModPartS2C> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(64), ClientModPartS2C::id,
		ByteBufCodecs.VAR_INT, ClientModPartS2C::index,
		ByteBufCodecs.VAR_INT, ClientModPartS2C::total,
		ByteBufCodecs.BYTE_ARRAY, ClientModPartS2C::data,
		ClientModPartS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
