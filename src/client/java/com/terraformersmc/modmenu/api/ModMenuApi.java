/*
 * Copied from Mod Menu (https://github.com/TerraformersMC/ModMenu), MIT License,
 * Copyright (c) 2018-2020 Prospector; the full notice is LICENSE_modmenu in this jar.
 *
 * The same types with the same signatures, so a mod built against Mod Menu loads its entrypoint
 * under Pandorical without Mod Menu installed. Whichever copy the game loads when both are
 * present, every caller finds the methods it was compiled against.
 */
package com.terraformersmc.modmenu.api;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public interface ModMenuApi {
	/** Pandorical's own mod list stands in for Mod Menu's. */
	static Screen createModsScreen(Screen previous) {
		return new justfatlard.pandorical.client.mods.ModListScreen(previous);
	}

	static Component createModsButtonText() {
		return Component.translatable("pandorical.mods.button");
	}

	/** A factory whose screen is null: Mod Menu's own default, which offers no config screen. */
	default ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> null;
	}

	default UpdateChecker getUpdateChecker() {
		return null;
	}

	default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
		return Map.of();
	}

	default Map<String, UpdateChecker> getProvidedUpdateCheckers() {
		return Map.of();
	}

	default void attachModpackBadges(Consumer<String> consumer) {
	}
}
