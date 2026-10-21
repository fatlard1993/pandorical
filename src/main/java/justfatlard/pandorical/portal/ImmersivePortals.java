package justfatlard.pandorical.portal;

import com.mojang.serialization.Codec;
import justfatlard.pandorical.protocol.ImmersivePortalsS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Optional;

/**
 * Portals you step through rather than wait in. The server's half is the wait: a player stands in
 * a nether portal for {@link #WAIT_TICKS} instead of vanilla's four seconds. The rest is the
 * client's, told by {@link ImmersivePortalsS2C}: the swirl thickens over that wait, covers the
 * change of world, and thins on the far side.
 */
public final class ImmersivePortals {
	private ImmersivePortals() {}

	/** Half a second: long enough to feel the portal take hold, short enough to walk through. */
	public static final int WAIT_TICKS = 10;

	private static volatile boolean byDefault = false;

	public static void setDefault(boolean on) {
		byDefault = on;
	}

	public static boolean enabled(MinecraftServer server) {
		Boolean chosen = Choice.get(server).chosen;
		return chosen != null ? chosen : byDefault;
	}

	public static void choose(MinecraftServer server, boolean on) {
		Choice choice = Choice.get(server);
		choice.chosen = on;
		choice.setDirty();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) sendTo(player);
	}

	public static void sendTo(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, ImmersivePortalsS2C.TYPE)) return;
		ServerPlayNetworking.send(player, new ImmersivePortalsS2C(enabled(player.level().getServer())));
	}

	/** How long a player waits in a nether portal, given vanilla's answer. */
	public static int wait(MinecraftServer server, int vanilla) {
		return enabled(server) ? Math.min(vanilla, WAIT_TICKS) : vanilla;
	}

	static final class Choice extends SavedData {
		static final Codec<Choice> CODEC = Codec.BOOL.optionalFieldOf("chosen").codec()
			.xmap(chosen -> {
				Choice choice = new Choice();
				choice.chosen = chosen.orElse(null);
				return choice;
			}, choice -> Optional.ofNullable(choice.chosen));

		private static final SavedDataType<Choice> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("pandorical", "immersive_portals"), Choice::new, CODEC, DataFixTypes.LEVEL);

		/** Null until an op chooses. */
		Boolean chosen;

		static Choice get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(TYPE);
		}
	}
}
