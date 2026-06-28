package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;
import org.joml.Matrix3x2fStack;

import java.util.Map;

/**
 * Renders a Minecraft map as a HUD minimap component. Always north-up.
 * Props:
 *   map_id  — integer map ID to render
 *   rotate  — "true" when compass is equipped: draws player dot as directional arrow
 */
public class MapComponent extends AbstractComponent {
    private static final float MAP_PIXELS = 128.0f;

    // Vanilla map border colors sampled from textures/map/map_background.png
    private static final int BORDER_OUTER = 0xFF99876C; // brown
    private static final int BORDER_INNER = 0xFFD6BE96; // light tan
    private static final int MARKER_COLOR = 0xFFFFFFFF; // white default

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
        compass = parseBool("rotate", false); // prop reused: "rotate" now means "has compass"
        compassTargetX = parseCoord("compass_tx");
        compassTargetZ = parseCoord("compass_tz");
        selfDecX = parseByte("self_dec_x");
        selfDecY = parseByte("self_dec_y");
        compassDecX = parseByte("compass_dec_x");
        compassDecY = parseByte("compass_dec_y");
        mobsData = props.getOrDefault("mobs", "");
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

        // Vanilla-style 2px border (brown outer, tan inner)
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, BORDER_OUTER);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, BORDER_INNER);

        graphics.enableScissor(x, y, x + width, y + height);

        float scale = Math.min(width, height) / MAP_PIXELS;

        // --- Zoom support ---
        float zoomLevel = MapDisplaySettings.getZoomLevel();
        float zoomScale = scale * zoomLevel;

        // Belt-and-suspenders clamp of self decoration bytes
        int clampedSelfDecX = Math.max(-127, Math.min(127, (int) selfDecX));
        int clampedSelfDecY = Math.max(-127, Math.min(127, (int) selfDecY));

        // When zoom > 1 we centre on the player; at 1× top-left is (x, y) as before
        float originX, originY;
        if (zoomLevel > 1.0f) {
            originX = x + width  / 2.0f - (clampedSelfDecX / 2.0f + 64f) * zoomScale;
            originY = y + height / 2.0f - (clampedSelfDecY / 2.0f + 64f) * zoomScale;
        } else {
            originX = x;
            originY = y;
        }

        // Map always north-up
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(zoomScale, zoomScale);
        graphics.map(renderState);
        pose.popMatrix();

        // --- Self dot/arrow: rendered from server-sent selfDecX/Y (always current) ---
        // The client-side mapData decoration bytes are stale (addClientSideDecorations doesn't
        // update reliably for our custom slot), so we use server-authoritative position props instead.
        int selfSi, selfSj;
        if (zoomLevel > 1.0f) {
            selfSi = Math.round(x + width  / 2.0f);
            selfSj = Math.round(y + height / 2.0f);
        } else {
            selfSi = Math.round(x + (clampedSelfDecX / 2.0f + 64f) * scale);
            selfSj = Math.round(y + (clampedSelfDecY / 2.0f + 64f) * scale);
        }

        // Player arrow always shows facing direction (yaw=0=south points down, yaw=90=west points left)
        float yawRad = (float) Math.toRadians(mc.player.getYRot());
        float facingDirX = -(float) Math.sin(yawRad);
        float facingDirY =  (float) Math.cos(yawRad);

        // --- Compass: smooth screen-space offset from the player dot ---
        // Compute as (compassTarget - player) in world space → screen pixels.
        // Using selfDecX/Y as base causes vibration because selfDecX is quantized (server ticks)
        // while mc.player.getX() is smooth (every frame). Pure offset avoids the mismatch.
        float compassScreenDx = 0, compassScreenDy = 0;
        boolean hasCompassTarget = compass && !Double.isNaN(compassTargetX) && !Double.isNaN(compassTargetZ);
        if (hasCompassTarget) {
            float scaleFactor = 1 << mapData.scale;
            // World-space delta → map-pixel delta → screen-pixel delta (smooth, no quantization)
            compassScreenDx = (float)((compassTargetX - mc.player.getX()) / scaleFactor) * zoomScale;
            compassScreenDy = (float)((compassTargetZ - mc.player.getZ()) / scaleFactor) * zoomScale;
        }

        // --- Mob Sight: render mob dots FIRST so player arrow renders on top ---
        if (!mobsData.isEmpty()) {
            MapDisplaySettings.ensureLoaded();
            boolean showHostile = MapDisplaySettings.isShowHostile();
            boolean showPassiveOther = MapDisplaySettings.isShowPassiveOther();
            java.util.Set<String> disabledMobTypes = MapDisplaySettings.getDisabledMobTypes();

            int clampedSelfX = Math.max(-127, Math.min(127, (int) selfDecX));
            int clampedSelfZ = Math.max(-127, Math.min(127, (int) selfDecY));
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
                    String entityTypeId = parts.length >= 4 ? parts[3] : "";

                    // Category filters
                    if (!showHostile && color == 0xFFFF3333) continue;
                    if (!showPassiveOther && (color == 0xFF33FF33 || color == 0xFFFFAA00)) continue;

                    // Individual mob type filter
                    if (!entityTypeId.isEmpty() && disabledMobTypes.contains(entityTypeId)) continue;

                    if (Math.abs(decX - clampedSelfX) <= 1 && Math.abs(decZ - clampedSelfZ) <= 1) continue;
                    int sx = Math.round(originX + (decX / 2.0f + 64f) * zoomScale);
                    int sy = Math.round(originY + (decZ / 2.0f + 64f) * zoomScale);
                    if (sx < x || sx >= x + width || sy < y || sy >= y + height) continue;
                    // 2×2 dot — small enough not to obscure map detail
                    graphics.fill(sx, sy, sx + 2, sy + 2, color);
                } catch (NumberFormatException ignored) {}
            }
        }

        // Draw player arrow with optional compass tip (on top of mob dots)
        drawArrow(graphics, selfSi, selfSj, facingDirX, facingDirY, MARKER_COLOR);

        // --- Compass destination X marker — stable dec-byte position (no edge drift) ---
        if (hasCompassTarget) {
            float cpx = originX + (compassDecX / 2.0f + 64f) * zoomScale;
            float cpy = originY + (compassDecY / 2.0f + 64f) * zoomScale;
            if (cpx >= x && cpx < x + width && cpy >= y && cpy < y + height) {
                int cpxi = Math.round(cpx), cpyi = Math.round(cpy);
                graphics.fill(cpxi - 2, cpyi - 2, cpxi - 1, cpyi - 1, 0xFF000000);
                graphics.fill(cpxi + 1, cpyi - 2, cpxi + 2, cpyi - 1, 0xFF000000);
                graphics.fill(cpxi - 1, cpyi - 1, cpxi + 1, cpyi + 1, 0xFF000000);
                graphics.fill(cpxi - 2, cpyi + 1, cpxi - 1, cpyi + 2, 0xFF000000);
                graphics.fill(cpxi + 1, cpyi + 1, cpxi + 2, cpyi + 2, 0xFF000000);
                graphics.fill(cpxi - 1, cpyi - 1, cpxi,     cpyi,     0xFFFF6600);
                graphics.fill(cpxi,     cpyi,     cpxi + 1, cpyi + 1, 0xFFFF6600);
                graphics.fill(cpxi,     cpyi - 1, cpxi + 1, cpyi,     0xFFFF6600);
                graphics.fill(cpxi - 1, cpyi,     cpxi,     cpyi + 1, 0xFFFF6600);
            }
        }

        // --- Other decorations (players + landmarks) ---
        // renderOnFrame=true  → landmarks, banners, treasure X — always show at their position
        // renderOnFrame=false → player-type; skip the stale self decoration, show all others
        //                       clamping off-map positions to the minimap edge (smaller dot)
        for (MapRenderState.MapDecorationRenderState dec : renderState.decorations) {
            boolean isPlayerType = !dec.renderOnFrame;

            if (isPlayerType) {
                // Skip ALL player-type decorations for now — we render self via server-sent selfDecX/Y,
                // and other players need proper server-side tracking to avoid stale positions.
                continue;
            }

            float mpx = dec.x / 2.0f;
            float mpy = dec.y / 2.0f;
            float rawSx = originX + (mpx + 64f) * zoomScale;
            float rawSy = originY + (mpy + 64f) * zoomScale;
            boolean onMap = rawSx >= x && rawSx < x + width && rawSy >= y && rawSy < y + height;

            int color = getDecorationColor(mc, dec);

            // Determine dot size from sprite name (encodes vanilla distance category):
            //   player          → on map       → r=1 (3×3 dot)
            //   player_off_map  → near edge    → r=0 with outline only (2×2)
            //   player_off_limits → far / wrong dim → r=0, half-alpha (1×1)
            //   anything else (landmark) → r=1
            int dotRadius = 1; // default: normal
            boolean offLimits = false;
            if (isPlayerType && dec.atlasSprite != null) {
                String spriteName = dec.atlasSprite.contents().name().getPath();
                if (spriteName.contains("player_off_limits")) {
                    dotRadius = 0;
                    offLimits = true;
                } else if (spriteName.contains("player_off_map")) {
                    dotRadius = 0;
                }
                // "player" → dotRadius stays 1
            }

            // Clamp off-map positions to minimap edge (landmarks skip this)
            float displaySx = onMap ? rawSx : Math.max(x + dotRadius + 1, Math.min(x + width - dotRadius - 2, rawSx));
            float displaySy = onMap ? rawSy : Math.max(y + dotRadius + 1, Math.min(y + height - dotRadius - 2, rawSy));
            int si = Math.round(displaySx);
            int sj = Math.round(displaySy);

            if (onMap || isPlayerType) {
                int outlineAlpha = offLimits ? 0x80000000 : 0xFF000000;
                int fillAlpha   = offLimits ? (0x80000000 | (color & 0x00FFFFFF)) : color;
                // Outline: (r+1)px border around the dot
                graphics.fill(si - dotRadius - 1, sj - dotRadius - 1,
                               si + dotRadius + 2, sj + dotRadius + 2, outlineAlpha);
                // Fill: dotRadius px
                if (dotRadius > 0) {
                    graphics.fill(si - dotRadius, sj - dotRadius,
                                   si + dotRadius + 1, sj + dotRadius + 1, fillAlpha);
                }
            }
            // Off-map landmarks are not drawn (would be noise at the edge)
        }

        graphics.disableScissor();

        // --- Facing direction + coordinates below the minimap ---
        if (!MapDisplaySettings.isShowCoords()) return;
        float yaw = mc.player.getYRot();
        // Convert MC yaw (0=south) to degrees-from-north clockwise
        float fromNorth = ((yaw + 180) % 360 + 360) % 360;
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        String facing = dirs[(int)((fromNorth + 22.5f) / 45f) % 8];
        String coords = facing + "  " + mc.player.getBlockX()
            + " / " + mc.player.getBlockY()
            + " / " + mc.player.getBlockZ();
        int textX = x + width / 2 - mc.font.width(coords) / 2;
        int textY = y + height + 4; // 2px border + 2px gap
        graphics.text(mc.font, coords, textX, textY, 0xFFFFFFFF, true);
    }

    /**
     * Draw a clean triangular arrow like the vanilla map player marker.
     * The triangle has a 3-pixel-wide base centered at (cx, cy) and a 1-pixel tip
     * at distance 3 in the direction (dirX, dirY). Black outline, white fill.
     *
     * Rasterisation: for each pixel in a 9×9 bounding box, test which side of the
     * three triangle edges it falls on. If inside all three, fill white; if within
     * 1-pixel of the outline, fill black.
     */
    /**
     * Draw the vanilla map player marker shape: pointed tip facing direction,
     * rectangular body, blunt tail — matching the vanilla player.png sprite.
     *
     * Pixels defined in (perp, fwd) coordinates:
     *   fwd+ = toward tip,  perp+/- = perpendicular sides
     * Converted to screen: screenX = cx + perp*perpX + fwd*dirX
     *                      screenY = cy + perp*perpY + fwd*dirY
     */
    private static void drawArrow(GuiGraphicsExtractor g, int cx, int cy,
                                   float dirX, float dirY, int color,
                                   boolean hasCompassTip, float compassDx, float compassDy) {
        // Draw the vanilla-style arrow body
        drawArrow(g, cx, cy, dirX, dirY, color);

        // Compass tip: a 2-pixel colored spike on the dot pointing toward the compass target
        if (hasCompassTip) {
            float dist = (float) Math.sqrt(compassDx * compassDx + compassDy * compassDy);
            if (dist > 0.01f) {
                float ndx = compassDx / dist, ndy = compassDy / dist;
                // Two pixels along the compass direction from center
                int t1x = Math.round(cx + ndx * 2), t1y = Math.round(cy + ndy * 2);
                int t2x = Math.round(cx + ndx * 4), t2y = Math.round(cy + ndy * 4);
                g.fill(t1x - 1, t1y - 1, t1x + 2, t1y + 2, 0xFF000000); // outline
                g.fill(t1x,     t1y,     t1x + 1, t1y + 1, 0xFFFF6600); // colored tip
                g.fill(t2x,     t2y,     t2x + 1, t2y + 1, 0xFFFF6600); // pointed end
            }
        }
    }

    private static void drawArrow(GuiGraphicsExtractor g, int cx, int cy,
                                   float dirX, float dirY, int color) {
        float perpX = -dirY, perpY = dirX;

        // Outline pixels (black) — (perp, fwd)
        int[][] outline = {
            {0,3},                              // tip
            {-1,2},{1,2},                       // narrow outline sides
            {-2,1},{2,1},{-2,0},{2,0},{-2,-1},{2,-1}, // body sides
            {-1,-2},{0,-2},{1,-2}               // tail (all black)
        };
        // Fill pixels (white) — interior of the shape
        int[][] fill = {
            {0,2},                              // narrow center
            {-1,1},{0,1},{1,1},                 // body rows
            {-1,0},{0,0},{1,0},
            {-1,-1},{0,-1},{1,-1}
        };

        for (int[] o : outline) {
            int sx = Math.round(cx + o[0] * perpX + o[1] * dirX);
            int sy = Math.round(cy + o[0] * perpY + o[1] * dirY);
            g.fill(sx, sy, sx+1, sy+1, 0xFF000000);
        }
        for (int[] f : fill) {
            int sx = Math.round(cx + f[0] * perpX + f[1] * dirX);
            int sy = Math.round(cy + f[0] * perpY + f[1] * dirY);
            g.fill(sx, sy, sx+1, sy+1, color);
        }
    }

    private static int getDecorationColor(Minecraft mc, MapRenderState.MapDecorationRenderState dec) {
        if (dec.name != null && mc.level != null) {
            String playerName = dec.name.getString();
            PlayerTeam team = mc.level.getScoreboard().getPlayersTeam(playerName);
            if (team != null) {
                var teamColor = team.getColor();
                if (teamColor.isPresent()) {
                    return 0xFF000000 | teamColor.get().rgb();
                }
            }
        }
        return MARKER_COLOR;
    }
}
