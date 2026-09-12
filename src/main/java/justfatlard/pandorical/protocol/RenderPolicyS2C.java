package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Only additive: it turns on what a client left off, never off what it turned on. Held for the
 * connection, never saved to the player's preferences.
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
