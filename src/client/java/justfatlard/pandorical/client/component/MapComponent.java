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

/**
 * Renders a Minecraft map as a HUD minimap component. Always north-up.
 * Props:
 *   map_id: integer map ID to render
 *   rotate: "true" when compass is equipped, draws player dot as directional arrow
 */
public class MapComponent extends AbstractComponent {
    private static final float MAP_PIXELS = 128.0f;

    /** The vanilla frame art: border plus the unexplored-area checkerboard. */
    private static final Identifier CHECKERBOARD_TEXTURE =
        Identifier.fromNamespaceAndPath("minecraft", "textures/map/map_background_checkerboard.png");

    // Vanilla 26.3 map frame geometry, measured from the snapshot jar:
    // FirstPersonHandsAndItemsRenderer.renderMap draws the background quad over
    // (-7,-7)..(135,135), i.e. a 7 map-px margin around the 128px map content.
    private static final float BORDER_TOTAL_MAP_PX = 7.0f;

    /**
     * Half the on-screen size of a marker. Vanilla scales its decoration quad by
     * 4 in map space; here the same 4 is applied in screen space instead, so a
     * marker stays legible on a minimap a third of a full map's size.
     */
    private static final float MARKER_HALF_PX = 4.0f;

    /** Vanilla stores decoration facing in sixteenths of a turn. */
    private static final float DEGREES_PER_ROT_STEP = 360.0f / 16.0f;

