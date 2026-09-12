package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import justfatlard.pandorical.api.KeepsakeApi;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record KeepsakesConfigC2S(Map<String, String> values) implements CustomPacketPayload {
	public static final Type<KeepsakesConfigC2S> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "keepsakes"));

	public static final StreamCodec<ByteBuf, KeepsakesConfigC2S> STREAM_CODEC =
		ByteBufCodecs.<ByteBuf, String, String, Map<String, String>>map(HashMap::new,
				ByteBufCodecs.stringUtf8(KeepsakeApi.LONGEST_KEY), ByteBufCodecs.stringUtf8(KeepsakeApi.LONGEST_VALUE), KeepsakeApi.MOST_KEYS)
			.map(KeepsakesConfigC2S::new, KeepsakesConfigC2S::values);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
