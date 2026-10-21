package justfatlard.pandorical.login;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.ClientModPartS2C;
import justfatlard.pandorical.protocol.ClientModsOfferS2C;
import justfatlard.pandorical.protocol.ClientModsWantC2S;
import justfatlard.pandorical.protocol.InstallKeptS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.util.Util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Client mods kept by the server: its own Pandorical and Fabric API, and every jar in the
 * {@code client-mods} folder beside {@code mods}, which the server itself never loads.
 *
 * <p>The client decides: it is told each mod's id, version, hash and size, and asks for the ones
 * it wants. Nothing it receives runs until the player agrees to install it and restarts the game.
 *
 * <p>The question goes first in the login, ahead of the content sync, so that a client too old
 * to read that sync still gets the new Pandorical before the format it cannot read arrives.
 */
public final class ServedClientMods {
	private ServedClientMods() {}

	/** Well under the configuration phase's packet cap, and a few packets for a whole jar. */
	private static final int PART_BYTES = 512 * 1024;

	/**
	 * Mods every client needs at the server's own version, offered from the server's mods folder
	 * so they keep in step with it without anybody copying them into {@code client-mods}.
	 */
	private static final List<String> SERVERS_OWN = List.of(Pandorical.MOD_ID, "fabric-api");

	private static final String ID_SHAPE = "[a-z][a-z0-9_-]{0,63}";

	/** Read once at startup: a jar on disk may be swapped for the next version before a restart. */
	private static Map<String, Jar> jars = Map.of();
	private static ClientModsOfferS2C offer;

	private record Jar(ClientModsOfferS2C.Entry entry, byte[] bytes) {}

