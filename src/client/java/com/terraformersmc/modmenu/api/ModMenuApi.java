/*
 * Copied from Mod Menu (https://github.com/TerraformersMC/ModMenu), MIT License,
 * Copyright (c) 2018-2020 Prospector; the full notice is LICENSE_modmenu in this jar.
 *
 * The package's types with Mod Menu's signatures, so a mod built against Mod Menu loads its
 * entrypoint under Pandorical without Mod Menu installed. With Mod Menu installed the game may load
 * either copy, so wherever Mod Menu's own answer differs, this copy asks Mod Menu for it.
 */
package com.terraformersmc.modmenu.api;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public interface ModMenuApi {
	/** Mod Menu's list where it is installed; Pandorical's stands in for it where it is not. */
	static Screen createModsScreen(Screen previous) {
		Object modMenus = ModMenuShim.call("com.terraformersmc.modmenu.gui.ModsScreen", null, previous);
		return modMenus instanceof Screen screen ? screen : new justfatlard.pandorical.client.mods.ModListScreen(previous);
	}

	static Component createModsButtonText() {
		Object modMenus = ModMenuShim.call("com.terraformersmc.modmenu.ModMenu", "createModsButtonText", true);
		return modMenus instanceof Component text ? text : Component.translatable("pandorical.mods.button");
	}

	/**
	 * A factory whose screen is null. Where Mod Menu is installed it is Mod Menu's own, which is how
	 * Mod Menu tells a mod with no config screen from one with.
	 */
	default ConfigScreenFactory<?> getModConfigScreenFactory() {
		Object modMenus = ModMenuShim.call("com.terraformersmc.modmenu.util.NullScreenFactory", null);
		return modMenus instanceof ConfigScreenFactory<?> factory ? factory : parent -> null;
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
