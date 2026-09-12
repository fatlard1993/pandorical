package justfatlard.pandorical.client.keepsake;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.KeepsakeApi;
import justfatlard.pandorical.client.mixin.ClientCommonListenerAccessor;
import justfatlard.pandorical.protocol.KeepsakeStoreS2C;
import justfatlard.pandorical.protocol.KeepsakesAskConfigS2C;
import justfatlard.pandorical.protocol.KeepsakesConfigC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;

/**
 * One keepsake file per server id and address the player connected to. Not the id alone: any
 * server that has seen an id could claim it, but not the player's address.
 */
public final class ClientKeepsakes {
	private ClientKeepsakes() {}

	private static final Gson GSON = new Gson();

	/** Stores are accepted only on the connection that asked. */
	private record Asked(Connection connection, String serverId, String address) {}

	private static volatile Asked asked;

	public static void register() {
		ClientConfigurationNetworking.registerGlobalReceiver(KeepsakesAskConfigS2C.TYPE, (payload, context) -> {
			var listener = (ClientCommonListenerAccessor) context.packetListener();
			Asked now = new Asked(listener.pandorical$connection(), payload.serverId(), addressOf(listener.pandorical$serverData()));
			asked = now;
			context.responseSender().sendPacket(new KeepsakesConfigC2S(read(now)));
		});

		ClientPlayNetworking.registerGlobalReceiver(KeepsakeStoreS2C.TYPE, (payload, context) -> {
			Asked now = asked;
			if (now == null || now.connection() != context.player().connection.getConnection()) return;
			if (!now.serverId().equals(payload.serverId())) return;
			if (payload.key().length() > KeepsakeApi.LONGEST_KEY || payload.value().length() > KeepsakeApi.LONGEST_VALUE) return;

			Map<String, String> values = read(now);
			if (payload.value().isEmpty()) values.remove(payload.key());
			else if (values.containsKey(payload.key()) || values.size() < KeepsakeApi.MOST_KEYS) values.put(payload.key(), payload.value());
			else return;
			write(now, values);
		});
	}

	/** Every local world shares one file. */
	private static String addressOf(ServerData server) {
		if (server == null) return "local";
		ServerAddress address = ServerAddress.parseString(server.ip);
		return address.getHost().toLowerCase(Locale.ROOT) + ":" + address.getPort();
	}

	/** Null for an id not shaped like a server's; the check also keeps it a safe file name. */
	private static Path fileFor(Asked asked) {
		if (asked.serverId() == null || !asked.serverId().matches("[0-9a-f-]{36}")) return null;
		String name = asked.serverId() + "@" + addressHash(asked.address()) + ".json";
		return FabricLoader.getInstance().getConfigDir().resolve("pandorical").resolve("keepsakes").resolve(name);
	}

	private static String addressHash(String address) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 8);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Map<String, String> read(Asked asked) {
		Path file = fileFor(asked);
		if (file == null || !Files.exists(file)) return new HashMap<>();
		try {
			Map<String, String> values = GSON.fromJson(Files.readString(file), new TypeToken<Map<String, String>>() {}.getType());
			return values == null ? new HashMap<>() : new HashMap<>(values);
		} catch (IOException | RuntimeException e) {
			Pandorical.LOGGER.warn("[pandorical] could not read {}: {}", file, e.getMessage());
			return new HashMap<>();
		}
	}

	private static void write(Asked asked, Map<String, String> values) {
		Path file = fileFor(asked);
		if (file == null) return;
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(values));
		} catch (IOException e) {
			Pandorical.LOGGER.warn("[pandorical] could not write {}: {}", file, e.getMessage());
		}
	}
}
