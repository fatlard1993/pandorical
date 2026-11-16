package justfatlard.pandorical.push;

import justfatlard.pandorical.api.AnimationApi;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.PlayAnimationS2C;
import justfatlard.pandorical.protocol.PlayAnimationV2S2C;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each entity plays. A client with the second channel is told only about entities it can
 * see, as it comes to see them, with how far in the animation is; one with only the first is told
 * about every entity in its world as an animation starts, and everything playing as it joins.
 */
public final class PlayingAnimations implements AnimationApi {
	public static final PlayingAnimations INSTANCE = new PlayingAnimations();

	/** @param startedAt server wall-clock millis */
	private record Playing(Entity entity, String animation, boolean looping, long startedAt) {
		PlayAnimationS2C old() {
			return new PlayAnimationS2C(entity.getId(), animation, looping);
		}

		PlayAnimationV2S2C now() {
			return new PlayAnimationV2S2C(entity.getId(), animation, looping,
				Math.max(0L, System.currentTimeMillis() - startedAt));
		}
	}

	private static final Map<Integer, Playing> PLAYING = new ConcurrentHashMap<>();

	private PlayingAnimations() {}

	@Override
	public void play(Entity entity, String animationId, boolean looping) {
		var playing = new Playing(entity, animationId, looping, System.currentTimeMillis());
		PLAYING.put(entity.getId(), playing);
		broadcast(entity, playing.old(), playing.now());
	}

	@Override
	public void stop(Entity entity) {
		PLAYING.remove(entity.getId());
		broadcast(entity, new PlayAnimationS2C(entity.getId(), "", false),
			new PlayAnimationV2S2C(entity.getId(), "", false, 0L));
	}

	private static void broadcast(Entity entity, PlayAnimationS2C old, PlayAnimationV2S2C now) {
		if (!(entity.level() instanceof ServerLevel level)) return;

		Set<ServerPlayer> seeing = null;
		for (ServerPlayer player : level.players()) {
			if (!PandoricalApi.hasCapability(player, Capabilities.ANIMATIONS)) continue;
			if (ServerPlayNetworking.canSend(player, PlayAnimationV2S2C.TYPE)) {
				if (seeing == null) seeing = new HashSet<>(PlayerLookup.tracking(entity));
				if (player == entity || seeing.contains(player)) ServerPlayNetworking.send(player, now);
			} else {
				ServerPlayNetworking.send(player, old);
			}
		}
	}

	/**
	 * As a player comes into view of an entity.
	 *
	 * @hidden
	 */
	public static void handleStartTracking(Entity entity, ServerPlayer player) {
		Playing playing = PLAYING.get(entity.getId());
		if (playing == null || playing.entity() != entity) return;
		if (!PandoricalApi.hasCapability(player, Capabilities.ANIMATIONS)
			|| !ServerPlayNetworking.canSend(player, PlayAnimationV2S2C.TYPE)) return;
		ServerPlayNetworking.send(player, playing.now());
	}

	/**
	 * As a player's client says what it reads: tracking began before that, so what it can already
	 * see is sent now. A client with only the first channel is sent everything playing.
	 *
	 * @hidden
	 */
	public static void sendAllTo(ServerPlayer player) {
		if (!PandoricalApi.hasCapability(player, Capabilities.ANIMATIONS)) return;

		boolean scoped = ServerPlayNetworking.canSend(player, PlayAnimationV2S2C.TYPE);
		for (Playing playing : PLAYING.values()) {
			if (!scoped) {
				ServerPlayNetworking.send(player, playing.old());
			} else if (playing.entity() == player || playing.entity().level() instanceof ServerLevel
					&& PlayerLookup.tracking(playing.entity()).contains(player)) {
				ServerPlayNetworking.send(player, playing.now());
			}
		}
	}

	/** @hidden */
	public static void forget(Entity entity) {
		Playing playing = PLAYING.get(entity.getId());
		if (playing != null && playing.entity() == entity) PLAYING.remove(entity.getId(), playing);
	}
}
