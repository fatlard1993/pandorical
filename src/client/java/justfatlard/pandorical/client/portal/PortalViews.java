package justfatlard.pandorical.client.portal;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import justfatlard.pandorical.protocol.PortalViewS2C;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * What is past the portal nearest the player, drawn behind its sheet with vanilla's swirl over it.
 * The server sends the far side (see FarSides); this draws it.
 *
 * <p>The far side is drawn first each frame, from the player's own eye, into a target the size of
 * the screen. The sheet is then drawn as an opaque quad that shows whatever that target holds at
 * the same pixel, the way the end portal shows its sky, so it lands only where the sheet is and
 * hides the wall behind it. Vanilla's translucent swirl draws after, over the top.
 *
 * <p>The far side is set behind the near sheet the way a trip through it would carry you: sheet
 * middle to sheet middle, turned a quarter when the two sheets face different ways, as vanilla
 * turns a traveller. Only the blocks beyond the sheet from the eye are drawn, so nothing of the
 * far side stands in front of the portal and no clip plane is needed.
 */
public final class PortalViews {
    private PortalViews() {}

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("pandorical", "portal_view");

    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("pandorical", "pipeline/portal_view"))
        .withVertexShader(Identifier.fromNamespaceAndPath("pandorical", "core/portal_view"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("pandorical", "core/portal_view"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .withCull(false)
        .build());

    private static final RenderType SHEET = RenderType.create("pandorical_portal_view",
        RenderSetup.builder(PIPELINE)
            .withTexture("Sampler0", TEXTURE, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            .createRenderSetup());

    /** A far block, and where its middle stands behind the near sheet. */
    private record Shown(BlockPos far, BlockState state, double x, double y, double z) {}

    /** One portal's far side, laid out behind its near sheet. */
    private static final class View {
        final ClientLevel level;
        final PortalViewS2C.Sheet near;
        final boolean turned;
        final Vector4f sky;
        final Holder<Biome> biome;
        /** Split by which side of the near sheet they stand on: the side away from the eye is drawn. */
        final List<Shown> lowSide = new ArrayList<>();
        final List<Shown> highSide = new ArrayList<>();
        final Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        /** Light on each far block's open faces, and on the air beside it: what its faces are lit by. */
        final Long2IntOpenHashMap light = new Long2IntOpenHashMap();

        View(ClientLevel level, PortalViewS2C view) {
            this.level = level;
            this.near = view.near();
            this.turned = view.near().alongX() != view.far().alongX();
            this.sky = new Vector4f(((view.sky() >> 16) & 0xFF) / 255.0F, ((view.sky() >> 8) & 0xFF) / 255.0F, (view.sky() & 0xFF) / 255.0F, 1.0F);
            this.biome = biome(level, view.biome(), view.near().corner());
            light.defaultReturnValue(0);

            Vec3 farMiddle = middle(view.far());
            Vec3 nearMiddle = middle(view.near());
            double plane = thin(nearMiddle);
            BlockPos corner = view.far().corner();
            for (PortalViewS2C.Block block : view.blocks()) {
                BlockPos far = corner.offset(block.dx(), block.dy(), block.dz());
                BlockState state = Block.stateById(block.state());
                states.put(far.asLong(), state);
                light.put(far.asLong(), block.light());

                double cx = far.getX() + 0.5 - farMiddle.x, cy = far.getY() + 0.5 - farMiddle.y, cz = far.getZ() + 0.5 - farMiddle.z;
                // Vanilla turns a traveller between sheets facing different ways by a quarter: this is that turn, undone
                Vec3 at = turned ? nearMiddle.add(cz, cy, -cx) : nearMiddle.add(cx, cy, cz);
                double side = thin(at) - plane;
                // The sheet's own row is half on each side of it, and would stand in front of it
                if (Math.abs(side) < 0.5) continue;
                (side < 0 ? lowSide : highSide).add(new Shown(far, state, at.x, at.y, at.z));
            }
            for (PortalViewS2C.Block block : view.blocks()) {
                BlockPos far = corner.offset(block.dx(), block.dy(), block.dz());
                for (Direction side : Direction.values()) {
                    long beside = far.relative(side).asLong();
                    if (!states.containsKey(beside)) light.put(beside, Math.max(light.get(beside), block.light()));
                }
            }
        }

        /** The middle of a sheet's bottom edge, in the plane of the sheet. */
        private static Vec3 middle(PortalViewS2C.Sheet sheet) {
            BlockPos corner = sheet.corner();
            return sheet.alongX()
                ? new Vec3(corner.getX() + sheet.width() / 2.0, corner.getY(), corner.getZ() + 0.5)
                : new Vec3(corner.getX() + 0.5, corner.getY(), corner.getZ() + sheet.width() / 2.0);
        }

        /** Where a point sits along the axis the near sheet is thin in. */
        double thin(Vec3 at) {
            return near.alongX() ? at.z : at.x;
        }

        /** The far side's own biome; the near sheet's when this client does not know it. */
        private static Holder<Biome> biome(ClientLevel level, String id, BlockPos near) {
            Identifier parsed = id.isEmpty() ? null : Identifier.tryParse(id);
            if (parsed != null) {
                Optional<? extends Holder<Biome>> found = level.registryAccess().lookupOrThrow(Registries.BIOME)
                    .get(ResourceKey.create(Registries.BIOME, parsed));
                if (found.isPresent()) return found.get();
            }
            return level.getBiome(near);
        }
    }

    /** A far block that answers for its neighbours and its light from the far side, not from this world. */
    private static final class FarBlock extends MovingBlockRenderState {
        private final View view;

        FarBlock(View view) {
            this.view = view;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return view.states.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            int packed = view.light.get(pos.asLong());
            return layer == LightLayer.BLOCK ? packed >> 4 : packed & 15;
        }
    }

    private static View view;
    private static boolean drawnThisFrame;

    private static ViewTexture texture;
    private static final SubmitNodeStorage submits = new SubmitNodeStorage();

    public static void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            texture = new ViewTexture();
            client.getTextureManager().register(TEXTURE, texture);
        });
        ClientPlayNetworking.registerGlobalReceiver(PortalViewS2C.TYPE, (payload, context) -> context.client().execute(() -> {
            ClientLevel level = context.client().level;
            view = payload.shown() && level != null ? new View(level, payload) : null;
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> view = null));
        LevelRenderEvents.COLLECT_SUBMITS.register(PortalViews::submitSheet);
    }

    /** How many far blocks are being drawn, for tests; zero with no view. */
    public static int showing() {
        View now = current();
        return now == null ? 0 : now.lowSide.size() + now.highSide.size();
    }

    private static View current() {
        View now = view;
        Minecraft client = Minecraft.getInstance();
        // A view is of the world it was sent in: after a trip it stands until the server sends the next
        return now != null && now.level == client.level && Crossing.active() ? now : null;
    }

    /** Draws the far side into the view texture. Runs as the level starts drawing, before anything is submitted. */
    public static void drawFarSide(CameraRenderState camera, GpuBufferSlice fog) {
        drawnThisFrame = false;
        View now = current();
        if (now == null || texture == null) return;
        Minecraft client = Minecraft.getInstance();

        RenderTarget main = client.gameRenderer.mainRenderTarget();
        texture.fit(main.width, main.height);

        Vec3 eye = camera.pos;
        double plane = now.thin(new Vec3(now.near.corner().getX() + 0.5, 0, now.near.corner().getZ() + 0.5));
        List<Shown> beyond = now.thin(eye) > plane ? now.lowSide : now.highSide;

        PoseStack pose = new PoseStack();
        for (Shown shown : beyond) {
            pose.pushPose();
            pose.translate(shown.x() - eye.x, shown.y() - eye.y, shown.z() - eye.z);
            if (now.turned) pose.rotateDegrees(Axis.YP, 90.0F);
            pose.translate(-0.5F, -0.5F, -0.5F);
            FarBlock block = new FarBlock(now);
            block.blockState = shown.state();
            block.randomSeedPos = shown.far();
            block.blockPos = shown.far();
            block.biome = now.biome;
            block.cardinalLighting = now.level.cardinalLighting();
            submits.submitMovingBlock(pose, block, EntityRenderState.NO_OUTLINE);
            pose.popPose();
        }

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(camera.viewRotationMatrix);
        RenderSystem.setShaderFog(fog);
        client.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
        FeatureRenderDispatcher dispatcher = client.gameRenderer.featureRenderDispatcher();
        try (
            FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(submits);
            RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Pandorical portal view", texture.colorView(), Optional.of(now.sky), texture.depthView(), OptionalDouble.of(0.0))
        ) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        } finally {
            modelView.popMatrix();
        }
        drawnThisFrame = true;
    }

    private static void submitSheet(LevelRenderContext context) {
        View now = current();
        if (!drawnThisFrame || now == null) return;
        Vec3 eye = context.levelState().cameraRenderState.pos;
        PortalViewS2C.Sheet sheet = now.near;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), SHEET, (pose, buffer) -> {
            for (int across = 0; across < sheet.width(); across++) {
                for (int up = 0; up < sheet.height(); up++) {
                    BlockPos pos = sheet.alongX() ? sheet.corner().offset(across, up, 0) : sheet.corner().offset(0, up, across);
                    quad(pose, buffer, pos, sheet.alongX(), eye);
                }
            }
        });
    }

    /** The middle of a sheet block's thickness, behind both faces vanilla draws the swirl on. */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, BlockPos pos, boolean alongX, Vec3 eye) {
        float x = (float) (pos.getX() - eye.x), y = (float) (pos.getY() - eye.y), z = (float) (pos.getZ() - eye.z);
        if (alongX) {
            buffer.addVertex(pose, x, y, z + 0.5F);
            buffer.addVertex(pose, x + 1, y, z + 0.5F);
            buffer.addVertex(pose, x + 1, y + 1, z + 0.5F);
            buffer.addVertex(pose, x, y + 1, z + 0.5F);
        } else {
            buffer.addVertex(pose, x + 0.5F, y, z);
            buffer.addVertex(pose, x + 0.5F, y, z + 1);
            buffer.addVertex(pose, x + 0.5F, y + 1, z + 1);
            buffer.addVertex(pose, x + 0.5F, y + 1, z);
        }
    }

    /** A colour and depth target the shader samples by name, resized to the window as it changes. */
    private static final class ViewTexture extends AbstractTexture {
        private GpuTexture depth;
        private GpuTextureView depthView;

        void fit(int width, int height) {
            if (this.texture != null && this.texture.getWidth(0) == width && this.texture.getHeight(0) == height) return;
            close();
            GpuDevice device = RenderSystem.getDevice();
            this.texture = device.createTexture(() -> "Pandorical portal view", 13, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            this.textureView = device.createTextureView(this.texture);
            this.depth = device.createTexture(() -> "Pandorical portal view depth", 9, GpuFormat.D32_FLOAT, width, height, 1, 1);
            this.depthView = device.createTextureView(this.depth);
        }

        GpuTextureView colorView() {
            return this.textureView;
        }

        GpuTextureView depthView() {
            return this.depthView;
        }

        @Override
        public void close() {
            super.close();
            if (this.depthView != null) this.depthView.close();
            if (this.depth != null) this.depth.close();
            this.depthView = null;
            this.depth = null;
        }
    }
}
