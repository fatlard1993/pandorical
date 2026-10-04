package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The player pressed use on a block of a walkable structure: which structure, and which of its
 * blocks, as the client drew them. The client's pick rather than the server's own, because a moving
 * structure is drawn a little behind where the server has it, and the server's own look along the
 * player's view would land a block or more off the one they clicked. The server holds it to reach,
 * the same as vanilla holds a click on a block in the world.
 */
public record UseStructureC2S(String structureId, int x, int y, int z) implements CustomPacketPayload {
    public static final Type<UseStructureC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "use_structure"));
    public static final StreamCodec<ByteBuf, UseStructureC2S> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(256), UseStructureC2S::structureId,
        ByteBufCodecs.VAR_INT, UseStructureC2S::x,
        ByteBufCodecs.VAR_INT, UseStructureC2S::y,
        ByteBufCodecs.VAR_INT, UseStructureC2S::z,
        UseStructureC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
