package justfatlard.pandorical.maps;

import com.mojang.serialization.Codec;
import justfatlard.pandorical.api.MapsApi;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Who has asked not to be shown on maps, kept with the world by UUID, so a map of homes can ask about
 * a player who is offline. See {@link MapsApi}.
 */
public final class MapVisibility implements MapsApi {
	public static final MapVisibility INSTANCE = new MapVisibility();

	private MapVisibility() {}

	@Override
	public boolean shown(MinecraftServer server, UUID player) {
		return !Hidden.get(server).players.contains(player);
	}

	public static boolean shown(ServerPlayer player) {
		return INSTANCE.shown(player.level().getServer(), player.getUUID());
	}

	public static void show(ServerPlayer player, boolean on) {
		Hidden hidden = Hidden.get(player.level().getServer());
		if (on ? hidden.players.remove(player.getUUID()) : hidden.players.add(player.getUUID())) hidden.setDirty();
	}

	static final class Hidden extends SavedData {
		static final Codec<Hidden> CODEC = UUIDUtil.CODEC.listOf().xmap(
			list -> {
				Hidden hidden = new Hidden();
				hidden.players.addAll(list);
				return hidden;
			},
			hidden -> new ArrayList<>(hidden.players));
		private static final SavedDataType<Hidden> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("pandorical", "hidden_from_maps"), Hidden::new, CODEC, DataFixTypes.LEVEL);

		final Set<UUID> players = new HashSet<>();

		static Hidden get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(TYPE);
		}
	}
}
