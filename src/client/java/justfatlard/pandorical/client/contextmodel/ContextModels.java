package justfatlard.pandorical.client.contextmodel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Models chosen by a block's neighbours. A block some provider covers has its model wrapped, and
 * the wrapper picks when it is drawn, through the Fabric renderer's world-aware quad emission, so
 * it holds under any renderer that implements it, vanilla's and Sodium's alike. Providers register
 * only models that exist.
 */
@Environment(EnvType.CLIENT)
public final class ContextModels {
	private ContextModels() {}

	public interface Provider {
		void scan(ResourceManager resources, Registrar add);

		/** Whether this provider may ever pick for the block; only those blocks are wrapped. */
		boolean covers(Block block);

		/** Null leaves the block's own model. */
		BlockStateModel pick(BlockState state, BlockAndTintGetter level, BlockPos pos, Lookup models);
	}

	public interface Registrar {
		ExtraModelKey<BlockStateModel> add(Identifier model);

		ExtraModelKey<BlockStateModel> add(Identifier model, ModelState state);
	}

	public interface Lookup {
		BlockStateModel get(ExtraModelKey<BlockStateModel> key);
	}

	private record Wanted(ExtraModelKey<BlockStateModel> key, Identifier model, ModelState state) {}

	private static final List<Provider> PROVIDERS = new ArrayList<>();
	public static void register(Provider provider) {
		PROVIDERS.add(provider);
	}

	public static void init() {
		PreparableModelLoadingPlugin.register(
			(shared, executor) -> CompletableFuture.supplyAsync(() -> {
				List<Wanted> wanted = new ArrayList<>();
				for (Provider provider : PROVIDERS) {
					provider.scan(shared.resourceManager(), new Registrar() {
						@Override
						public ExtraModelKey<BlockStateModel> add(Identifier model) {
							return add(model, BlockModelRotation.IDENTITY);
						}

						@Override
						public ExtraModelKey<BlockStateModel> add(Identifier model, ModelState state) {
							ExtraModelKey<BlockStateModel> key = ExtraModelKey.create(() -> model + "@" + state);
							wanted.add(new Wanted(key, model, state));
							return key;
						}
					});
				}
				return wanted;
			}, executor),
			(wanted, context) -> {
				for (Wanted w : wanted) {
					context.addModel(w.key(), SimpleUnbakedExtraModel.blockStateModel(w.model(), w.state()));
				}
				if (wanted.isEmpty()) return;
				context.modifyBlockModelAfterBake().register((model, baked) -> {
					Block block = baked.state().getBlock();
					for (Provider provider : PROVIDERS) {
						if (provider.covers(block)) return new Contextual(model);
					}
					return model;
				});
			});
	}

	private static BlockStateModel pick(BlockStateModel own, BlockState state, BlockAndTintGetter level, BlockPos pos) {
		Lookup lookup = key -> ((FabricModelManager) Minecraft.getInstance().getModelManager()).getModel(key);
		for (Provider provider : PROVIDERS) {
			BlockStateModel chosen = provider.pick(state, level, pos, lookup);
			if (chosen != null) return chosen;
		}
		return own;
	}

	/** A block's own model wherever no provider picks another; out of the world, always its own. */
	private record Contextual(BlockStateModel own) implements BlockStateModel {
		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
			pick(own, state, level, pos).emitQuads(emitter, level, pos, state, random, cullTest);
		}

		@Override
		public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
			return pick(own, state, level, pos).materialFlags(level, pos, state, random);
		}

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
			own.collectParts(random, parts);
		}

		@Override
		public Material.Baked particleMaterial() {
			return own.particleMaterial();
		}

		@Override
		public int materialFlags() {
			return own.materialFlags();
		}
	}
}
