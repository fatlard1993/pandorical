package justfatlard.pandorical.client.contextmodel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Models chosen by a block's neighbours. A model cannot see the world, so the choice is made
 * where the chunk compiler looks the model up. Providers register only models that exist.
 */
@Environment(EnvType.CLIENT)
public final class ContextModels {
	private ContextModels() {}

	public static final ThreadLocal<BlockAndTintGetter> LEVEL = new ThreadLocal<>();
	public static final ThreadLocal<BlockPos> POS = new ThreadLocal<>();

	public interface Provider {
		void scan(ResourceManager resources, Registrar add);

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
	private static boolean any;

	public static boolean active() {
		return any;
	}

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
				any = !wanted.isEmpty();
			});
	}

	public static BlockStateModel pick(BlockStateModelSet models, BlockState state) {
		BlockStateModel own = models.get(state);
		if (!any) return own;
		BlockAndTintGetter level = LEVEL.get();
		BlockPos pos = POS.get();
		if (level == null || pos == null) return own;
		Lookup lookup = key -> ((FabricModelManager) Minecraft.getInstance().getModelManager()).getModel(key);
		for (Provider provider : PROVIDERS) {
			BlockStateModel chosen = provider.pick(state, level, pos, lookup);
			if (chosen != null) return chosen;
		}
		return own;
	}
}
