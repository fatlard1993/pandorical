package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Keep this value for this server; an empty value takes the key away. */
public record KeepsakeStoreS2C(String serverId, String key, String value) implements CustomPacketPayload {
	public static final Type<KeepsakeStoreS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "keepsake_store"));

	public static final StreamCodec<ByteBuf, KeepsakeStoreS2C> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, KeepsakeStoreS2C::serverId,
		ByteBufCodecs.STRING_UTF8, KeepsakeStoreS2C::key,
		ByteBufCodecs.STRING_UTF8, KeepsakeStoreS2C::value,
		KeepsakeStoreS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