    private final MapRenderState renderState = new MapRenderState();
    private int mapIdValue = -1;
    private boolean compass = false;
    private double compassTargetX = Double.NaN;
    private double compassTargetZ = Double.NaN;
    // Self decoration bytes sent from server (client can't compute them without map center)
    private byte selfDecX = 0;
    private byte selfDecY = 0;
    // Compass target as stable map dec bytes (server-computed; avoids edge drift)
    private byte compassDecX = 0;
    private byte compassDecY = 0;
    // Mob Sight enchantment: serialized mob dot list from server
    private String mobsData = "";
    /** How the map is shown, as the server says for this player: nothing here is the client's own choice. */
    private float zoom = 1.0f;
    private boolean showCoords = true;
    private boolean showHostile = true;
    private boolean showPassive = true;
    /** A heading arrow to lay in the corner, when the server supplies one. */
    private Identifier needleTexture = null;
    /** Whether the compass is pointing past the edge of this map. */
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
        compass = parseBool("rotate", false); // the "rotate" prop carries "has compass"
        compassTargetX = parseCoord("compass_tx");
        compassTargetZ = parseCoord("compass_tz");
        selfDecX = parseByte("self_dec_x");
        selfDecY = parseByte("self_dec_y");
        compassDecX = parseByte("compass_dec_x");
        compassDecY = parseByte("compass_dec_y");
        mobsData = props.getOrDefault("mobs", "");
        needleTexture = Identifier.tryParse(props.getOrDefault("needle", ""));
        compassOffMap = parseBool("compass_off_map", false);
        zoom = parseFloat("zoom", 1.0f);
        showCoords = parseBool("show_coords", true);
        showHostile = parseBool("show_hostile", true);
        showPassive = parseBool("show_passive", true);
    }

    /**
     * Parse a world coordinate prop. Returns NaN if the prop is absent or empty (no target).
     */
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

        // The frame is part of the component, not an overhang. Painting it outside
        // the declared bounds put it past whichever screen edge the minimap was
        // anchored to, and the anchored edges are the ones that got clipped.
        int footprint = Math.min(width, height);
        int mapSize = Math.round(footprint * MAP_PIXELS / (MAP_PIXELS + 2f * BORDER_TOTAL_MAP_PX));
        int border = Math.max(1, (footprint - mapSize) / 2);
        int mapX = x + border;
        int mapY = y + border;
        float scale = mapSize / MAP_PIXELS;

        // Vanilla frame + backdrop in one stretched blit of the actual 26.3 map art:
        // map_background_checkerboard.png carries the brown outline, tan margin, AND
        // the two-tone checkerboard vanilla shows behind unexplored map pixels.
        graphics.blit(RenderPipelines.GUI_TEXTURED, CHECKERBOARD_TEXTURE,
            x, y, 0.0F, 0.0F, footprint, footprint, footprint, footprint);

        // Decorations are drawn below, in screen space. Handing them to graphics.map()
        // as well drew every landmark twice: vanilla's sprite first, then ours on top
        // of it, which is what made a treasure X read as a white smudge.
        List<MapRenderState.MapDecorationRenderState> decorations = new ArrayList<>(renderState.decorations);
        renderState.decorations.clear();

        graphics.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);

        float zoomScale = scale * zoom;

        // Belt-and-suspenders clamp of self decoration bytes
        int clampedSelfDecX = Math.max(-127, Math.min(127, (int) selfDecX));
        int clampedSelfDecY = Math.max(-127, Math.min(127, (int) selfDecY));

        // When zoom > 1 we centre on the player; at 1x top-left is the map corner
        float originX, originY;
        if (zoom > 1.0f) {
            originX = mapX + mapSize / 2.0f - (clampedSelfDecX / 2.0f + 64f) * zoomScale;
            originY = mapY + mapSize / 2.0f - (clampedSelfDecY / 2.0f + 64f) * zoomScale;
        } else {
            originX = mapX;
            originY = mapY;
        }

        // Drawn by vanilla rather than by us. It is the same sprite either way, but the
        // transform, the scale and the way the quad is sampled then come from the map renderer
        // instead of from a copy of it down here - so the marker on the minimap is the marker on
        // the map in your hand, and stays that way when Mojang next changes it.
        MapRenderState.MapDecorationRenderState self = new MapRenderState.MapDecorationRenderState();
        self.atlasSprite = mapSprite(mc, "player");
        self.x = (byte) clampedSelfDecX;
        self.y = (byte) clampedSelfDecY;
        self.rot = (byte) Math.round(mc.player.getYRot() * 16.0F / 360.0F);
        self.renderOnFrame = true;
        renderState.decorations.add(self);

        // Map always north-up
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(zoomScale, zoomScale);
        graphics.map(renderState);
        pose.popMatrix();

        // --- Mob Sight: mob dots render first so the player marker lands on top ---
        if (!mobsData.isEmpty()) {
            String[] entries = mobsData.split(";");
            for (String entry : entries) {
                // Format: decX,decZ,colorARGB,entityTypeId
                // Split on first 3 commas only so entityTypeId (which may contain ':') is kept intact
                String[] parts = entry.split(",", 4);
                if (parts.length < 3) continue;
                try {
                    int decX = Integer.parseInt(parts[0]);
                    int decZ = Integer.parseInt(parts[1]);
                    int color = Integer.parseInt(parts[2]);

                    // Category filters, by the colour the server gave the dot
                    if (!showHostile && color == 0xFFFF3333) continue;
                    if (!showPassive && (color == 0xFF33FF33 || color == 0xFFFFAA00)) continue;

                    if (Math.abs(decX - clampedSelfDecX) <= 1 && Math.abs(decZ - clampedSelfDecY) <= 1) continue;
                    int sx = Math.round(originX + (decX / 2.0f + 64f) * zoomScale);
                    int sy = Math.round(originY + (decZ / 2.0f + 64f) * zoomScale);
                    if (sx < mapX || sx >= mapX + mapSize || sy < mapY || sy >= mapY + mapSize) continue;
                    // 2x2 dot; small enough not to obscure map detail
                    graphics.fill(sx, sy, sx + 2, sy + 2, color);
                } catch (NumberFormatException ignored) {}
            }
        }

        // --- Landmarks, banners, treasure X: vanilla's own sprites ---
        // renderOnFrame=false is the player-type set, which vanilla itself skips in a
        // GUI; ours is drawn from the server props above and other players need
        // tracking we do not have.
        for (MapRenderState.MapDecorationRenderState dec : decorations) {
            if (!dec.renderOnFrame || dec.atlasSprite == null) continue;

            float sx = originX + (dec.x / 2.0f + 64f) * zoomScale;
            float sy = originY + (dec.y / 2.0f + 64f) * zoomScale;
            if (sx < mapX || sx >= mapX + mapSize || sy < mapY || sy >= mapY + mapSize) continue;

            drawMarker(graphics, dec.atlasSprite, sx, sy, dec.rot * DEGREES_PER_ROT_STEP);
        }

        // --- Compass destination marker: vanilla target_point, centred on the target ---
        boolean hasCompassTarget = compass && !Double.isNaN(compassTargetX) && !Double.isNaN(compassTargetZ);
        if (hasCompassTarget) {
            float cpx = originX + (compassDecX / 2.0f + 64f) * zoomScale;
            float cpy = originY + (compassDecY / 2.0f + 64f) * zoomScale;
            if (cpx >= mapX && cpx < mapX + mapSize && cpy >= mapY && cpy < mapY + mapSize) {
                // Past the edge the server has already walked the point back along its own
                // bearing to the border, so the marker sits where the thing actually lies rather
                // than in whichever corner two independent clamps happened to meet. Turned to
                // face outward there, which is the only way a marker on a border says "further".
                float turn = compassOffMap ? (float) Math.toDegrees(Math.atan2(
                    compassTargetX - mapData.centerX, -(compassTargetZ - mapData.centerZ))) : 0f;
                drawMarker(graphics, mapSprite(mc, compassOffMap ? "target_x" : "target_point"),
                    cpx, cpy, turn);
            }
        }

        // The heading arrow, small, in the corner the map has least to say in. Only with a
        // compass: without one there is no heading to point along and an arrow would be a
        // decoration pretending to be information.
        if (compass && needleTexture != null && hasCompassTarget) {
            int size = Math.max(8, mapSize / 5);
            int nx = mapX + 2;
            int ny = mapY + mapSize - size - 2;

            // Taken from world coordinates, not from the map's decoration bytes.
            //
            // Those bytes are the target's position ON THE MAP, and a target off the map has
            // already been walked back to the border before they are written - so past the edge
            // the needle was reading the border point rather than the thing, and pointing at a
            // spot on the frame. Worse the further out it was: the clamped point converges on
            // the map's own rim, so out of range the needle swung toward the rim regardless of
            // where the target actually lay, and near the rim the difference against the
            // player's own clamped position collapsed toward zero and the heading became noise.
            //
            // The real coordinates are already here, sent beside those bytes, and they are
            // subject to no clamp, no map scale and no edge. The needle is a heading; it should
            // never have been asking the map.
            float bearing = (float) Math.toDegrees(Math.atan2(
                compassTargetX - mc.player.getX(), -(compassTargetZ - mc.player.getZ())));

            Matrix3x2fStack npose = graphics.pose();
            npose.pushMatrix();
            npose.translate(nx + size / 2.0f, ny + size / 2.0f);
            npose.rotate((float) Math.toRadians(bearing));
            npose.translate(-size / 2.0f, -size / 2.0f);
            graphics.blit(RenderPipelines.GUI_TEXTURED, needleTexture, 0, 0, 0.0F, 0.0F,
                size, size, size, size);
            npose.popMatrix();
        }

        graphics.disableScissor();

        // --- Facing direction + coordinates, inside the map so the component
        // --- never paints outside the bounds it told the layout it occupies.
        if (!showCoords) return;
        float yaw = mc.player.getYRot();
        // Convert MC yaw (0=south) to degrees-from-north clockwise
        float fromNorth = ((yaw + 180) % 360 + 360) % 360;
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        String facing = dirs[(int)((fromNorth + 22.5f) / 45f) % 8];
        int bx = mc.player.getBlockX(), by = mc.player.getBlockY(), bz = mc.player.getBlockZ();
        // Centring alone does not keep the readout inside a small map: step down to
        // shorter forms until one fits, and draw nothing rather than overflow.
        String coords = facing + "  " + bx + " / " + by + " / " + bz;
        // Below the map when the component was handed room for it, over the map when it was not.
        // The map is square at min(width, height), so any height beyond that is space the caller
        // asked for on purpose, and a readout sitting under the frame beats one painted across
        // the ground it is describing. A caller that declares a square still gets the old
        // placement rather than text over the edge of its own bounds.
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
     * Draw a map decoration the way vanilla draws it, in screen pixels.
     *
     * <p>The transform is lifted from {@code GuiGraphicsExtractor.map}: translate to
     * the marker, rotate, scale, then nudge by an eighth of a pixel. Vanilla samples
     * the sprite with its V coordinates swapped, so the quad is flipped in Y here to
     * match; without that the arrow points the wrong way down its own axis.
     */
    private static void drawMarker(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite,
                                    float cx, float cy, float rotDegrees) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(rotDegrees));
        pose.scale(MARKER_HALF_PX, MARKER_HALF_PX);
        pose.translate(-0.125f, 0.125f);
        pose.scale(1f, -1f);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, -1, -1, 2, 2);
        pose.popMatrix();
    }

    /**
     * Map decorations live in their own atlas in 26.3, not the GUI atlas the
     * Identifier blitSprite overload resolves against, so the sprite is fetched
     * from that atlas explicitly.
     */
    private static TextureAtlasSprite mapSprite(Minecraft mc, String name) {
        return mc.getAtlasManager()
            .getAtlasOrThrow(AtlasIds.MAP_DECORATIONS)
            .getSprite(Identifier.withDefaultNamespace(name));
    }
}