	/** Logins whose question is still open, so a late answer does not send into a finished login. */
	private static final Set<ServerConfigurationPacketListenerImpl> asking =
		Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	public static void registerPayloads() {
		PayloadTypeRegistry.clientboundConfiguration().register(ClientModsOfferS2C.TYPE, ClientModsOfferS2C.STREAM_CODEC);
		PayloadTypeRegistry.clientboundConfiguration().register(ClientModPartS2C.TYPE, ClientModPartS2C.STREAM_CODEC);
		PayloadTypeRegistry.serverboundConfiguration().register(ClientModsWantC2S.TYPE, ClientModsWantC2S.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(InstallKeptS2C.TYPE, InstallKeptS2C.STREAM_CODEC);
	}

	/** After every other BEFORE_CONFIGURE listener, so the task this puts first stays first. */
	public static void register() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
		jars = readJars();
		// A client sees the answer channel only when this registers it, so a server with nothing
		// to hand out is one no client waits on.
		if (jars.isEmpty()) return;
		offer = new ClientModsOfferS2C(jars.values().stream().map(Jar::entry).toList());
		Pandorical.LOGGER.info("[pandorical] keeping {} client mod(s) for players: {}", jars.size(), jars.keySet());

		ServerConfigurationNetworking.registerGlobalReceiver(ClientModsWantC2S.TYPE, (payload, context) -> {
			var handler = context.packetListener();
			context.server().execute(() -> answered(handler, payload.ids()));
		});

		ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler, server) -> {
			if (!ServerConfigurationNetworking.canSend(handler, ClientModsOfferS2C.TYPE)) return;
			asking.add(handler);
			putFirst(handler, new Task(handler));
		});

		ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> asking.remove(handler));
	}

	private static void answered(ServerConfigurationPacketListenerImpl handler, List<String> ids) {
		if (!asking.remove(handler)) return;
		List<String> sent = new ArrayList<>();
		for (String id : ids) {
			Jar jar = jars.get(id);
			if (jar == null || sent.contains(id)) continue;
			byte[] bytes = jar.bytes();
			int total = (bytes.length + PART_BYTES - 1) / PART_BYTES;
			for (int i = 0; i < total; i++) {
				byte[] part = Arrays.copyOfRange(bytes, i * PART_BYTES, Math.min(bytes.length, (i + 1) * PART_BYTES));
				ServerConfigurationNetworking.send(handler, new ClientModPartS2C(id, i, total, part));
			}
			sent.add(id + " " + jar.entry().version());
		}
		if (!sent.isEmpty()) {
			var profile = handler.getOwner();
			Pandorical.LOGGER.info("[pandorical] sent {} to {}", sent,
				profile != null ? profile.name() : "(unknown profile)");
		}
		try {
			handler.completeTask(Task.TYPE);
		} catch (IllegalStateException e) {
			// Answered after the wait ran out; the login already went on.
		}
	}

	/** The version kept of each client mod, by id. */
	public static Map<String, String> versions() {
		Map<String, String> out = new LinkedHashMap<>();
		jars.forEach((id, jar) -> out.put(id, jar.entry().version()));
		return out;
	}

	private static Map<String, Jar> readJars() {
		Map<String, Jar> out = new LinkedHashMap<>();
		for (String id : SERVERS_OWN) {
			FabricLoader.getInstance().getModContainer(id).ifPresent(mod -> {
				for (Path path : mod.getOrigin().getPaths()) read(path, out);
			});
		}

		Path folder = FabricLoader.getInstance().getGameDir().resolve("client-mods");
		if (Files.isDirectory(folder)) {
			try (Stream<Path> files = Files.list(folder)) {
				files.sorted().forEach(path -> read(path, out));
			} catch (IOException e) {
				Pandorical.LOGGER.warn("[pandorical] could not list {}", folder, e);
			}
		}
		return out;
	}

	private static void read(Path path, Map<String, Jar> out) {
		if (path.getFileName() == null || !path.getFileName().toString().endsWith(".jar")
				|| !Files.isRegularFile(path)) return;
		try {
			byte[] bytes = Files.readAllBytes(path);
			String[] idVersion = declared(bytes);
			if (idVersion == null || !idVersion[0].matches(ID_SHAPE)) {
				Pandorical.LOGGER.warn("[pandorical] {} is not a Fabric mod; not offering it", path.getFileName());
				return;
			}
			if (out.containsKey(idVersion[0])) {
				Pandorical.LOGGER.warn("[pandorical] {} is a second copy of {}; not offering it", path.getFileName(), idVersion[0]);
				return;
			}
			String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			out.put(idVersion[0], new Jar(new ClientModsOfferS2C.Entry(idVersion[0], idVersion[1], sha, bytes.length), bytes));
		} catch (Exception e) {
			Pandorical.LOGGER.warn("[pandorical] could not read {} to offer it to players", path, e);
		}
	}

	/** The id and version a jar's fabric.mod.json declares, or null when it has none. */
	private static String[] declared(byte[] jar) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
			for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
				if (!entry.getName().equals("fabric.mod.json")) continue;
				JsonObject json = JsonParser.parseReader(new InputStreamReader(zip, StandardCharsets.UTF_8)).getAsJsonObject();
				if (!json.has("id") || !json.has("version")) return null;
				return new String[] {json.get("id").getAsString(), json.get("version").getAsString()};
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static void putFirst(ServerConfigurationPacketListenerImpl handler, ConfigurationTask task) {
		try {
			var field = ServerConfigurationPacketListenerImpl.class.getDeclaredField("configurationTasks");
			field.setAccessible(true);
			var queue = (Queue<ConfigurationTask>) field.get(handler);
			var reordered = new ArrayDeque<ConfigurationTask>();
			reordered.add(task);
			reordered.addAll(queue);
			queue.clear();
			queue.addAll(reordered);
		} catch (ReflectiveOperationException | RuntimeException e) {
			Pandorical.LOGGER.warn("[pandorical] could not put the client mods question first; asking it last", e);
			handler.addTask(task);
		}
	}

	/** Holds the login for the answer, but not past the wait: client mods must never hang a login. */
	private static final class Task implements ConfigurationTask {
		static final Type TYPE = new Type("pandorical:client_mods");
		private static final long WAIT_MILLIS = 10_000L;

		private final ServerConfigurationPacketListenerImpl handler;
		private long startedAt;

		Task(ServerConfigurationPacketListenerImpl handler) {
			this.handler = handler;
		}

		@Override
		public void start(Consumer<Packet<?>> sender) {
			startedAt = Util.getMillis();
			sender.accept(ServerConfigurationNetworking.createClientboundPacket(offer));
		}

		@Override
		public boolean tick() {
			if (Util.getMillis() - startedAt <= WAIT_MILLIS) return false;
			asking.remove(handler);
			return true;
		}

		@Override
		public Type type() {
			return TYPE;
		}
	}
}
