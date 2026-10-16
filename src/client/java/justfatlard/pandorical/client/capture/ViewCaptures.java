package justfatlard.pandorical.client.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import justfatlard.pandorical.client.mixin.GameRendererAccessor;
import justfatlard.pandorical.client.mixin.SectionOcclusionGraphAccessor;
import justfatlard.pandorical.protocol.CapturePartC2S;
import justfatlard.pandorical.protocol.CaptureViewRequestS2C;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * Takes the pictures a server asks for from a viewpoint. The world is drawn from there between
 * frames, into the main target, the way vanilla draws a panorama: the next frame clears and redraws
 * it before anything reaches the screen, so the player never sees it.
 *
 * <p>The game works out which sections can be seen on a background thread, from wherever the
 * camera last was, so one pass from somewhere else would draw the world as the player can see it.
 * The pass waits for that work done from the viewpoint, and puts the player's back after. Sections
 * the player has never had in view may not be built yet; drawing schedules them, and the pass is
 * tried again each tick until they are, for at most a second.
 */
public final class ViewCaptures {
    private ViewCaptures() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("pandorical");
    public static final float FOV = 70.0F;
    private static final int MOST_TRIES = 20;

    public record View(Vec3 eye, float yRot, float xRot) {}

    private static CaptureViewRequestS2C asked;
    private static int tries;
    private static View drawing;

    /** The view being drawn now, for the camera to take; null outside a pass. */
    public static View drawing() {
        return drawing;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CaptureViewRequestS2C.TYPE, (payload, context) ->
            context.client().execute(() -> ask(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (asked != null) attempt(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> asked = null));
    }

    private static void ask(Minecraft client, CaptureViewRequestS2C payload) {
        if (asked != null) ClientPlayNetworking.send(CapturePartC2S.declined(asked.id()));
        asked = null;
        if (client.level == null) {
            ClientPlayNetworking.send(CapturePartC2S.declined(payload.id()));
            return;
        }
        asked = payload;
        tries = 0;
    }

    private static void attempt(Minecraft client) {
        // A picture of the player's own view reads the main target too
        if (ClientCaptures.busy()) return;
        CaptureViewRequestS2C request = asked;
        if (client.level == null) {
            asked = null;
            ClientPlayNetworking.send(CapturePartC2S.declined(request.id()));
            return;
        }
        View view = new View(new Vec3(request.x(), request.y(), request.z()), request.yRot(), request.xRot());
        boolean built;
        try {
            built = draw(client, view);
        } catch (RuntimeException e) {
            LOGGER.warn("[Pandorical] Could not draw the view the server asked for", e);
            asked = null;
            ClientPlayNetworking.send(CapturePartC2S.declined(request.id()));
            return;
        }
        if (!built && ++tries < MOST_TRIES) return;
        asked = null;
        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), frame -> {
            byte[] png = ClientCaptures.crop(frame, request.side());
            client.execute(() -> ClientCaptures.send(request.id(), png));
        });
    }

    /** Draws the view into the main target; false if some section in it is not built yet. */
    private static boolean draw(Minecraft client, View view) {
        GameRenderer renderer = client.gameRenderer;
        GameRenderState state = renderer.gameRenderState();
        LevelRenderState level = state.levelRenderState;
        SectionOcclusionGraph graph = client.levelRenderer.sectionOcclusionGraph();
        SectionOcclusionGraphAccessor graphs = (SectionOcclusionGraphAccessor) graph;

        settle(graphs);
        Object playersGraph = graphs.pandorical$currentGraph().get();
        CameraType cameraType = client.options.getCameraType();
        boolean hudShown = !client.gui.hud.isHidden();
        if (hudShown) client.gui.hud.toggle();
        // Detached, so the player is drawn if they stand in view, and their hand and overlays are not
        client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        drawing = view;
        try {
            renderer.update(DeltaTracker.ONE);
            renderer.extract(DeltaTracker.ONE, true);
            if (!state.shouldRenderLevel) throw new IllegalStateException("the world is not being drawn");
            graph.update(level.cameraRenderState, state.optionsRenderState.fov, level.chunkLoadingRenderState);
            settle(graphs);
            // Again, now the sections in view are the ones seen from here
            renderer.extract(DeltaTracker.ONE, true);

            RenderTarget target = renderer.mainRenderTarget();
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                target.getColorTexture(), state.guiRenderState.clearColorOverride, target.getDepthTexture(), 0.0);
            ((GameRendererAccessor) renderer).pandorical$globalSettings().update(
                state.windowRenderState.width, state.windowRenderState.height,
                state.optionsRenderState.glintStrength, level.gameTime, level.worldPartialTicks,
                state.optionsRenderState.menuBackgroundBlurriness, level.cameraRenderState.pos,
                state.optionsRenderState.textureFiltering == TextureFilteringMethod.RGSS);
            renderer.renderLevel();
            return built(client);
        } finally {
            drawing = null;
            client.options.setCameraType(cameraType);
            if (hudShown && client.gui.hud.isHidden()) client.gui.hud.toggle();
            // The camera back on the player before the rest of the game reads it
            renderer.update(DeltaTracker.ONE);
            settle(graphs);
            graphs.pandorical$currentGraph().set(playersGraph);
            graph.invalidate();
            graphs.pandorical$needsFrustumUpdate().set(true);
        }
    }

    private static boolean built(Minecraft client) {
        for (SectionRenderDispatcher.RenderSection section : client.levelRenderer.visibleSections()) {
            if (section.getSectionMesh() == CompiledSectionMesh.UNCOMPILED) return false;
        }
        return true;
    }

    /** Waits out a visibility update running in the background, so its answer is not lost or late. */
    private static void settle(SectionOcclusionGraphAccessor graph) {
        Future<?> task = graph.pandorical$fullUpdateTask();
        if (task == null) return;
        try {
            task.get();
        } catch (Exception e) {
            LOGGER.warn("[Pandorical] A section visibility update failed", e);
        }
    }
}
