package justfatlard.pandorical.client.picture;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import justfatlard.pandorical.api.Picture;
import justfatlard.pandorical.protocol.PicturesS2C;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Server pictures, each a texture on a thin panel at its anchor entity. A paint rewrites only its
 * cells; the texture uploads at most once a frame.
 */
public final class ClientPictures {
    private ClientPictures() {}

    private static final float MAX_DISTANCE = 96;

    private static final class Shown {
        final Picture picture;
        final Identifier textureId;
        final DynamicTexture texture;
        final DynamicTexture back;
        final Identifier backId;
        boolean dirty = true;

        Shown(Picture picture) {
            // A fresh id: releasing the replaced picture's texture would close this one.
            int serial = nextSerial++;
            this.picture = picture;
            this.textureId = Identifier.fromNamespaceAndPath("pandorical", "picture/" + serial);
            this.texture = new DynamicTexture(textureId::toString, picture.columns(), picture.rows(), false);
            this.backId = Identifier.fromNamespaceAndPath("pandorical", "picture/" + serial + "_back");
            this.back = new DynamicTexture(backId::toString, 1, 1, false);
            this.back.getPixels().setPixel(0, 0, picture.backColor());
            this.back.upload();
            for (int i = 0; i < picture.cells().length; i++) draw(i);
            Minecraft mc = Minecraft.getInstance();
            mc.getTextureManager().register(textureId, texture);
            mc.getTextureManager().register(backId, back);
        }

        void draw(int index) {
            int cell = picture.cells()[index] & 0xFF;
            int color = cell < picture.palette().length ? picture.palette()[cell] : 0xFFFF00FF;
            texture.getPixels().setPixel(index % picture.columns(), index / picture.columns(), color);
            dirty = true;
        }

        void release() {
            Minecraft mc = Minecraft.getInstance();
            mc.getTextureManager().release(textureId);
            mc.getTextureManager().release(backId);
        }
    }

    private static final Map<Integer, Shown> SHOWN = new HashMap<>();
    private static int nextSerial;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PicturesS2C.TYPE,
            (payload, context) -> context.client().execute(() -> apply(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientPictures::clearAll));
        // The textures go with the anchor, whatever the server says.
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            Shown shown = SHOWN.remove(entity.getId());
            if (shown != null) shown.release();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(ClientPictures::onCollectSubmits);
    }

    private static void apply(PicturesS2C payload) {
        if (payload.isShow()) {
            Shown old = SHOWN.put(payload.entityId(), new Shown(payload.picture()));
            if (old != null) old.release();
        } else if (payload.isPaint()) {
            Shown shown = SHOWN.get(payload.entityId());
            if (shown == null) return;
            byte[] cells = shown.picture.cells();
            int n = Math.min(payload.indices().length, payload.values().length);
            for (int i = 0; i < n; i++) {
                int index = payload.indices()[i];
                if (index < 0 || index >= cells.length) continue;
                cells[index] = payload.values()[i];
                shown.draw(index);
            }
        } else {
            Shown shown = SHOWN.remove(payload.entityId());
            if (shown != null) shown.release();
        }
    }

    private static void clearAll() {
        SHOWN.values().forEach(Shown::release);
        SHOWN.clear();
    }

    private static void onCollectSubmits(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || SHOWN.isEmpty()) return;
        Vec3 cam = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();

        for (Map.Entry<Integer, Shown> entry : SHOWN.entrySet()) {
            Entity anchor = level.getEntity(entry.getKey());
            if (anchor == null) continue;
            Shown shown = entry.getValue();
            Picture.Pose pose = shown.picture.pose();
            Vec3 at = anchor.position().add(pose.x(), pose.y(), pose.z());
            if (at.distanceToSqr(cam) > MAX_DISTANCE * MAX_DISTANCE) continue;
            if (shown.dirty) {
                shown.texture.upload();
                shown.dirty = false;
            }

            int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(at.add(0, pose.height() / 2, 0)));
            poseStack.pushPose();
            poseStack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            // Local +z is the way the front faces, local +y up the picture.
            poseStack.rotateDegrees(Axis.YP, -pose.yaw());
            poseStack.rotateDegrees(Axis.XP, -pose.tilt());

            float w = pose.width() / 2;
            float h = pose.height();
            float d = shown.picture.thickness();
            // Unshaded, like a map in a frame.
            context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.text(shown.textureId), (p, v) -> {
                flat(p, v, light, -w, 0, 0, 1);
                flat(p, v, light, w, 0, 1, 1);
                flat(p, v, light, w, h, 1, 0);
                flat(p, v, light, -w, h, 0, 0);
            });
            if ((shown.picture.backColor() >>> 24) != 0) {
                context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(shown.backId),
                    (p, v) -> panel(p, v, light, w, h, d));
            }
            poseStack.popPose();
        }
    }

    private static void panel(PoseStack.Pose p, VertexConsumer v, int light, float w, float h, float d) {
        face(p, v, light, 0, 0, -1, w, 0, -d, 0, 0, -w, 0, -d, 0, 0, -w, h, -d, 0, 0, w, h, -d, 0, 0);
        face(p, v, light, 0, 1, 0, -w, h, 0, 0, 0, w, h, 0, 0, 0, w, h, -d, 0, 0, -w, h, -d, 0, 0);
        face(p, v, light, 0, -1, 0, -w, 0, -d, 0, 0, w, 0, -d, 0, 0, w, 0, 0, 0, 0, -w, 0, 0, 0, 0);
        face(p, v, light, 1, 0, 0, w, 0, 0, 0, 0, w, 0, -d, 0, 0, w, h, -d, 0, 0, w, h, 0, 0, 0);
        face(p, v, light, -1, 0, 0, -w, 0, -d, 0, 0, -w, 0, 0, 0, 0, -w, h, 0, 0, 0, -w, h, -d, 0, 0);
    }

    /** Corners counter-clockwise seen from the side it faces, each x, y, z, u, v. */
    private static void face(PoseStack.Pose p, VertexConsumer v, int light, float nx, float ny, float nz,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3) {
        vertex(p, v, light, nx, ny, nz, x0, y0, z0, u0, v0);
        vertex(p, v, light, nx, ny, nz, x1, y1, z1, u1, v1);
        vertex(p, v, light, nx, ny, nz, x2, y2, z2, u2, v2);
        vertex(p, v, light, nx, ny, nz, x3, y3, z3, u3, v3);
    }

    private static void flat(PoseStack.Pose p, VertexConsumer v, int light, float x, float y, float u, float uv) {
        v.addVertex(p, x, y, 0).setColor(0xFFFFFFFF).setUv(u, uv).setLight(light);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer v, int light, float nx, float ny, float nz,
            float x, float y, float z, float u, float uv) {
        v.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, uv).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(p, nx, ny, nz);
    }
}
