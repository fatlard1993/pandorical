package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * An entity has started, or stopped, playing an animation the server defined.
 *
 * <p>An empty id means "stop". The client works out how far through the animation is from when the
 * packet arrived rather than being told each frame - an animation is a thing that runs, not a
 * position that has to be streamed.
 *
 * @param entityId the entity's network id, which is what the renderer has to hand
 * @param animation the animation's id, or empty to stop whatever is playing
 * @param looping whether it repeats or plays once and holds its last pose
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
