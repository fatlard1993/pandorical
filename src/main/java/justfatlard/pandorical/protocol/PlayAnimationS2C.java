package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The client times the animation from this packet's arrival.
 *
 * @param animation empty to stop whatever is playing
 * @param looping false plays once and holds the last pose
 */
public record PlayAnimationS2C(int entityId, String animation, boolean looping)
		implements CustomPacketPayload {

	public static final Type<PlayAnimationS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "play_animation"));

	public static final StreamCodec<ByteBuf, PlayAnimationS2C> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, PlayAnimationS2C::entityId,
		ByteBufCodecs.STRING_UTF8, PlayAnimationS2C::animation,
		ByteBufCodecs.BOOL, PlayAnimationS2C::looping,
		PlayAnimationS2C::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
