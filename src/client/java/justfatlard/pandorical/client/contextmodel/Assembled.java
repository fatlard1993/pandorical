package justfatlard.pandorical.client.contextmodel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;

import java.util.List;

/** Pieces drawn in order, so a piece laid over the same face as an earlier one lands on top of it. */
@Environment(EnvType.CLIENT)
record Assembled(List<BlockStateModel> pieces) implements BlockStateModel {
	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
		for (BlockStateModel piece : pieces) piece.collectParts(random, parts);
	}

	@Override
	public Material.Baked particleMaterial() {
		return pieces.getFirst().particleMaterial();
	}

	@Override
	public int materialFlags() {
		int flags = 0;
		for (BlockStateModel piece : pieces) flags |= piece.materialFlags();
		return flags;
	}
}
