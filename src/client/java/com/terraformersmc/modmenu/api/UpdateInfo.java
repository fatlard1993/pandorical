/*
 * Copied from Mod Menu (https://github.com/TerraformersMC/ModMenu), MIT License,
 * Copyright (c) 2018-2020 Prospector; the full notice is LICENSE_modmenu in this jar.
 *
 * The same types with the same signatures, so a mod built against Mod Menu loads its entrypoint
 * under Pandorical without Mod Menu installed. Whichever copy the game loads when both are
 * present, every caller finds the methods it was compiled against.
 */
package com.terraformersmc.modmenu.api;

import net.minecraft.network.chat.Component;

public interface UpdateInfo {
	boolean isUpdateAvailable();

	default Component getUpdateMessage() {
		return null;
	}

	String getDownloadLink();

	UpdateChannel getUpdateChannel();
}
