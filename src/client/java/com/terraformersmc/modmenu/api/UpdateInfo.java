/*
 * Copied from Mod Menu (https://github.com/TerraformersMC/ModMenu), MIT License,
 * Copyright (c) 2018-2020 Prospector; the full notice is LICENSE_modmenu in this jar.
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
