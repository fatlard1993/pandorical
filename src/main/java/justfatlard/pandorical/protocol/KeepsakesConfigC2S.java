package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The answer: everything this server left with the game, key to value. */
public record KeepsakesConfigC2S(Map<String, String> values) implements CustomPacketPayload {
	public static final Type<KeepsakesConfigC2S> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "keepsakes"));

	public static final StreamCodec<ByteBuf, KeepsakesConfigC2S> STREAM_CODEC =
		ByteBufCodecs.<ByteBuf, String, String, Map<String, String>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8)
			.map(KeepsakesConfigC2S::new, KeepsakesConfigC2S::values);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
