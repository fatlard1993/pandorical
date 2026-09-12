package justfatlard.pandorical.push;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.pandorical.api.AnimationApi;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.PlayAnimationS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** What each entity is animating, broadcast as it starts and stops and replayed to each arrival. */
public final class PlayingAnimations implements AnimationApi {
	public static final PlayingAnimations INSTANCE = new PlayingAnimations();

	/**
	 * What each entity is playing, so somebody who walks into view is told about an animation
	 * that started before they arrived. Keyed by network id and cleared when the entity goes.
	 */
	private static final Map<Integer, PlayAnimationS2C> PLAYING = new ConcurrentHashMap<>();

	private PlayingAnimations() {}

	@Override
	public void play(Entity entity, String animationId, boolean looping) {
		var payload = new PlayAnimationS2C(entity.getId(), animationId, looping);
		PLAYING.put(entity.getId(), payload);
		broadcast(entity, payload);
	}

	@Override
	public void stop(Entity entity) {
		PLAYING.remove(entity.getId());
		broadcast(entity, new PlayAnimationS2C(entity.getId(), "", false));
	}

	private static void broadcast(Entity entity, PlayAnimationS2C payload) {
		if (!(entity.level() instanceof ServerLevel level)) return;

		for (ServerPlayer player : level.players()) {
			if (!PandoricalApi.hasCapability(player, Capabilities.ANIMATIONS)) continue;
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** Catch a joining client up on everything already playing. */
	public static void sendAllTo(ServerPlayer player) {
		if (!PandoricalApi.hasCapability(player, Capabilities.ANIMATIONS)) return;

		for (var payload : PLAYING.values()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** An entity that has gone is not playing anything. */
	public static void forget(int entityId) {
		PLAYING.remove(entityId);
	}
}
