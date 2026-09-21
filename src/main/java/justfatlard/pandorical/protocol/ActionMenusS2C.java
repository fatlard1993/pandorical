package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Menus the server thinks this player would want, offered once each.
 *
 * <p>A suggestion and not an instruction: the client adds a menu it has never been offered before
 * and records the id, so a player who deletes, renames or rearranges one is never given it back.
 * Action menus are the player's own, and a server that could rewrite them every login would make
 * them the server's.
 *
 * <p>A separate payload, so an older client is never sent it.
 */
public record ActionMenusS2C(List<Menu> menus) implements CustomPacketPayload {
    public ActionMenusS2C {
        menus = Wire.fit(menus, 64, "action menus");
    }


    /**
     * @param id      how the client remembers having been offered this menu; stable across renames
     * @param name    what it is called when it arrives
     * @param key     an InputConstants key name to open it with, e.g. {@code key.keyboard.j}, or
     *                empty for none - the player picks one
     * @param buttons in the order they should sit in the grid
     */
    public record Menu(String id, String name, String key, List<Button> buttons) {
        /** Trimmed here rather than thrown on at encode, which would disconnect the player. */
        public Menu {
            id = Wire.fit(id, 128, "an action menu id");
            name = Wire.fit(name, 128, "an action menu name");
            key = Wire.fit(key, 64, "an action menu key");
            buttons = Wire.fit(buttons, 256, "buttons on one action menu");
        }

        public static final StreamCodec<ByteBuf, Menu> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), Menu::id,
            ByteBufCodecs.stringUtf8(128), Menu::name,
            ByteBufCodecs.stringUtf8(64), Menu::key,
            Button.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Menu::buttons,
            Menu::new
        );
    }

    /**
     * @param icon       an item id, drawn on the button
     * @param label      the word under it
     * @param command    what it runs, with or without the leading slash; empty for a key button
     * @param keyMapping the key mapping it presses, e.g. {@code key.pandorical.action3}; empty for
     *                   a command button
     */
    public record Button(String icon, String label, String command, String keyMapping) {
        public Button {
            icon = Wire.fit(icon, 256, "an action button icon");
            label = Wire.fit(label, 128, "an action button label");
            command = Wire.fit(command, 512, "an action button command");
            keyMapping = Wire.fit(keyMapping, 128, "an action button key mapping");
        }

        public static final StreamCodec<ByteBuf, Button> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), Button::icon,
            ByteBufCodecs.stringUtf8(128), Button::label,
            ByteBufCodecs.stringUtf8(512), Button::command,
            ByteBufCodecs.stringUtf8(128), Button::keyMapping,
            Button::new
        );
    }

    public static final Type<ActionMenusS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "action_menus"));

    public static final StreamCodec<ByteBuf, ActionMenusS2C> STREAM_CODEC = StreamCodec.composite(
        Menu.STREAM_CODEC.apply(ByteBufCodecs.list(64)), ActionMenusS2C::menus,
        ActionMenusS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
