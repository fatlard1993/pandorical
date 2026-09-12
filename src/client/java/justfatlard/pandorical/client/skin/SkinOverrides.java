package justfatlard.pandorical.client.skin;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.SkinOverrideS2C;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Skins the server has asked this client to draw instead of the ones profiles carry.
 *
 * <p>The image arrives as bytes and is registered straight into the texture manager, which is the
 * whole point of doing it here: a client resolves a profile's skin through authlib, and authlib
 * only accepts texture URLs from domains it fetches from Mojang. A server cannot get its own skin
 * file into that path at all. Registering the picture directly goes around it.
 */
public final class SkinOverrides {
	private SkinOverrides() {}

	private record Worn(Identifier texture, PlayerModelType model) {}

	private static final Map<UUID, Worn> WORN = new ConcurrentHashMap<>();

	/**
	 * The same overrides as the game's head renderers want them, built once per subject.
	 *
	 * <p>A head asks for its face every frame, and the render info it gets back lazily creates GPU
	 * state on first use. Keyed by subject rather than by profile so every head of one player shares
	 * the one entry; dropped with the override it was built from.
	 */
	private static final Map<UUID, PlayerSkinRenderCache.RenderInfo> HEADS = new ConcurrentHashMap<>();

	/** Apply, or take off when the image is empty. */
	public static void handle(SkinOverrideS2C payload) {
		if (payload.png().length == 0) {
			remove(payload.subject());
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Identifier id = Identifier.fromNamespaceAndPath("pandorical",
			"skin/" + payload.subject().toString().replace("-", ""));

		// The texture owns the image from here: it keeps the pixels, and the upload it records is
		// run by the render backend later in the frame. Closing the image on the way out of this
		// method freed that memory first, and on Windows the upload then read freed memory and
		// took the whole process down four seconds after joining.
		NativeImage image;
		try {
			image = NativeImage.read(payload.png());
		} catch (Exception e) {
			Pandorical.LOGGER.warn("Unreadable skin override for {}", payload.subject(), e);
			return;
		}
		// Released first: registering over a live id leaks the old texture, and a player
		// changing skin twice in a session is exactly when that happens.
		remove(payload.subject());
		client.getTextureManager().register(id, new DynamicTexture(() -> id.toString(), image));

		WORN.put(payload.subject(),
			new Worn(id, payload.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE));
	}

	private static void remove(UUID subject) {
		HEADS.remove(subject);
		Worn worn = WORN.remove(subject);
		if (worn != null) Minecraft.getInstance().getTextureManager().release(worn.texture());
	}

	/** The skin to draw for this player, or null to let the profile's own answer stand. */
	public static PlayerSkin forPlayer(UUID subject, PlayerSkin theirs) {
		Worn worn = WORN.get(subject);
		if (worn == null) return null;

		// Patched rather than rebuilt, so only the body and the arm width are ours. A cape, an
		// elytra texture and whether the skin counts as secure belong to the player, and an
		// override is about the skin alone.
		return theirs.with(PlayerSkin.Patch.create(
						// Both ids given explicitly: the second is the path the renderer binds, and this
			// texture was registered straight into the texture manager under that exact id rather
			// than living in a resource pack, so the one-argument form would derive a path to a
			// file that does not exist and draw nothing.
			java.util.Optional.of(new ClientAsset.ResourceTexture(worn.texture(), worn.texture())),
			java.util.Optional.empty(),
			java.util.Optional.empty(),
			java.util.Optional.of(worn.model())));
	}

	/** Whether a head carrying this profile has an override to wear. */
	public static boolean dresses(ResolvableProfile profile) {
		UUID subject = subjectOf(profile);
		return subject != null && WORN.containsKey(subject);
	}

	/**
	 * The face to draw on a head carrying this profile, or null to let the game's answer stand.
	 *
	 * <p>Patched over what the game resolved, the same way {@link #forPlayer} does for the player,
	 * and with no further patch on top: a head's own skin patch component is already in the game's
	 * answer, and the server's override is meant to win over it.
	 */
	public static PlayerSkinRenderCache.RenderInfo forHead(PlayerSkinRenderCache cache,
			ResolvableProfile profile, PlayerSkinRenderCache.RenderInfo theirs) {
		UUID subject = subjectOf(profile);
		if (subject == null || theirs == null || !WORN.containsKey(subject)) return null;

		return HEADS.computeIfAbsent(subject, id -> {
			PlayerSkin worn = forPlayer(id, theirs.playerSkin());
			return worn == null ? theirs
				: cache.new RenderInfo(theirs.gameProfile(), worn, PlayerSkin.Patch.EMPTY);
		});
	}

	/** The player a head belongs to, when the profile knows; a head named but not resolved does not. */
	private static UUID subjectOf(ResolvableProfile profile) {
		GameProfile partial = profile.partialProfile();
		return partial == null ? null : partial.id();
	}

	/** Dropped wholesale on disconnect: the ids mean nothing on the next server. */
	public static void clearAll() {
		for (UUID subject : Map.copyOf(WORN).keySet()) remove(subject);
		WORN.clear();
		HEADS.clear();
	}
}
