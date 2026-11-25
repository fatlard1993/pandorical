package justfatlard.pandorical.client.mods;

import com.mojang.blaze3d.platform.NativeImage;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.ClientModAboutC2S;
import justfatlard.pandorical.protocol.ModIconS2C;
import justfatlard.pandorical.settings.ModCatalog;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mod icons the game's resources cannot reach, as textures at {@link ModCatalog#ICON_PREFIX}: a
 * local mod's from its own jar, and a server-only mod's as the server sent it. On the client thread.
 */
@Environment(EnvType.CLIENT)
public final class ClientModIcons {
	private ClientModIcons() {}

	/** Each local mod's icon texture, and whether its jar gave something drawable. */
	private static final Map<Identifier, Boolean> LOCAL = new HashMap<>();
	private static final Set<Identifier> REGISTERED = new HashSet<>();

	/**
	 * {@link ModCatalog#aboutOf}, with its icon registered first when it names one of these
	 * textures, and no icon when the jar's would not read.
	 */
	public static ClientModAboutC2S.About aboutOf(ModContainer container) {
		ClientModAboutC2S.About about = ModCatalog.aboutOf(container);
		Identifier id = ours(about.icon());
		if (id == null) return about;
		boolean drawn = LOCAL.computeIfAbsent(id, key -> {
			byte[] png = ModCatalog.iconBytes(container);
			return png != null && register(key, png);
		});
		return drawn ? about : new ClientModAboutC2S.About(about.id(), about.license(), about.contributors(), "",
			about.links(), about.bundled());
	}

	/** A local mod's own icon outranks the server's. */
	public static void receive(ModIconS2C payload) {
		Identifier id = Identifier.tryParse(ModCatalog.ICON_PREFIX + payload.mod());
		if (id != null && !LOCAL.getOrDefault(id, false)) register(id, payload.png());
	}

	/** Whether {@code id} has something to draw, rather than the missing-texture checkerboard. */
	public static boolean drawable(Identifier id) {
		return REGISTERED.contains(id) || Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
	}

	private static Identifier ours(String icon) {
		return icon.startsWith(ModCatalog.ICON_PREFIX) ? Identifier.tryParse(icon) : null;
	}

	private static boolean register(Identifier id, byte[] png) {
		// The texture owns the image: never close it here.
		NativeImage image;
		try {
			image = NativeImage.read(png);
		} catch (Exception e) {
			Pandorical.LOGGER.warn("Unreadable mod icon {}", id, e);
			return false;
		}
		// Registering over a live id leaks the old texture.
		if (REGISTERED.remove(id)) Minecraft.getInstance().getTextureManager().release(id);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
		REGISTERED.add(id);
		return true;
	}
}
