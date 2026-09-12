package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import justfatlard.pandorical.api.KeepsakeApi;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** An empty value removes the key. */
public record KeepsakeStoreS2C(String serverId, String key, String value) implements CustomPacketPayload {
	public static final Type<KeepsakeStoreS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "keepsake_store"));

	public static final StreamCodec<ByteBuf, KeepsakeStoreS2C> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(36), KeepsakeStoreS2C::serverId,
		ByteBufCodecs.stringUtf8(KeepsakeApi.LONGEST_KEY), KeepsakeStoreS2C::key,
		ByteBufCodecs.stringUtf8(KeepsakeApi.LONGEST_VALUE), KeepsakeStoreS2C::value,
		KeepsakeStoreS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
