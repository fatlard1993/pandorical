package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Switch one of the player's own mods off, or back on, by the file name they reported.
 *
 * <p>A file name and not a path: the client looks it up among the jars it has already told the
 * server about and touches nothing else, so a server cannot name a file on somebody's disk and
 * have it renamed.
 *
 * <p>Which way it goes is not carried here, because the name already says it: a jar ending in
 * {@code .disabled} is off, anything else is on. A field saying the same thing is one the client
 * would have to check against what it already knows, and a field that must be checked is a field
 * that can lie.
 */
public record ClientModToggleS2C(String file) implements CustomPacketPayload {

    public static final Type<ClientModToggleS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "client_mod_toggle"));

    public static final StreamCodec<ByteBuf, ClientModToggleS2C> STREAM_CODEC =
        ByteBufCodecs.stringUtf8(256).map(ClientModToggleS2C::new, ClientModToggleS2C::file);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
