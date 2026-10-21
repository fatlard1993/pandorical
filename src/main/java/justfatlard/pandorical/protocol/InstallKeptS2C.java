package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The player said, from the notice, to install the client mods this server sent them now. */
public record InstallKeptS2C() implements CustomPacketPayload {
    public static final Type<InstallKeptS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "install_kept"));

    public static final StreamCodec<ByteBuf, InstallKeptS2C> STREAM_CODEC =
        StreamCodec.unit(new InstallKeptS2C());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
