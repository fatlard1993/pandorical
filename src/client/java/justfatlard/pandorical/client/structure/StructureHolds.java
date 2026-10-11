package justfatlard.pandorical.client.structure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import justfatlard.pandorical.api.RelPos;
import justfatlard.pandorical.client.structure.StructureManager.ClientStructure;
import justfatlard.pandorical.client.structure.StructureManager.StructurePoseSnapshot;
import justfatlard.pandorical.structure.DryCells;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Dry structures on the client: the sea kept out of the hold of a ship at sea, which stands where
 * the world has water. Nothing in a dry cell is in water, the camera included, and the water's
 * surface is not drawn across one.
 */
public final class StructureHolds {
	private StructureHolds() {}

	/**
	 * The two masks over a dry cell with no dry cell above it, as heights in the cell. They lie
	 * either side of the top of a still source block, at 8/9, so it is hidden from above and from
	 * below, wherever the camera is. Exact where a structure stands at a whole block's height, as a
	 * ship does; it holds the height it was christened at.
	 */
	private static final float MASK_ABOVE = 0.95f;
	private static final float MASK_BELOW = 0.82f;

	/** Whether this point of the world is in a dry cell of a structure, as it is drawn this tick. */
	public static boolean dryAt(double x, double y, double z) {
		for (ClientStructure structure : StructureManager.getActive()) {
			if (structure.dry.isEmpty() || !structure.visible) continue;
			StructurePoseSnapshot pose = structure.thisTick();
			if (structure.dry.holds(pose.x(), pose.y(), pose.z(), pose.yaw(), x, y, z)) return true;
		}
		return false;
	}

	/**
	 * Mask the water's surface off the structure's dry cells, as a boat masks it off its own floor:
	 * depth and no colour, so the water behind it is not drawn. The pose stack is the structure's.
	 */
	static void submitMask(ClientStructure structure, PoseStack poseStack, SubmitNodeCollector collector) {
		DryCells dry = structure.dry;
		if (dry.isEmpty()) return;
		collector.submitCustomGeometry(poseStack, RenderTypes.waterMask(), (pose, buffer) -> {
			for (RelPos cell : dry.tops()) {
				plane(pose, buffer, cell, cell.y() + MASK_ABOVE);
				plane(pose, buffer, cell, cell.y() + MASK_BELOW);
			}
		});
	}

	/** Both faces, so it masks seen from above and from below. */
	private static void plane(PoseStack.Pose pose, VertexConsumer buffer, RelPos cell, float y) {
		float x0 = cell.x(), x1 = cell.x() + 1, z0 = cell.z(), z1 = cell.z() + 1;
		buffer.addVertex(pose, x0, y, z0);
		buffer.addVertex(pose, x0, y, z1);
		buffer.addVertex(pose, x1, y, z1);
		buffer.addVertex(pose, x1, y, z0);

		buffer.addVertex(pose, x0, y, z0);
		buffer.addVertex(pose, x1, y, z0);
		buffer.addVertex(pose, x1, y, z1);
		buffer.addVertex(pose, x0, y, z1);
	}
}
