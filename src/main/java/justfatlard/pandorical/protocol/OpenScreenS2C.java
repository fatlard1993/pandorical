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
    Optional<ContainerDef> container,
    Optional<String> recipeStation
) implements CustomPacketPayload {

    /**
     * A screen that is a crafting station, and the recipe book category it works from.
     *
     * <p>A Pandorical screen has no vanilla recipe book and cannot have one: the book is bolted
     * to {@code RecipeBookMenu}, and this is not one. So a station says what it is and leaves the
     * showing to whoever is listening - the client's own book, or a mod that replaces it. Without
     * this a fletching table could only ever browse its recipes by growing a browser of its own,
     * which is how the last one ended up as a grid of two-letter buttons.
     *
     * <p>An id, not a category object: the client resolves it against its own registry, and a
     * client that has never heard of the category simply shows nothing rather than failing.
     */
    public OpenScreenS2C(String screenId, String screenType, int width, int height,
            boolean pauseGame, String title, List<ComponentDef> components,
            Optional<ContainerDef> container) {
        this(screenId, screenType, width, height, pauseGame, title, components, container,
            Optional.empty());
    }
    public static final Type<OpenScreenS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "open_screen"));

    /** Matches ComponentDef.MAX_CHILDREN: a screen root is not more permissive than a node. */
    private static final int MAX_ROOT_COMPONENTS = 256;

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
            // Never size the list from the wire: ComponentDef caps its own children and
            // depth, but this root count was allocating before a single component decoded.
            if (compCount < 0 || compCount > MAX_ROOT_COMPONENTS) {
                throw new io.netty.handler.codec.DecoderException(
                    "OpenScreenS2C component count " + compCount + " exceeds " + MAX_ROOT_COMPONENTS);
            }
            List<ComponentDef> components = new java.util.ArrayList<>();
            for (int i = 0; i < compCount; i++) {
                components.add(ComponentDef.STREAM_CODEC.decode(buf));
            }
            boolean hasContainer = ByteBufCodecs.BOOL.decode(buf);
            Optional<ContainerDef> container = hasContainer
                ? Optional.of(ContainerDef.STREAM_CODEC.decode(buf))
                : Optional.empty();

            Optional<String> recipeStation = ByteBufCodecs.BOOL.decode(buf)
                ? Optional.of(ByteBufCodecs.STRING_UTF8.decode(buf))
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

            ByteBufCodecs.BOOL.encode(buf, payload.recipeStation().isPresent());
            payload.recipeStation().ifPresent(id -> ByteBufCodecs.STRING_UTF8.encode(buf, id));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
