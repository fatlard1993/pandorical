package justfatlard.pandorical.client.rail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

/**
 * Models chosen by what stands next to a block.
 *
 * <p>A model on its own no longer sees the world, so a block that wants to look different beside
 * a particular neighbour - a curve in a diagonal run, a gate joined to another - has to be
 * answered where the chunk compiler looks its model up, with the neighbours in hand. This is
 * that one place. Each {@link Provider} says which extra models it wants loaded, found by
 * scanning the resources so nothing is asked for that nobody shipped, and then picks one at
 * render time or declines.
 */
@Environment(EnvType.CLIENT)
public final class ContextModels {
	private ContextModels() {}

	/** What the compiler is looking at, stashed from the block-state read just before the model lookup. */
	public static final ThreadLocal<BlockAndTintGetter> LEVEL = new ThreadLocal<>();
	public static final ThreadLocal<BlockPos> POS = new ThreadLocal<>();

	public interface Provider {
		/** The extra models this provider can use that actually exist, registered through {@code add}. */
		void scan(ResourceManager resources, Registrar add);

		/** The model to draw for this state here, or null to leave the block's own. */
		BlockStateModel pick(BlockState state, BlockAndTintGetter level, BlockPos pos, Lookup models);
	}

	public interface Registrar {
		/** The model as it is, unturned. */
		ExtraModelKey<BlockStateModel> add(Identifier model);

		/** The model turned, the way a blockstate variant turns one, so a facing need not be baked in. */
		ExtraModelKey<BlockStateModel> add(Identifier model, ModelState state);
	}

	public interface Lookup {
		BlockStateModel get(ExtraModelKey<BlockStateModel> key);
	}

	private record Wanted(ExtraModelKey<BlockStateModel> key, Identifier model, ModelState state) {}

	private static final List<Provider> PROVIDERS = new ArrayList<>();
	private static boolean any;

	/** Whether any provider found models to pick from. */
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

	/** The compiler's question. */
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
