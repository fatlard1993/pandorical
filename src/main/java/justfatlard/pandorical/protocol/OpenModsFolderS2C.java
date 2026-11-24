package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Open the player's own mods folder on their desktop. Carries no path: the client opens its own
 * folder and nothing else, and only while the mods screen is open.
 */
public record OpenModsFolderS2C() implements CustomPacketPayload {
    public static final Type<OpenModsFolderS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "open_mods_folder"));

    public static final StreamCodec<ByteBuf, OpenModsFolderS2C> STREAM_CODEC = StreamCodec.unit(new OpenModsFolderS2C());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
