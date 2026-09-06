package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Rendering this server asks for, over and above what the player chose.
 *
 * <p>Only ever additive: a policy can turn something on that a client left off, and never off that
 * a client turned on. A server saying "cull leaves here" is answering for its own content - a world
 * full of enormous trees is a different proposition from vanilla - and it is not entitled to
 * countermand somebody's settings in the other direction.
 *
 * <p>Held for the connection and dropped when it ends, so a policy is a fact about being on this
 * server rather than a change to the player's own preferences.
 */
public record RenderPolicyS2C(boolean cullLeaves) implements CustomPacketPayload {

    public static final Type<RenderPolicyS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "render_policy"));

    public static final StreamCodec<ByteBuf, RenderPolicyS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, RenderPolicyS2C::cullLeaves,
        RenderPolicyS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
