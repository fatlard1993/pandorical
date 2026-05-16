package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Map;

/**
 * Universal component descriptor. The server describes UI as a tree of these;
 * the client instantiates the matching PandoricalComponent for each one.
 */
public record ComponentDef(
    String id,
    String type,
    int x,
    int y,
    int width,
    int height,
    Map<String, String> props,
    List<ComponentDef> children
) {
    private static final StreamCodec<ByteBuf, Map<String, String>> STRING_MAP_CODEC =
        ByteBufCodecs.map(
            java.util.HashMap::new,
            ByteBufCodecs.STRING_UTF8,
            ByteBufCodecs.STRING_UTF8
        );

    private static final int MAX_DEPTH = 32;
    private static final int MAX_CHILDREN = 256;
    private static final int MAX_PROPS = 64;

    public static final StreamCodec<ByteBuf, ComponentDef> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ComponentDef decode(ByteBuf buf) {
            return decodeWithDepth(buf, 0);
        }

        private ComponentDef decodeWithDepth(ByteBuf buf, int depth) {
            if (depth > MAX_DEPTH) {
                throw new io.netty.handler.codec.DecoderException(
                    "ComponentDef tree exceeds max depth of " + MAX_DEPTH);
            }
            String id = ByteBufCodecs.STRING_UTF8.decode(buf);
            String type = ByteBufCodecs.STRING_UTF8.decode(buf);
            int x = ByteBufCodecs.VAR_INT.decode(buf);
            int y = ByteBufCodecs.VAR_INT.decode(buf);
            int width = ByteBufCodecs.VAR_INT.decode(buf);
            int height = ByteBufCodecs.VAR_INT.decode(buf);
            Map<String, String> props = STRING_MAP_CODEC.decode(buf);
            if (props.size() > MAX_PROPS) {
                throw new io.netty.handler.codec.DecoderException(
                    "ComponentDef has " + props.size() + " props, max is " + MAX_PROPS);
            }
            int childCount = ByteBufCodecs.VAR_INT.decode(buf);
            if (childCount < 0 || childCount > MAX_CHILDREN) {
                throw new io.netty.handler.codec.DecoderException(
                    "ComponentDef has " + childCount + " children, max is " + MAX_CHILDREN);
            }
            List<ComponentDef> children = new java.util.ArrayList<>(childCount);
            for (int i = 0; i < childCount; i++) {
                children.add(decodeWithDepth(buf, depth + 1));
            }
            return new ComponentDef(id, type, x, y, width, height, props, children);
        }

        @Override
        public void encode(ByteBuf buf, ComponentDef def) {
            ByteBufCodecs.STRING_UTF8.encode(buf, def.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, def.type());
            ByteBufCodecs.VAR_INT.encode(buf, def.x());
            ByteBufCodecs.VAR_INT.encode(buf, def.y());
            ByteBufCodecs.VAR_INT.encode(buf, def.width());
            ByteBufCodecs.VAR_INT.encode(buf, def.height());
            STRING_MAP_CODEC.encode(buf, def.props());
            ByteBufCodecs.VAR_INT.encode(buf, def.children().size());
            for (ComponentDef child : def.children()) {
                ComponentDef.STREAM_CODEC.encode(buf, child);
            }
        }
    };
}
