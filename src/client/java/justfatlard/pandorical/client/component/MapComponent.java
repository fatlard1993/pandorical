package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A map as a north-up HUD minimap. The {@code rotate} prop means the player holds a compass. */
public class MapComponent extends AbstractComponent {
    private static final float MAP_PIXELS = 128.0f;

    /** Vanilla's frame, margin and unexplored-area checkerboard in one texture. */
    private static final Identifier CHECKERBOARD_TEXTURE =
        Identifier.fromNamespaceAndPath("minecraft", "textures/map/map_background_checkerboard.png");

    // Vanilla's in-hand map background spans (-7,-7)..(135,135) around the 128px map.
    private static final float BORDER_TOTAL_MAP_PX = 7.0f;

    /** Vanilla's decoration scale, applied in screen space rather than map space. */
    private static final float MARKER_HALF_PX = 4.0f;

    /** Vanilla stores decoration facing in sixteenths of a turn. */
    private static final float DEGREES_PER_ROT_STEP = 360.0f / 16.0f;

    private final MapRenderState renderState = new MapRenderState();
    private int mapIdValue = -1;
    private boolean compass = false;
    private double compassTargetX = Double.NaN;
    private double compassTargetZ = Double.NaN;
    // Server-computed: the client does not know the map's centre.
    private byte selfDecX = 0;
    private byte selfDecY = 0;
    private byte compassDecX = 0;
    private byte compassDecY = 0;
    private record Dot(int decX, int decZ, int color, boolean person) {}

