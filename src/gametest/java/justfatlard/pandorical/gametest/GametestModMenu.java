package justfatlard.pandorical.gametest;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A config screen declared the way a mod declares one for Mod Menu, for the mods screens to open. */
public final class GametestModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return Config::new;
	}

	public static final class Config extends Screen {
		private final Screen parent;

		Config(Screen parent) {
			super(Component.literal("Gametest config"));
			this.parent = parent;
		}

		@Override
		public void onClose() {
			minecraft.gui.setScreen(parent);
		}
	}
}
