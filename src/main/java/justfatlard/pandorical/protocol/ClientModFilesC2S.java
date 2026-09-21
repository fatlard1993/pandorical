package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Which jars a player's own mods folder holds, and which of them are switched off.
 *
 * <p>Separate from the settings a client declares, because a mod that is switched off declares
 * nothing: the loader never saw it, so its name and version have to be read out of the jar by the
 * client itself. Without this a disabled mod is invisible, and the only way back is to go
 * and find the file.
 */
public record ClientModFilesC2S(List<Entry> entries) implements CustomPacketPayload {

    /**
     * @param id      the mod's id, as its own metadata gives it
     * @param name    its display name
     * @param version its version
     * @param file    the file name in the mods folder, which is the handle for switching it
     * @param enabled false for a jar renamed out of the loader's way
     */
    public record Entry(String id, String name, String version, String file, boolean enabled) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), Entry::id,
            ByteBufCodecs.stringUtf8(128), Entry::name,
            ByteBufCodecs.stringUtf8(64), Entry::version,
            ByteBufCodecs.stringUtf8(256), Entry::file,
            ByteBufCodecs.BOOL, Entry::enabled,
            Entry::new
        );
    }

    public static final Type<ClientModFilesC2S> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "client_mod_files"));

    public static final StreamCodec<ByteBuf, ClientModFilesC2S> STREAM_CODEC = StreamCodec.composite(
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list(256)), ClientModFilesC2S::entries,
        ClientModFilesC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