    private List<Dot> dots = List.of();
    private float zoom = 1.0f;
    private boolean showCoords = true;
    private boolean showHostile = true;
    private boolean showPassive = true;
    private Identifier needleTexture = null;
    private boolean compassOffMap = false;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        parseProps();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        parseProps();
    }

    private void parseProps() {
        mapIdValue = parseInt("map_id", -1);
        compass = parseBool("rotate", false);
        compassTargetX = parseCoord("compass_tx");
        compassTargetZ = parseCoord("compass_tz");
        selfDecX = parseByte("self_dec_x");
        selfDecY = parseByte("self_dec_y");
        compassDecX = parseByte("compass_dec_x");
        compassDecY = parseByte("compass_dec_y");
        dots = parseDots(props.getOrDefault("mobs", ""));
        needleTexture = Identifier.tryParse(props.getOrDefault("needle", ""));
        compassOffMap = parseBool("compass_off_map", false);
        zoom = parseFloat("zoom", 1.0f);
        showCoords = parseBool("show_coords", true);
        showHostile = parseBool("show_hostile", true);
        showPassive = parseBool("show_passive", true);
    }

    /** {@code decX,decZ,colorARGB,entityTypeId} entries, semicolon-separated; the type id may itself hold a colon. */
    private static List<Dot> parseDots(String raw) {
        if (raw.isEmpty()) return List.of();
        List<Dot> out = new ArrayList<>();
        for (String entry : raw.split(";")) {
            String[] parts = entry.split(",", 4);
            if (parts.length < 3) continue;
            try {
                out.add(new Dot(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    parts.length > 3 && parts[3].equals("minecraft:player")));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** NaN when absent or empty: no target. */
    private double parseCoord(String key) {
        String val = props.get(key);
        if (val == null || val.isEmpty()) return Double.NaN;
        try { return Double.parseDouble(val); } catch (NumberFormatException e) { return Double.NaN; }
    }

    private byte parseByte(String key) {
        String val = props.get(key);
        if (val == null || val.isEmpty()) return 0;
        try { return Byte.parseByte(val); } catch (NumberFormatException e) { return 0; }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (mapIdValue < 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        MapId mapId = new MapId(mapIdValue);
        MapItemSavedData mapData = MapItem.getSavedData(mapId, mc.level);
        if (mapData == null) return;

        mc.getMapRenderer().extractRenderState(mapId, mapData, renderState);

        // The frame stays inside the bounds, or an anchored minimap clips at the screen edge.
        int footprint = Math.min(width, height);
        int mapSize = Math.round(footprint * MAP_PIXELS / (MAP_PIXELS + 2f * BORDER_TOTAL_MAP_PX));
        int border = Math.max(1, (footprint - mapSize) / 2);
        int mapX = x + border;
        int mapY = y + border;
        float scale = mapSize / MAP_PIXELS;

        graphics.blit(RenderPipelines.GUI_TEXTURED, CHECKERBOARD_TEXTURE,
            x, y, 0.0F, 0.0F, footprint, footprint, footprint, footprint);

        // Decorations are drawn below in screen space, so graphics.map() must not draw them too.
        List<MapRenderState.MapDecorationRenderState> decorations = new ArrayList<>(renderState.decorations);
        renderState.decorations.clear();

        graphics.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);

        float zoomScale = scale * zoom;

        int clampedSelfDecX = Math.max(-127, Math.min(127, (int) selfDecX));
        int clampedSelfDecY = Math.max(-127, Math.min(127, (int) selfDecY));

        float originX, originY;
        if (zoom > 1.0f) {
            originX = mapX + mapSize / 2.0f - (clampedSelfDecX / 2.0f + 64f) * zoomScale;
            originY = mapY + mapSize / 2.0f - (clampedSelfDecY / 2.0f + 64f) * zoomScale;
        } else {
            originX = mapX;
            originY = mapY;
        }

        // The player marker goes through vanilla's map renderer, to match the in-hand map.
        MapRenderState.MapDecorationRenderState self = new MapRenderState.MapDecorationRenderState();
        self.atlasSprite = mapSprite(mc, "player");
        self.x = (byte) clampedSelfDecX;
        self.y = (byte) clampedSelfDecY;
        self.rot = (byte) Math.round(mc.player.getYRot() * 16.0F / 360.0F);
        self.renderOnFrame = true;
        renderState.decorations.add(self);

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(zoomScale, zoomScale);
        graphics.map(renderState);
        pose.popMatrix();

        for (Dot dot : dots) {
            // Filtered by the server's colour. A person's is their locator bar colour, any colour.
            if (!dot.person() && !showHostile && dot.color() == 0xFFFF3333) continue;
            if (!dot.person() && !showPassive && (dot.color() == 0xFF33FF33 || dot.color() == 0xFFFFAA00)) continue;

            if (Math.abs(dot.decX() - clampedSelfDecX) <= 1 && Math.abs(dot.decZ() - clampedSelfDecY) <= 1) continue;
            int sx = Math.round(originX + (dot.decX() / 2.0f + 64f) * zoomScale);
            int sy = Math.round(originY + (dot.decZ() / 2.0f + 64f) * zoomScale);
            if (sx < mapX || sx >= mapX + mapSize || sy < mapY || sy >= mapY + mapSize) continue;
            if (dot.person()) {
                diamond(graphics, sx, sy, dot.color());
            } else {
                graphics.fill(sx, sy, sx + 2, sy + 2, dot.color());
            }
        }

        // renderOnFrame=false marks the player-type decorations, which vanilla skips in a GUI too.
        for (MapRenderState.MapDecorationRenderState dec : decorations) {
            if (!dec.renderOnFrame || dec.atlasSprite == null) continue;

            float sx = originX + (dec.x / 2.0f + 64f) * zoomScale;
            float sy = originY + (dec.y / 2.0f + 64f) * zoomScale;
            if (sx < mapX || sx >= mapX + mapSize || sy < mapY || sy >= mapY + mapSize) continue;

            drawMarker(graphics, dec.atlasSprite, sx, sy, dec.rot * DEGREES_PER_ROT_STEP);
        }

        boolean hasCompassTarget = compass && !Double.isNaN(compassTargetX) && !Double.isNaN(compassTargetZ);
        if (hasCompassTarget) {
            float cpx = originX + (compassDecX / 2.0f + 64f) * zoomScale;
            float cpy = originY + (compassDecY / 2.0f + 64f) * zoomScale;
            if (cpx >= mapX && cpx < mapX + mapSize && cpy >= mapY && cpy < mapY + mapSize) {
                // Off the map the server has moved the point to the border along its bearing;
                // the marker turns to face outward there.
                float turn = compassOffMap ? (float) Math.toDegrees(Math.atan2(
                    compassTargetX - mapData.centerX, -(compassTargetZ - mapData.centerZ))) : 0f;
                drawMarker(graphics, mapSprite(mc, compassOffMap ? "target_x" : "target_point"),
                    cpx, cpy, turn);
            }
        }

        if (compass && needleTexture != null && hasCompassTarget) {
            int size = Math.max(8, mapSize / 5);
            int nx = mapX + 2;
            int ny = mapY + mapSize - size - 2;

            // From world coordinates: the decoration bytes are clamped to the map's border.
            float bearing = (float) Math.toDegrees(Math.atan2(
                compassTargetX - mc.player.getX(), -(compassTargetZ - mc.player.getZ())));
            // Relative to the player's facing: straight up is straight ahead.
            float facingFromNorth = mc.player.getYRot() + 180.0f;

            Matrix3x2fStack npose = graphics.pose();
            npose.pushMatrix();
            npose.translate(nx + size / 2.0f, ny + size / 2.0f);
            npose.rotate((float) Math.toRadians(bearing - facingFromNorth));
            npose.translate(-size / 2.0f, -size / 2.0f);
            graphics.blit(RenderPipelines.GUI_TEXTURED, needleTexture, 0, 0, 0.0F, 0.0F,
                size, size, size, size);
            npose.popMatrix();
        }

        graphics.disableScissor();

        if (!showCoords) return;
        float yaw = mc.player.getYRot();
        // Yaw 0 is south.
        float fromNorth = ((yaw + 180) % 360 + 360) % 360;
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        String facing = dirs[(int)((fromNorth + 22.5f) / 45f) % 8];
        int bx = mc.player.getBlockX(), by = mc.player.getBlockY(), bz = mc.player.getBlockZ();
        String coords = facing + "  " + bx + " / " + by + " / " + bz;
        // Height beyond the square map is room for the readout below it; otherwise it goes over.
        int spare = height - footprint;
        boolean below = spare >= mc.font.lineHeight + 1;
        int room = below ? footprint : mapSize;

        if (mc.font.width(coords) > room) coords = facing + " " + bx + "/" + by + "/" + bz;
        if (mc.font.width(coords) > room) coords = facing;
        if (mc.font.width(coords) > room) return;

        int textX = (below ? x : mapX) + (room - mc.font.width(coords)) / 2;
        int textY = below
            ? y + footprint + (spare - mc.font.lineHeight) / 2
            : mapY + mapSize - mc.font.lineHeight - 1;
        graphics.text(mc.font, coords, textX, textY, 0xFFFFFFFF, true);
    }

    /**
     * The transform and quad of {@code GuiGraphicsExtractor.map}, in screen pixels. The V flip is
     * in the UVs, not the quad: the GUI culls a quad wound the other way.
     */
    private static void drawMarker(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
                                    float cx, float cy, float rotDegrees) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(rotDegrees));
        pose.scale(MARKER_HALF_PX, MARKER_HALF_PX);
        pose.translate(-0.125f, 0.125f);
        // x0, x1, y0, y1: this overload takes its corners an axis at a time.
        graphics.blit(sprite.atlasLocation(), -1, 1, -1, 1,
            sprite.getU0(), sprite.getU1(), sprite.getV1(), sprite.getV0());
        pose.popMatrix();
    }

    /** A player: a diamond, since a locator bar colour can match a mob category's colour. */
    static void diamond(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
        diamondFill(graphics, cx, cy, 3, 0xE0101010);
        diamondFill(graphics, cx, cy, 2, color);
    }

    private static void diamondFill(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** Map decorations have their own atlas, not the GUI atlas blitSprite resolves against. */
    private static TextureAtlasSprite mapSprite(Minecraft mc, String name) {
        return mc.getAtlasManager()
            .getAtlasOrThrow(AtlasIds.MAP_DECORATIONS)
            .getSprite(Identifier.withDefaultNamespace(name));
    }
}
