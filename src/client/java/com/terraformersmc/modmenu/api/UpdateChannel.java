/*
 * Copied from Mod Menu (https://github.com/TerraformersMC/ModMenu), MIT License,
 * Copyright (c) 2018-2020 Prospector; the full notice is LICENSE_modmenu in this jar.
 */
package com.terraformersmc.modmenu.api;

public enum UpdateChannel {
	ALPHA,
	BETA,
	RELEASE;

	/** The player's choice in Mod Menu's settings where it is installed; release otherwise. */
	public static UpdateChannel getUserPreference() {
		try {
			Object option = Class.forName("com.terraformersmc.modmenu.config.ModMenuConfig").getField("UPDATE_CHANNEL").get(null);
			Object chosen = option.getClass().getMethod("getValue").invoke(option);
			if (chosen instanceof UpdateChannel channel) return channel;
		} catch (ReflectiveOperationException | LinkageError absent) {
			// No Mod Menu
		}
		return RELEASE;
	}
}
