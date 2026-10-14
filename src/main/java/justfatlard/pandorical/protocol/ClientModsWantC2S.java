package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The answer to {@link ClientModsOfferS2C}, by mod id: the login waits for it, even when empty. */
public record ClientModsWantC2S(List<String> ids) implements CustomPacketPayload {
	public static final Type<ClientModsWantC2S> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "client_mods_want"));

	public static final StreamCodec<ByteBuf, ClientModsWantC2S> STREAM_CODEC =
		ByteBufCodecs.<ByteBuf, String, List<String>>collection(ArrayList::new, ByteBufCodecs.stringUtf8(64), ClientModsOfferS2C.MOST)
			.map(ClientModsWantC2S::new, ClientModsWantC2S::ids);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
