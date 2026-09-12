package justfatlard.pandorical.client.keepsake;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.KeepsakeStoreS2C;
import justfatlard.pandorical.protocol.KeepsakesAskConfigS2C;
import justfatlard.pandorical.protocol.KeepsakesConfigC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The game's half of the keepsakes: one small file per server, in this game's config folder,
 * named by the id the server gave itself. Only that server is ever shown what is in its file.
 */
public final class ClientKeepsakes {
	private ClientKeepsakes() {}

	private static final Gson GSON = new Gson();

	public static void register() {
		ClientConfigurationNetworking.registerGlobalReceiver(KeepsakesAskConfigS2C.TYPE, (payload, context) ->
			context.responseSender().sendPacket(new KeepsakesConfigC2S(read(payload.serverId()))));

		ClientPlayNetworking.registerGlobalReceiver(KeepsakeStoreS2C.TYPE, (payload, context) -> {
			Map<String, String> values = read(payload.serverId());
			if (payload.value().isEmpty()) values.remove(payload.key());
			else values.put(payload.key(), payload.value());
			write(payload.serverId(), values);
		});
	}

	/** Null for anything that is not the shape of an id a server makes, which is also what keeps it a file name. */
	private static Path fileFor(String serverId) {
		if (serverId == null || !serverId.matches("[0-9a-f-]{36}")) return null;
		return FabricLoader.getInstance().getConfigDir().resolve("pandorical").resolve("keepsakes").resolve(serverId + ".json");
	}

	private static Map<String, String> read(String serverId) {
		Path file = fileFor(serverId);
		if (file == null || !Files.exists(file)) return new HashMap<>();
		try {
			Map<String, String> values = GSON.fromJson(Files.readString(file), new TypeToken<Map<String, String>>() {}.getType());
			return values == null ? new HashMap<>() : new HashMap<>(values);
		} catch (IOException | RuntimeException e) {
			Pandorical.LOGGER.warn("[pandorical] could not read {}: {}", file, e.getMessage());
			return new HashMap<>();
		}
	}

	private static void write(String serverId, Map<String, String> values) {
		Path file = fileFor(serverId);
		if (file == null) return;
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(values));
		} catch (IOException e) {
			Pandorical.LOGGER.warn("[pandorical] could not write {}: {}", file, e.getMessage());
		}
	}
}
