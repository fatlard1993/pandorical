package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Server tells client to adjust camera behavior.
 * Types: "distance" (params: distance), "perspective" (params: mode), "reset"
 */
public record CameraHintS2C(
    String hintType,
    Map<String, String> params
) implements CustomPacketPayload {
    public static final Type<CameraHintS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "camera_hint"));

    public static final StreamCodec<ByteBuf, CameraHintS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, CameraHintS2C::hintType,
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), CameraHintS2C::params,
        CameraHintS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
