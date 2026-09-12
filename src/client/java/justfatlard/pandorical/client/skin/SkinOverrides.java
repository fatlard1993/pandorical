package justfatlard.pandorical.client.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.SkinOverrideS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-sent skins, registered straight into the texture manager: authlib accepts skin URLs only
 * from Mojang's domains.
 */
public final class SkinOverrides {
	private SkinOverrides() {}

	private record Worn(Identifier texture, PlayerModelType model) {}

	private static final Map<UUID, Worn> WORN = new ConcurrentHashMap<>();

	/** Built once per subject: a render info creates GPU state on first use. */
	private static final Map<UUID, PlayerSkinRenderCache.RenderInfo> HEADS = new ConcurrentHashMap<>();

	/** An empty image removes the override. */
	public static void handle(SkinOverrideS2C payload) {
		if (payload.png().length == 0) {
			remove(payload.subject());
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Identifier id = Identifier.fromNamespaceAndPath("pandorical",
			"skin/" + payload.subject().toString().replace("-", ""));

		// The texture owns the image, and its upload runs later in the frame: never close it here.
		NativeImage image;
		try {
			image = NativeImage.read(payload.png());
		} catch (Exception e) {
			Pandorical.LOGGER.warn("Unreadable skin override for {}", payload.subject(), e);
			return;
		}
		// Registering over a live id leaks the old texture.
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

	/** Null when there is no override. */
	public static PlayerSkin forPlayer(UUID subject, PlayerSkin theirs) {
		Worn worn = WORN.get(subject);
		if (worn == null) return null;

		// Patched, not rebuilt: the cape, elytra and secure flag stay the player's.
		return theirs.with(PlayerSkin.Patch.create(
			// Both ids explicit: the one-argument form derives a resource pack path, which this lacks.
			Optional.of(new ClientAsset.ResourceTexture(worn.texture(), worn.texture())),
			Optional.empty(),
			Optional.empty(),
			Optional.of(worn.model())));
	}

	public static boolean dresses(ResolvableProfile profile) {
		UUID subject = subjectOf(profile);
		return subject != null && WORN.containsKey(subject);
	}

	/**
	 * Null when there is no override. No patch goes on top: the head's own skin patch is already
	 * in {@code theirs}, and the override wins over it.
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

	/** Null for a head named but not resolved. */
	private static UUID subjectOf(ResolvableProfile profile) {
		GameProfile partial = profile.partialProfile();
		return partial == null ? null : partial.id();
	}

	public static void clearAll() {
		for (UUID subject : Map.copyOf(WORN).keySet()) remove(subject);
		WORN.clear();
		HEADS.clear();
	}
}
