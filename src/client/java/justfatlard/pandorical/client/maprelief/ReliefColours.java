package justfatlard.pandorical.client.maprelief;

import com.mojang.blaze3d.platform.NativeImage;
import justfatlard.pandorical.Pandorical;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What colour a block is, seen from above, from the side and from below: the average of the
 * textures its model shows that way, tinted as the world would tint it in a given biome. A
 * voxel is far too small for a texture to read, so its average is what the eye gets anyway.
 */
final class ReliefColours {
    private ReliefColours() {}

    /** Average colour of each sprite's opaque pixels, and how much of the sprite is opaque. */
    private record Average(int rgb, float cover) {}

    private static final Average NOTHING = new Average(0, 0);
    private static final Map<Identifier, Average> SPRITES = new HashMap<>();
    private static final Direction[] LOOKING = {Direction.UP, Direction.NORTH, Direction.DOWN};
    /** For blocks that show nothing any of the ways we look, a colour that says so without clashing. */
    private static final int UNKNOWN = 0xFF7F7F7F;

    /** Sprites are read afresh each session, so a resource pack changed mid-session shows from the next one. */
    static void forget() {
        SPRITES.clear();
    }

    /** ARGB for the top, side and underside of a block in a biome; never 0. */
    static int[] of(BlockState block, Biome biome) {
        int[] faces = new int[3];
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(block);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        for (int face = 0; face < 3; face++) {
            List<BakedQuad> quads = new ArrayList<>();
            for (BlockStateModelPart part : parts) quads.addAll(part.getQuads(LOOKING[face]));
            // Plants and the like show no face to any side; their crossed planes are all there is
            if (quads.isEmpty()) for (BlockStateModelPart part : parts) quads.addAll(part.getQuads(null));
            faces[face] = quads.isEmpty() ? fallback(block, model, biome) : blend(block, quads, biome);
        }
        return faces;
    }

    private static int blend(BlockState block, List<BakedQuad> quads, Biome biome) {
        float r = 0, g = 0, b = 0, weight = 0;
        for (BakedQuad quad : quads) {
            Average average = average(quad.materialInfo().sprite());
            if (average.cover() == 0) continue;
            int rgb = quad.materialInfo().isTinted() ? ARGB.multiply(average.rgb(), tint(block, quad.materialInfo().tintIndex(), biome)) : average.rgb();
            r += ARGB.red(rgb) * average.cover();
            g += ARGB.green(rgb) * average.cover();
            b += ARGB.blue(rgb) * average.cover();
            weight += average.cover();
        }
        if (weight == 0) return UNKNOWN;
        return ARGB.color(255, Math.round(r / weight), Math.round(g / weight), Math.round(b / weight));
    }

    /** Water, lava and anything else drawn by something other than its model: its particle, tinted if it takes a tint. */
    private static int fallback(BlockState block, BlockStateModel model, Biome biome) {
        Average average = average(model.particleMaterial().sprite());
        if (average.cover() == 0) return UNKNOWN;
        int tint = tint(block, 0, biome);
        FluidState fluid = block.getFluidState();
        if (tint == -1 && fluid.is(FluidTags.WATER) && biome != null) tint = biome.getWaterColor();
        return ARGB.opaque(ARGB.multiply(average.rgb(), tint));
    }

    /** The block's tint in the biome, or white when it takes none. */
    private static int tint(BlockState block, int index, Biome biome) {
        BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(block, index);
        if (source == null) return -1;
        try {
            return biome == null ? source.color(block) : source.colorInWorld(block, new InBiome(biome), BlockPos.ZERO);
        } catch (RuntimeException e) {
            return source.color(block);
        }
    }

    private static Average average(TextureAtlasSprite sprite) {
        return SPRITES.computeIfAbsent(sprite.contents().name(), ReliefColours::read);
    }

    private static Average read(Identifier sprite) {
        Identifier file = sprite.withPath(path -> "textures/" + path + ".png");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return NOTHING;
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            // An animated texture is its frames stacked; the first is enough
            int size = Math.min(image.getWidth(), image.getHeight());
            long r = 0, g = 0, b = 0, opaque = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int pixel = image.getPixel(x, y);
                    if (ARGB.alpha(pixel) < 128) continue;
                    r += ARGB.red(pixel);
                    g += ARGB.green(pixel);
                    b += ARGB.blue(pixel);
                    opaque++;
                }
            }
            if (opaque == 0) return NOTHING;
            return new Average(ARGB.color(255, (int) (r / opaque), (int) (g / opaque), (int) (b / opaque)), opaque / (float) (size * size));
        } catch (IOException e) {
            Pandorical.LOGGER.warn("[pandorical] map relief could not read {}", file, e);
            return NOTHING;
        }
    }

    /** Somewhere in a biome with nothing around it: all a tint asks of the world. */
    private record InBiome(Biome biome) implements BlockAndTintGetter {
        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return resolver.getColor(biome, pos.getX(), pos.getZ());
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return CardinalLighting.DEFAULT;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return null;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    }
}
