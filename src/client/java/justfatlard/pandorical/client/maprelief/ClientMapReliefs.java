package justfatlard.pandorical.client.maprelief;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.MapTerrain;
import justfatlard.pandorical.protocol.MapReliefS2C;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Framed maps the server has asked to be drawn as their ground. Each terrain is coloured on
 * arrival, block by block from the textures in use, and meshed off the render thread; until the
 * mesh is ready the frame keeps whatever it showed before. A relief asked to rise grows out of
 * the frame from nothing, and one asked to sink goes back into it before the map lies flat.
 *
 * <p>Drawn in the space {@code MapRenderer} draws a map in: a pixel to a unit, x along the map,
 * y down it, and the frame's front towards negative z.
 */
public final class ClientMapReliefs {
    private ClientMapReliefs() {}

    /** Carries a frame's relief from extraction, where the entity is in hand, to submit, where only its state is. */
    public interface Holder {
        Drawn pandorical$relief();

        void pandorical$setRelief(Drawn relief);
    }

    /** A relief as it is to be drawn this frame: how far it has risen, 1 for standing. */
    public record Drawn(Relief relief, float risen) {}

    /** Rising from {@code from} towards {@code to}, since {@code start} in milliseconds. */
    private record Motion(long start, float from, float to) {
        float at(long now) {
            float t = Math.min(1, (now - start) / (float) (to > from ? RISE_MILLIS : SINK_MILLIS));
            return from + (to - from) * (to > from ? overshoot(t) : t * t * t);
        }

        boolean over(long now) {
            return now - start >= (to > from ? RISE_MILLIS : SINK_MILLIS);
        }

        /** Past the end and back, so the ground lands rather than stops. */
        private static float overshoot(float t) {
            float c = 1.2F, u = t - 1;
            return 1 + (c + 1) * u * u * u + c * u * u;
        }
    }

    private static final long RISE_MILLIS = 800;
    private static final long SINK_MILLIS = 600;

    private static final int SIDE = MapTerrain.SIDE;
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("pandorical", "map_relief/white");

    /**
     * Coarser meshes for further off, each voxel twice the last across. A voxel is a 128th of a
     * block, smaller than a pixel from a few blocks away, so drawing every one from across a room
     * is work nobody sees; each step coarser is about a quarter of the quads.
     */
    private static final int LEVELS = 4;
    /** Blocks from the camera within which the finest mesh is drawn; each coarser one reaches twice as far. */
    private static final float FINEST_WITHIN = 6;

    public static final class Relief {
        /** What it was meshed from, so the same ground sent again is drawn without meshing it again. */
        private final MapTerrain source;
        private final float[][] levels;
        private final short[] tops;
        /** How far the tallest column stands out of the frame, in blocks. */
        public final float rise;

        private Relief(MapTerrain source, float[][] levels, short[] tops) {
            this.source = source;
            this.levels = levels;
            this.tops = tops;
            int tallest = 0;
            for (short top : tops) tallest = Math.max(tallest, top);
            this.rise = tallest / (float) SIDE;
        }

