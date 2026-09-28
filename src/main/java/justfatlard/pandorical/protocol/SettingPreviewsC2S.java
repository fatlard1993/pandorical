package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The picture for each value of a setting whose options are shapes rather than words.
 *
 * <p>Its own channel rather than a field on {@code client_settings}, which is already in players'
 * hands: a reader that finds bytes left over is disconnected, so one more field there would kick
 * every client running a released Pandorical. A client too old to send this simply does not, and
 * its settings are drawn with their names alone, the way they are now.
 *
 * <p>Sent after the settings themselves, so the server has somewhere to put them.
 */
public record SettingPreviewsC2S(List<Entry> entries) implements CustomPacketPayload {

    /**
     * @param modId   the mod the setting belongs to
     * @param key     the setting's key within that mod
     * @param value   the option id this picture is for
     * @param texture a full resource path with its extension, drawn beside the control
     */
    public record Entry(String modId, String key, String value, String texture) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), Entry::modId,
            ByteBufCodecs.stringUtf8(64), Entry::key,
            ByteBufCodecs.stringUtf8(64), Entry::value,
            ByteBufCodecs.stringUtf8(256), Entry::texture,
            Entry::new
        );
    }

    public static final Type<SettingPreviewsC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "setting_previews"));

    public static final StreamCodec<ByteBuf, SettingPreviewsC2S> STREAM_CODEC = StreamCodec.composite(
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list(1024)), SettingPreviewsC2S::entries,
        SettingPreviewsC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
