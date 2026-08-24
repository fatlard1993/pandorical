package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Buttons a server mod wants on the player's own inventory screen.
 *
 * <p>Its own payload rather than another field on {@link PlayerInventoryRegistrationsS2C},
 * deliberately. Adding to that record would change bytes a client already knows how to read, and
 * a client one version behind would fail to decode the packet that gives it its extra slots -
 * losing the map and compass slots to gain a button. A separate payload is simply never sent to a
 * client that has not declared it: no buttons there, and everything else exactly as before.
 */
public record InventoryButtonsS2C(List<Button> buttons) implements CustomPacketPayload {

    public static final Type<InventoryButtonsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "inventory_buttons"));

    /**
     * One button.
     *
     * @param namespace who registered it, so a press can be routed back to them
     * @param id        their own name for it
     * @param screenX   offset from the inventory panel's top-left, in gui pixels
     * @param size      width and height; these are square
     * @param glyph     what to draw on it - a character, not a texture, so a mod needs no art
     */
    public record Button(String namespace, String id, int screenX, int screenY, int size,
                         String glyph) {}

    /** A sane ceiling: the inventory screen has room for a handful, not a toolbar. */
    private static final int MAX_BUTTONS = 32;

    public static final StreamCodec<ByteBuf, InventoryButtonsS2C> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public InventoryButtonsS2C decode(ByteBuf buf) {
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                if (count < 0 || count > MAX_BUTTONS) {
                    throw new io.netty.handler.codec.DecoderException(
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