        /** The tallest column within {@code reach} pixels of one: what a decoration there has to clear. */
        private int peakNear(int x, int y, int reach) {
            int peak = 0;
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    int px = x + dx, py = y + dy;
                    if (px >= 0 && py >= 0 && px < SIDE && py < SIDE) peak = Math.max(peak, tops[px + py * SIDE]);
                }
            }
            return peak;
        }
    }

    private static final Map<Integer, Relief> SHOWN = new HashMap<>();
    private static final Map<Integer, Motion> MOVING = new HashMap<>();
    /**
     * Reliefs that have sunk back into their frames, kept while the frame is about: a table
     * switched on again sends the same ground, and it rises at once rather than a mesh later.
     */
    private static final Map<Integer, Relief> SUNK = new HashMap<>();
    /** The latest terrain sent for each frame, so a mesh finished after a newer one arrived is dropped. */
    private static final Map<Integer, MapTerrain> LATEST = new HashMap<>();
    private static boolean whiteMade;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MapReliefS2C.TYPE,
            (payload, context) -> context.client().execute(() -> apply(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            SHOWN.clear();
            MOVING.clear();
            SUNK.clear();
            LATEST.clear();
            ReliefColours.forget();
        }));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            SHOWN.remove(entity.getId());
            MOVING.remove(entity.getId());
            SUNK.remove(entity.getId());
            LATEST.remove(entity.getId());
        });
    }

    private static void apply(MapReliefS2C payload) {
        int frame = payload.entityId();
        MapTerrain terrain = payload.terrain();
        if (terrain == null) {
            LATEST.remove(frame);
            if (payload.motion() == MapReliefS2C.Motion.SINK && SHOWN.containsKey(frame)) {
                long now = Util.getMillis();
                MOVING.put(frame, new Motion(now, risen(frame, now), 0));
            } else {
                SHOWN.remove(frame);
                MOVING.remove(frame);
            }
            return;
        }
        LATEST.put(frame, terrain);
        // Timed from arrival, not from the mesh being ready: maps rising one after another keep
        // the order they were sent in, whichever took longer to mesh
        long arrived = Util.getMillis();
        float from = SHOWN.containsKey(frame) ? risen(frame, arrived) : 0;
        Relief ready = SUNK.remove(frame);
        if (ready == null) ready = SHOWN.get(frame);
        if (ready != null && ready.source.equals(terrain)) {
            install(frame, ready, payload.motion(), arrived, from);
            return;
        }
        int[] colours = colour(terrain);
        CompletableFuture.supplyAsync(() -> build(terrain, colours), Util.backgroundExecutor())
            .whenCompleteAsync((relief, failure) -> {
                if (failure != null) Pandorical.LOGGER.warn("[pandorical] could not mesh a map relief", failure);
                else if (LATEST.get(frame) == terrain) install(frame, relief, payload.motion(), arrived, from);
            }, Minecraft.getInstance());
    }

    /**
     * A rise runs from wherever the ground was when it was asked for, flat or partway down;
     * anything else leaves a rise under way to finish, and stands up ground that had sunk.
     */
    private static void install(int frame, Relief relief, MapReliefS2C.Motion motion, long arrived, float from) {
        SHOWN.put(frame, relief);
        Motion moving = MOVING.get(frame);
        if (motion == MapReliefS2C.Motion.RISE) MOVING.put(frame, new Motion(arrived, from, 1));
        else if (moving != null && moving.to() == 0) MOVING.remove(frame);
    }

    private static float risen(int frame, long now) {
        Motion moving = MOVING.get(frame);
        return moving == null ? 1 : moving.at(now);
    }

    /** Top, side and underside colours for every block the terrain uses, in every biome it uses. */
    private static int[] colour(MapTerrain terrain) {
        int biomes = Math.max(1, terrain.biomes().size());
        Biome[] biome = new Biome[biomes];
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
            for (int i = 0; i < terrain.biomes().size(); i++) biome[i] = registry.getValue(terrain.biomes().get(i));
        }
        int[] colours = new int[terrain.blocks().size() * biomes * 3];
        for (int block = 1; block < terrain.blocks().size(); block++) {
            for (int b = 0; b < biomes; b++) {
                System.arraycopy(ReliefColours.of(terrain.blocks().get(block), biome[b]), 0, colours, (block * biomes + b) * 3, 3);
            }
        }
        return colours;
    }

    /** Each voxel keyed by its block and its column's biome together, which is what its colour depends on. */
    private static Relief build(MapTerrain terrain, int[] colours) {
        int height = Math.max(1, terrain.height());
        int biomes = Math.max(1, terrain.biomes().size());
        int[] voxels = new int[SIDE * SIDE * height];
        short[] tops = new short[SIDE * SIDE];
        for (int column = 0; column < SIDE * SIDE; column++) {
            int y = 0;
            for (int run = terrain.firstRun(column); run < terrain.firstRun(column + 1); run++) {
                int block = terrain.runBlock(run);
                int key = block == MapTerrain.EMPTY ? 0 : block * biomes + terrain.biomeOf(column);
                for (int n = terrain.runLength(run); n > 0; n--, y++) voxels[column + y * SIDE * SIDE] = key;
            }
            tops[column] = (short) y;
        }
        ReliefMesher.FaceColour colour = (key, face) -> colours[key * 3 + face];
        float[][] levels = new float[LEVELS][];
        levels[0] = ReliefMesher.mesh(SIDE, height, 1, voxels, colour);
        for (int level = 1; level < LEVELS; level++) {
            int factor = 1 << level;
            int[] coarse = ReliefMesher.coarsen(SIDE, height, voxels, factor);
            levels[level] = ReliefMesher.mesh((SIDE + factor - 1) / factor, (height + factor - 1) / factor, factor, coarse, colour);
        }
        return new Relief(terrain, levels, tops);
    }

    public static Relief of(Entity frame) {
        return SHOWN.get(frame.getId());
    }

    /** The relief to draw a framed map with and how far it has risen, or null to draw it flat. */
    public static Drawn forFrame(ItemFrame frame, MapId mapId) {
        if (mapId == null) return null;
        int id = frame.getId();
        Relief relief = SHOWN.get(id);
        if (relief == null) return null;
        long now = Util.getMillis();
        Motion moving = MOVING.get(id);
        if (moving != null && moving.over(now)) {
            MOVING.remove(id);
            if (moving.to() == 0) {
                SUNK.put(id, SHOWN.remove(id));
                return null;
            }
        }
        return new Drawn(relief, risen(id, now));
    }

    /**
     * Draw a relief where {@code MapRenderer} would have drawn the flat map, decorations
     * included, each lifted clear of the ground beneath it.
     */
    public static void draw(Drawn drawn, MapRenderState map, PoseStack poseStack, SubmitNodeCollector collector,
            boolean showOnlyFrame, int light, double distanceSq) {
        Relief relief = drawn.relief();
        // Flattened towards the frame rather than to it, so a normal still has a way to face
        float risen = Math.max(0.01F, drawn.risen());
        int level = 0;
        while (level < LEVELS - 1 && distanceSq > sq(FINEST_WITHIN * (1 << level))) level++;
        float[] q = relief.levels[level];
        poseStack.pushPose();
        poseStack.scale(1, 1, risen);
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(white()), (pose, buffer) -> {
            // Six ways a face can point, so six normals to turn with the frame, not four per quad
            Vector3f[] normals = new Vector3f[6];
            for (int i = 0; i < q.length; i += ReliefMesher.QUAD) {
                int way = q[i] != 0 ? (q[i] > 0 ? 0 : 1) : q[i + 1] != 0 ? (q[i + 1] > 0 ? 2 : 3) : (q[i + 2] > 0 ? 4 : 5);
                Vector3f normal = normals[way];
                if (normal == null) normal = normals[way] = pose.transformNormal(q[i], q[i + 1], q[i + 2], new Vector3f());
                int colour = Float.floatToRawIntBits(q[i + 3]);
                for (int c = i + 4; c < i + ReliefMesher.QUAD; c += 3) {
                    buffer.addVertex(pose, q[c], q[c + 1], q[c + 2]).setColor(colour).setUv(0.5F, 0.5F)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normal.x(), normal.y(), normal.z());
                }
            }
        });
        poseStack.popPose();

        int count = 0;
        for (MapRenderState.MapDecorationRenderState decoration : map.decorations) {
            if (showOnlyFrame && !decoration.renderOnFrame) continue;
            float x = decoration.x / 2.0F + 64.0F;
            float y = decoration.y / 2.0F + 64.0F;
            // A decoration is eight pixels across, so it has to clear everything within four
            float ground = relief.peakNear(Mth.floor(x), Mth.floor(y), 4) * risen;

            TextureAtlasSprite sprite = decoration.atlasSprite;
            if (sprite != null) {
                poseStack.pushPose();
                poseStack.translate(x, y, -ground - 0.02F);
                poseStack.rotateDegrees(Axis.ZP, decoration.rot * 360 / 16.0F);
                poseStack.scale(4.0F, 4.0F, 3.0F);
                poseStack.translate(-0.125F, 0.125F, 0.0F);
                float z = count * -0.001F;
                collector.submitCustomGeometry(poseStack, RenderTypes.text(sprite.atlasLocation()), (pose, buffer) -> {
                    buffer.addVertex(pose, -1.0F, 1.0F, z).setColor(-1).setUv(sprite.getU0(), sprite.getV0()).setLight(light);
                    buffer.addVertex(pose, 1.0F, 1.0F, z).setColor(-1).setUv(sprite.getU1(), sprite.getV0()).setLight(light);
                    buffer.addVertex(pose, 1.0F, -1.0F, z).setColor(-1).setUv(sprite.getU1(), sprite.getV1()).setLight(light);
                    buffer.addVertex(pose, -1.0F, -1.0F, z).setColor(-1).setUv(sprite.getU0(), sprite.getV1()).setLight(light);
                });
                poseStack.popPose();
            }

            if (decoration.name != null) {
                Font font = Minecraft.getInstance().font;
                float width = font.width(decoration.name);
                float scale = Mth.clamp(25.0F / width, 0.0F, 6.0F / 9.0F);
                poseStack.pushPose();
                poseStack.translate(x - width * scale / 2.0F, y + 4.0F, -ground - 0.025F);
                poseStack.scale(scale, scale, -1.0F);
                poseStack.translate(0.0F, 0.0F, 0.1F);
                collector.order(1).submitText(poseStack, 0.0F, 0.0F, decoration.name.getVisualOrderText(), false,
                    Font.DisplayMode.NORMAL, light, -1, Integer.MIN_VALUE, 0);
                poseStack.popPose();
            }
            count++;
        }
    }

    private static float sq(float x) {
        return x * x;
    }

    /** Colour comes from the vertices, so the texture is one white pixel. */
    private static Identifier white() {
        if (!whiteMade) {
            DynamicTexture texture = new DynamicTexture(WHITE::toString, 1, 1, false);
            texture.getPixels().setPixel(0, 0, -1);
            texture.upload();
            Minecraft.getInstance().getTextureManager().register(WHITE, texture);
            whiteMade = true;
        }
        return WHITE;
    }
}
