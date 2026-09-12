package justfatlard.pandorical.client.contextmodel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.HashMap;
import java.util.Map;

/**
 * A top slab's underside is half a block above where a ceiling model puts its ceiling, so a
 * ceiling block under one takes {@code <block>_hung_<facing>[_on]}, facing baked in.
 */
@Environment(EnvType.CLIENT)
public final class SlabHung implements ContextModels.Provider {
	private record Key(Block block, Direction facing, boolean powered) {}

	private static final Map<Key, ExtraModelKey<BlockStateModel>> KEYS = new HashMap<>();

	@Override
	public void scan(ResourceManager resources, ContextModels.Registrar add) {
		KEYS.clear();
		for (Block block : BuiltInRegistries.BLOCK) {
			BlockState state = block.defaultBlockState();
			if (!state.hasProperty(BlockStateProperties.ATTACH_FACE)
					|| !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
					|| !state.hasProperty(BlockStateProperties.POWERED)) {
				continue;
			}
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				for (boolean powered : new boolean[] {false, true}) {
					String path = id.getPath() + "_hung_" + facing.getSerializedName() + (powered ? "_on" : "");
					Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "models/block/" + path + ".json");
					if (resources.getResource(file).isPresent()) {
						KEYS.put(new Key(block, facing, powered),
							add.add(Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + path)));
					}
				}
			}
		}
	}

	@Override
	public BlockStateModel pick(BlockState state, BlockAndTintGetter level, BlockPos pos, ContextModels.Lookup models) {
		if (KEYS.isEmpty() || !state.hasProperty(BlockStateProperties.ATTACH_FACE)) return null;
		if (state.getValue(BlockStateProperties.ATTACH_FACE) != AttachFace.CEILING) return null;
		BlockState above = level.getBlockState(pos.above());
		if (!(above.getBlock() instanceof SlabBlock) || above.getValue(SlabBlock.TYPE) != SlabType.TOP) return null;
		ExtraModelKey<BlockStateModel> key = KEYS.get(new Key(state.getBlock(),
			state.getValue(BlockStateProperties.HORIZONTAL_FACING), state.getValue(BlockStateProperties.POWERED)));
		return key == null ? null : models.get(key);
	}
}
