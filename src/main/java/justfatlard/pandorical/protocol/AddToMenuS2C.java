package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A command the player asked to keep, on its way to the screen that asks which menu it goes on.
 *
 * <p>Its own channel rather than a settings write, because the two are not the same thing: a
 * setting is a value the player chose from a list the client drew, and this is a command the
 * server names. Keeping them apart is what stops a server writing an arbitrary command into the
 * player's menus by dressing it as a setting.
 *
 * <p>A client too old to know this channel never registers it, so the server finds it cannot send
 * and leaves the Add button off the screen. Nobody has to update for it.
 */
public record AddToMenuS2C(String command) implements CustomPacketPayload {

    public static final Type<AddToMenuS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "add_to_menu"));

    public static final StreamCodec<ByteBuf, AddToMenuS2C> STREAM_CODEC =
        ByteBufCodecs.stringUtf8(512).map(AddToMenuS2C::new, AddToMenuS2C::command);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
