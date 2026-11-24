package justfatlard.pandorical.client.mods;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import justfatlard.pandorical.Pandorical;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The config screens mods declared for Mod Menu, through its {@code modmenu} entrypoint, read the
 * way Mod Menu reads them: a mod's own factory, else one another mod provides for it.
 *
 * <p>Read once, on first use rather than at launch, and each entrypoint on its own: one that will
 * not load, because it leans on a config library that is not installed, costs only that mod its
 * button.
 */
@Environment(EnvType.CLIENT)
public final class ModConfigScreens {
	private ModConfigScreens() {}

	private static @Nullable Map<String, ConfigScreenFactory<?>> factories;

	private static synchronized Map<String, ConfigScreenFactory<?>> factories() {
		if (factories != null) return factories;
		Map<String, ConfigScreenFactory<?>> own = new HashMap<>();
		Map<String, ConfigScreenFactory<?>> provided = new HashMap<>();
		for (EntrypointContainer<ModMenuApi> container : FabricLoader.getInstance().getEntrypointContainers("modmenu", ModMenuApi.class)) {
			String id = container.getProvider().getMetadata().getId();
			try {
				ModMenuApi api = container.getEntrypoint();
				// Mod Menu's default factory makes no screen; only one the mod wrote itself counts.
				if (api.getClass().getMethod("getModConfigScreenFactory").getDeclaringClass() != ModMenuApi.class) {
					ConfigScreenFactory<?> factory = api.getModConfigScreenFactory();
					if (factory != null) own.put(id, factory);
				}
				Map<String, ConfigScreenFactory<?>> others = api.getProvidedConfigScreenFactories();
				if (others != null) others.forEach(provided::putIfAbsent);
			} catch (Throwable broken) {
				Pandorical.LOGGER.warn("[pandorical] {}'s Mod Menu entrypoint would not load, so it has no Configure button", id, broken);
			}
		}
		provided.putAll(own);
		factories = Map.copyOf(provided);
		return factories;
	}

	public static Set<String> configurable() {
		return factories().keySet();
	}

	public static boolean has(String modId) {
		return factories().containsKey(modId);
	}

	/** The mod's config screen over {@code parent}, or null when it has none or it failed to open. */
	public static @Nullable Screen create(String modId, Screen parent) {
		ConfigScreenFactory<?> factory = factories().get(modId);
		if (factory == null) return null;
		try {
			return factory.create(parent);
		} catch (Throwable broken) {
			Pandorical.LOGGER.warn("[pandorical] {}'s config screen failed to open", modId, broken);
			return null;
		}
	}
}
