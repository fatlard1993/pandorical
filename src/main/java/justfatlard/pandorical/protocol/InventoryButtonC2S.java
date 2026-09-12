package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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
