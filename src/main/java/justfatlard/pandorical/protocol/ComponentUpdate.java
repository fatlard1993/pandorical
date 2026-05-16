package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;

/**
 * Partial property update for a live component. Only changed props are sent.
 */
public record ComponentUpdate(
    String componentId,
    Map<String, String> changedProps
) {
    public static final StreamCodec<ByteBuf, ComponentUpdate> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ComponentUpdate::componentId,
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), ComponentUpdate::changedProps,
        ComponentUpdate::new
    );
}
