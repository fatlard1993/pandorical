package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@link PlayAnimationS2C} with how long the animation has already been playing, so a client that
 * comes into view of the entity part way through, or is told again, carries on from there instead
 * of starting over. Sent only to the players tracking the entity, and again whenever one starts
 * to; the client forgets it when the entity leaves its world. The old channel stays for clients
 * that have only it.
 *
 * @param animation     empty to stop whatever is playing
 * @param looping       false plays once and holds the last pose
 * @param elapsedMillis how long ago, by the server's clock, it started
 */
public record PlayAnimationV2S2C(int entityId, String animation, boolean looping, long elapsedMillis)
		implements CustomPacketPayload {

	public static final Type<PlayAnimationV2S2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "play_animation_v2"));

	public static final StreamCodec<ByteBuf, PlayAnimationV2S2C> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, PlayAnimationV2S2C::entityId,
		ByteBufCodecs.STRING_UTF8, PlayAnimationV2S2C::animation,
		ByteBufCodecs.BOOL, PlayAnimationV2S2C::looping,
		ByteBufCodecs.VAR_LONG, PlayAnimationV2S2C::elapsedMillis,
		PlayAnimationV2S2C::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
