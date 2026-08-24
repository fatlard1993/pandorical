package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A press on one of the buttons from {@link InventoryButtonsS2C}.
 *
 * <p>Not a screen action: those are checked against the pandorical screen the player has open,
 * and the whole point of these is that they live on the vanilla inventory, which is nobody's.
 */
public record InventoryButtonC2S(String namespace, String id) implements CustomPacketPayload {

    public static final Type<InventoryButtonC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "inventory_button"));

    public static final StreamCodec<ByteBuf, InventoryButtonC2S> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(128), InventoryButtonC2S::namespace,
        ByteBufCodecs.stringUtf8(128), InventoryButtonC2S::id,
        InventoryButtonC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
