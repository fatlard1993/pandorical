package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public record OpenScreenS2C(
    String screenId,
    String screenType,
    int width,
    int height,
    boolean pauseGame,
    String title,
    List<ComponentDef> components,
    Optional<ContainerDef> container
) implements CustomPacketPayload {
    public static final Type<OpenScreenS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "open_screen"));

    public static final StreamCodec<ByteBuf, OpenScreenS2C> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OpenScreenS2C decode(ByteBuf buf) {
            String screenId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String screenType = ByteBufCodecs.STRING_UTF8.decode(buf);
            int width = ByteBufCodecs.VAR_INT.decode(buf);
            int height = ByteBufCodecs.VAR_INT.decode(buf);
            boolean pauseGame = ByteBufCodecs.BOOL.decode(buf);
            String title = ByteBufCodecs.STRING_UTF8.decode(buf);
            int compCount = ByteBufCodecs.VAR_INT.decode(buf);
            List<ComponentDef> components = new java.util.ArrayList<>(compCount);
            for (int i = 0; i < compCount; i++) {
                components.add(ComponentDef.STREAM_CODEC.decode(buf));
            }
            boolean hasContainer = ByteBufCodecs.BOOL.decode(buf);
            Optional<ContainerDef> container = hasContainer
                ? Optional.of(ContainerDef.STREAM_CODEC.decode(buf))
                : Optional.empty();
            return new OpenScreenS2C(screenId, screenType, width, height, pauseGame, title, components, container);
        }

        @Override
        public void encode(ByteBuf buf, OpenScreenS2C payload) {
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.screenId());
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.screenType());
            ByteBufCodecs.VAR_INT.encode(buf, payload.width());
            ByteBufCodecs.VAR_INT.encode(buf, payload.height());
            ByteBufCodecs.BOOL.encode(buf, payload.pauseGame());
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.title());
            ByteBufCodecs.VAR_INT.encode(buf, payload.components().size());
            for (ComponentDef comp : payload.components()) {
                ComponentDef.STREAM_CODEC.encode(buf, comp);
            }
            ByteBufCodecs.BOOL.encode(buf, payload.container().isPresent());
            payload.container().ifPresent(c -> ContainerDef.STREAM_CODEC.encode(buf, c));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
