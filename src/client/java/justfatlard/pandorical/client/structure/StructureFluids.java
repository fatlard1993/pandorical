package justfatlard.pandorical.client.structure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import justfatlard.pandorical.client.structure.StructureManager.ClientStructure;
import justfatlard.pandorical.client.structure.StructureManager.RelPosKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.Map;
import java.util.function.Function;

/**
 * The water in a structure's waterlogged blocks.
 *
 * <p>A structure's blocks go through the moving-block renderer, a piston's, which draws a block's
 * model and nothing of its fluid: in the world that comes from the chunk mesh, which a structure
 * is not part of. So a waterlogged slab sailed off as a dry one. This draws the fluid as a still
 * source would stand in the block, a box with its faces shared with more of the same left out.
 */
public final class StructureFluids {
	private StructureFluids() {}

	/** A still source's surface, with nothing of its kind above it. */
	private static final float SOURCE_HEIGHT = 8.0f / 9.0f;

	/** The pose stack is the structure's; {@code worldPos} places a block for light and biome. */
	static void submit(ClientStructure structure, ClientLevel level, PoseStack poseStack, SubmitNodeCollector collector,
			Function<RelPosKey, BlockPos> worldPos) {
		Map<RelPosKey, BlockState> blocks = structure.blocks;
		if (blocks.values().stream().allMatch(state -> state.getFluidState().isEmpty())) return;

		collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) -> {
			for (Map.Entry<RelPosKey, BlockState> entry : blocks.entrySet()) {
				FluidState fluid = entry.getValue().getFluidState();
				if (fluid.isEmpty()) continue;

				RelPosKey rel = entry.getKey();
				BlockPos world = worldPos.apply(rel);
				TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getBlockStateModelSet()
					.get(fluid.createLegacyBlock()).particleMaterial().sprite();
				int colour = fluid.is(FluidTags.WATER)
					? ARGB.opaque(level.getBiome(world).value().getWaterColor())
					: 0xFFFFFFFF;
				int light = LightCoordsUtil.getLightCoords(level, world);
				float top = holds(blocks, rel, Direction.UP, fluid.getType()) ? 1.0f : SOURCE_HEIGHT;

				for (Direction face : Direction.values()) {
					if (holds(blocks, rel, face, fluid.getType()) || covered(blocks, rel, face)) continue;
					face(pose, buffer, rel, face, top, sprite, colour, light);
				}
			}
		});
	}

	private static boolean holds(Map<RelPosKey, BlockState> blocks, RelPosKey rel, Direction toward, Fluid fluid) {
		BlockState next = blocks.get(neighbour(rel, toward));
		return next != null && next.getFluidState().getType().isSame(fluid);
	}

	private static boolean covered(Map<RelPosKey, BlockState> blocks, RelPosKey rel, Direction toward) {
		BlockState next = blocks.get(neighbour(rel, toward));
		return next != null && next.isSolidRender();
	}

	private static RelPosKey neighbour(RelPosKey rel, Direction toward) {
		return new RelPosKey(rel.x() + toward.getStepX(), rel.y() + toward.getStepY(), rel.z() + toward.getStepZ());
	}

	/** One face of the box, wound to face outward. */
	private static void face(PoseStack.Pose pose, VertexConsumer buffer, RelPosKey rel, Direction face, float top,
			TextureAtlasSprite sprite, int colour, int light) {
		float x0 = rel.x(), x1 = rel.x() + 1, y0 = rel.y(), y1 = rel.y() + top, z0 = rel.z(), z1 = rel.z() + 1;
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
		float[][] corners = switch (face) {
			case UP -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
			case DOWN -> new float[][] {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
			case NORTH -> new float[][] {{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
			case SOUTH -> new float[][] {{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
			case WEST -> new float[][] {{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
			case EAST -> new float[][] {{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
		};
		float[][] uvs = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
		for (int i = 0; i < 4; i++) {
			buffer.addVertex(pose, corners[i][0], corners[i][1], corners[i][2]).setColor(colour)
				.setUv(uvs[i][0], uvs[i][1]).setLight(light)
				.setNormal(pose, face.getStepX(), face.getStepY(), face.getStepZ());
		}
	}
}
