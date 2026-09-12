package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** At login: which server this is, so the game can hand back what that server left with it. */
public record KeepsakesAskConfigS2C(String serverId) implements CustomPacketPayload {
	public static final Type<KeepsakesAskConfigS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "keepsakes_ask"));

	public static final StreamCodec<ByteBuf, KeepsakesAskConfigS2C> STREAM_CODEC =
		ByteBufCodecs.STRING_UTF8.map(KeepsakesAskConfigS2C::new, KeepsakesAskConfigS2C::serverId);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
