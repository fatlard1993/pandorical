package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The client mods this server keeps, Pandorical's own jar among them, described but not sent: a
 * client asks for the ones it wants with {@link ClientModsWantC2S}.
 */
public record ClientModsOfferS2C(List<Entry> mods) implements CustomPacketPayload {
	public static final int MOST = 256;

	public static final Type<ClientModsOfferS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "client_mods_offer"));

	/**
	 * @param sha256 the jar's SHA-256, lowercase hex
	 * @param size   the jar's length in bytes
	 */
	public record Entry(String id, String version, String sha256, int size) {
		public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), Entry::id,
			ByteBufCodecs.stringUtf8(64), Entry::version,
			ByteBufCodecs.stringUtf8(64), Entry::sha256,
			ByteBufCodecs.VAR_INT, Entry::size,
			Entry::new);
	}

	public static final StreamCodec<ByteBuf, ClientModsOfferS2C> STREAM_CODEC =
		ByteBufCodecs.<ByteBuf, Entry, List<Entry>>collection(ArrayList::new, Entry.STREAM_CODEC, MOST)
			.map(ClientModsOfferS2C::new, ClientModsOfferS2C::mods);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
