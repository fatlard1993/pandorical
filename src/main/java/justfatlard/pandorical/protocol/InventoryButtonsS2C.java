package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import io.netty.handler.codec.DecoderException;

/**
 * Separate from {@link PlayerInventoryRegistrationsS2C}, whose shape older clients decode: a client
 * that has not declared this type is just not sent buttons.
 */
public record InventoryButtonsS2C(List<Button> buttons) implements CustomPacketPayload {

    public static final Type<InventoryButtonsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "inventory_buttons"));

    /**
     * @param screenX offset from the inventory panel's top-left, in GUI pixels
     * @param size    width and height; buttons are square
     * @param glyph   a character, drawn in place of a texture
     */
    public record Button(String namespace, String id, int screenX, int screenY, int size,
                         String glyph) {}

    private static final int MAX_BUTTONS = 32;

    public static final StreamCodec<ByteBuf, InventoryButtonsS2C> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public InventoryButtonsS2C decode(ByteBuf buf) {
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                if (count < 0 || count > MAX_BUTTONS) {
                    throw new DecoderException(
                        "inventory button count " + count + " exceeds " + MAX_BUTTONS);
                }
                List<Button> buttons = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    buttons.add(new Button(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)));
                }
                return new InventoryButtonsS2C(buttons);
            }

            @Override
            public void encode(ByteBuf buf, InventoryButtonsS2C value) {
                ByteBufCodecs.VAR_INT.encode(buf, value.buttons().size());
                for (Button b : value.buttons()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, b.namespace());
                    ByteBufCodecs.STRING_UTF8.encode(buf, b.id());
                    ByteBufCodecs.VAR_INT.encode(buf, b.screenX());
                    ByteBufCodecs.VAR_INT.encode(buf, b.screenY());
                    ByteBufCodecs.VAR_INT.encode(buf, b.size());
                    ByteBufCodecs.STRING_UTF8.encode(buf, b.glyph());
                }
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
