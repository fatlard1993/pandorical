package justfatlard.pandorical.client.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.client.mixin.ClientCommonListenerAccessor;
import justfatlard.pandorical.protocol.ClientModPartS2C;
import justfatlard.pandorical.protocol.ClientModsOfferS2C;
import justfatlard.pandorical.protocol.ClientModsWantC2S;
import justfatlard.pandorical.protocol.InstallKeptS2C;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Client mods kept by the server being joined: a newer Pandorical, and the suite's client-only
 * mods, installed and kept current unless the player switches one off.
 *
 * <p>Jars are downloaded during the login and checked, then left in a folder of their own where
 * the loader never looks. They go into the mods folder only when the player, at a menu, says yes,
 * and only run once the game restarts, so saying yes closes the game: playing on after an install
 * is playing on the old version against a server that sent the new one. A server can therefore
 * hand over bytes, but never code the player did not agree to.
 *
 * <p>Opting out of one mod is switching it off from the Mods screen, which leaves its jar in the
 * folder as {@code .disabled}: a mod switched off is never installed or updated again. Saying no
 * to the question skips those versions; a newer one asks again.
 *
 * <p>An old jar is deleted, not overwritten: a running game reads classes out of it lazily, and
 * on Linux and macOS the open file outlives its name. Windows will not delete a jar in use, so
 * there an update waits for the game to quit.
 */
public final class KeptClientMods {
	private KeptClientMods() {}

	/** Pandorical is about a megabyte; this is room to grow, not room to be flooded. */
	private static final int LARGEST = 32 * 1024 * 1024;

	private static final String ID_SHAPE = "[a-z][a-z0-9_-]{0,63}";

	/** A version safe to put in a file name, and nothing else. */
	private static final String VERSION_SHAPE = "[0-9A-Za-z.+-]{1,64}";

	/** Ticks the install buttons stay dead, so a click already on its way cannot land on one. */
	private static final int ARM_TICKS = 20;

	private static final class Download {
		final ClientModsOfferS2C.Entry entry;
		final ByteArrayOutputStream bytes;
		int next;

		Download(ClientModsOfferS2C.Entry entry) {
			this.entry = entry;
			this.bytes = new ByteArrayOutputStream(entry.size());
		}
	}

	private static Connection downloadingOn;
	private static final Map<String, Download> downloads = new HashMap<>();
	private static boolean stagedAny;

	/** The disconnect a too-old client owes its server, held until the jars are in. */
	private static Connection owingOn;
	private static Component owed;

	private static boolean askedThisRun;

	private record Staged(String id, String version, Path file) {}

	public static void register() {
		ClientConfigurationNetworking.registerGlobalReceiver(ClientModsOfferS2C.TYPE, (payload, context) -> {
			Connection connection = ((ClientCommonListenerAccessor) context.packetListener()).pandorical$connection();
			List<ClientModsOfferS2C.Entry> wanted = wanted(payload.mods());
			context.responseSender().sendPacket(new ClientModsWantC2S(wanted.stream().map(ClientModsOfferS2C.Entry::id).toList()));
			synchronized (KeptClientMods.class) {
				downloadingOn = connection;
				downloads.clear();
				stagedAny = false;
				for (ClientModsOfferS2C.Entry entry : wanted) downloads.put(entry.id(), new Download(entry));
				if (downloads.isEmpty()) settle(connection);
				else Pandorical.LOGGER.info("[pandorical] taking {} from this server",
					wanted.stream().map(entry -> entry.id() + " " + entry.version()).toList());
			}
		});

		ClientConfigurationNetworking.registerGlobalReceiver(ClientModPartS2C.TYPE, (payload, context) -> {
			Connection connection = ((ClientCommonListenerAccessor) context.packetListener()).pandorical$connection();
			synchronized (KeptClientMods.class) {
				if (downloadingOn != connection) return;
				Download now = downloads.get(payload.id());
				if (now == null) return;
				if (payload.index() != now.next || now.bytes.size() + payload.data().length > now.entry.size()) {
					Pandorical.LOGGER.warn("[pandorical] {} arrived out of order; dropped it", payload.id());
					downloads.remove(payload.id());
				} else {
					now.bytes.writeBytes(payload.data());
					now.next++;
					if (now.next < payload.total()) return;
					downloads.remove(payload.id());
					if (stage(now.entry, now.bytes.toByteArray())) stagedAny = true;
				}
				if (downloads.isEmpty()) settle(connection);
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(InstallKeptS2C.TYPE, (payload, context) ->
			context.client().execute(() -> askNow(context.client())));

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (askedThisRun || !askingPlace(client, screen)) return;
			List<Staged> staged = staged();
			if (staged.isEmpty()) return;
			askedThisRun = true;
			client.execute(() -> ask(client, screen, staged));
		});
	}

	/**
	 * Where the question can be put: anywhere the player has stopped to look at a menu.
	 *
	 * <p>Not only the title screen and the server list. A launcher that joins a server on start
	 * (quick play, the way the house launches) never shows either: the game goes straight from
	 * loading into the world, and an update that waited for the title screen waited forever while
	 * the server sent it again on every join. So the pause menu asks too, in game, and so does the
	 * screen a too-old client is sent home to.
	 */
	private static boolean askingPlace(Minecraft client, Screen screen) {
		if (screen instanceof PauseScreen) return client.level != null;
		if (client.level != null) return false;
		return screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen
			|| screen instanceof DisconnectedScreen;
	}

	/**
	 * Asked for from the server's notice, so put now, whatever is on screen. Nothing staged means
	 * this version was turned down or never came; the player has just said they want it, so the
	 * refusal is forgotten and the next join fetches it again.
	 */
	private static void askNow(Minecraft client) {
		List<Staged> staged = staged();
		if (staged.isEmpty()) {
			try {
				Files.deleteIfExists(declinedFile());
			} catch (IOException e) {
				Pandorical.LOGGER.debug("[pandorical] could not forget what was declined", e);
			}
			client.gui.hud.getChat().addClientSystemMessage(Component.translatable("pandorical.update.none_staged"));
			return;
		}
		askedThisRun = true;
		ask(client, client.gui.screen(), staged);
	}

	/**
	 * Called in place of disconnecting a client too old for the server: when this server keeps
	 * client mods, the disconnect waits until they are in, and then says so.
	 */
	public static boolean holdDisconnect(Connection connection, Component reason) {
		if (!ClientConfigurationNetworking.canSend(ClientModsWantC2S.TYPE)) return false;
		synchronized (KeptClientMods.class) {
			owingOn = connection;
			owed = reason;
		}
		return true;
	}

	private static void settle(Connection connection) {
		downloadingOn = null;
		if (owingOn != connection) return;
		Component reason = stagedAny ? Component.translatable("pandorical.update.too_old_downloaded") : owed;
		owingOn = null;
		owed = null;
		Minecraft.getInstance().execute(() -> connection.disconnect(reason));
	}

	private static List<ClientModsOfferS2C.Entry> wanted(List<ClientModsOfferS2C.Entry> offered) {
		Set<String> off = switchedOff();
		Properties declined = declined();
		List<ClientModsOfferS2C.Entry> out = new ArrayList<>();
		for (ClientModsOfferS2C.Entry entry : offered) {
			if (!entry.id().matches(ID_SHAPE) || !entry.version().matches(VERSION_SHAPE)) continue;
			if (!entry.sha256().matches("[0-9a-f]{64}") || entry.size() <= 0 || entry.size() > LARGEST) continue;
			if (off.contains(entry.id())) continue;
			if (entry.version().equals(declined.getProperty(entry.id()))) continue;
			if (Files.exists(stageDir().resolve(stagedName(entry.id(), entry.version())))) continue;

			ModContainer mod = FabricLoader.getInstance().getModContainer(entry.id()).orElse(null);
			if (mod != null) {
				if (!newer(entry.version(), mod.getMetadata().getVersion().getFriendlyString())) continue;
				// Only a jar of its own in the mods folder can be swapped; a mod inside another,
				// or one from a dev environment, is somebody else's to update.
				if (jarOf(mod) == null) {
					Pandorical.LOGGER.info("[pandorical] not taking {} {}: {} is not a jar of its own in {}",
						entry.id(), entry.version(), mod.getOrigin().getPaths(), modsDir());
					continue;
				}
			}
			out.add(entry);
		}
		return out;
	}

	/** Checked, then written where the loader never looks. */
	private static boolean stage(ClientModsOfferS2C.Entry entry, byte[] bytes) {
		try {
			if (bytes.length != entry.size()) throw new IOException("expected " + entry.size() + " bytes, got " + bytes.length);
			String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			if (!sha.equals(entry.sha256())) throw new IOException("its hash does not match the offer");
			String[] says = declared(new ByteArrayInputStream(bytes));
			if (says == null || !entry.id().equals(says[0]) || !entry.version().equals(says[1])) {
				throw new IOException("it is not " + entry.id() + " " + entry.version());
			}

			Path dir = stageDir();
			Files.createDirectories(dir);
			clearStaged(entry.id());
			Path file = dir.resolve(stagedName(entry.id(), entry.version()));
			Path tmp = file.resolveSibling(file.getFileName() + ".part");
			Files.write(tmp, bytes);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			Pandorical.LOGGER.info("[pandorical] {} {} is downloaded; the game will ask to install it", entry.id(), entry.version());
			return true;
		} catch (Exception e) {
			Pandorical.LOGGER.warn("[pandorical] did not keep the server's {} {}: {}", entry.id(), entry.version(), e.getMessage());
			return false;
		}
	}

	/** The id and version a jar's own fabric.mod.json declares, or null. */
	private static String[] declared(java.io.InputStream jar) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(jar)) {
			for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
				if (!entry.getName().equals("fabric.mod.json")) continue;
				JsonObject json = JsonParser.parseReader(new InputStreamReader(zip, StandardCharsets.UTF_8)).getAsJsonObject();
				if (!json.has("id") || !json.has("version")) return null;
				return new String[] {json.get("id").getAsString(), json.get("version").getAsString()};
			}
		}
		return null;
	}

	/** Staged jars still worth installing; the rest are cleared away. */
	private static List<Staged> staged() {
		List<Staged> out = new ArrayList<>();
		Path dir = stageDir();
		if (!Files.isDirectory(dir)) return out;
		Set<String> off = switchedOff();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.toList()) {
				Staged staged = parseStaged(file);
				if (staged == null) continue;
				ModContainer mod = FabricLoader.getInstance().getModContainer(staged.id()).orElse(null);
				boolean useful = !off.contains(staged.id()) && (mod == null
					|| (jarOf(mod) != null && newer(staged.version(), mod.getMetadata().getVersion().getFriendlyString())));
				if (useful) out.add(staged);
				else Files.deleteIfExists(file);
			}
		} catch (IOException e) {
			Pandorical.LOGGER.debug("[pandorical] could not read {}", dir, e);
		}
		return out;
	}

	private static void ask(Minecraft client, Screen previous, List<Staged> staged) {
		List<String> lines = new ArrayList<>();
		for (Staged one : staged) {
			ModContainer mod = FabricLoader.getInstance().getModContainer(one.id()).orElse(null);
			lines.add(mod == null
				? one.id() + " " + one.version() + " (new)"
				: mod.getMetadata().getName() + " " + one.version() + " (you have " + mod.getMetadata().getVersion().getFriendlyString() + ")");
		}
		ConfirmScreen confirm = new ConfirmScreen(
			yes -> {
				if (yes && install(staged)) {
					client.exitWorldAndClose();
					return;
				}
				if (!yes) decline(staged);
				client.gui.setScreen(previous);
			},
			Component.translatable("pandorical.update.title"),
			Component.translatable("pandorical.update.message", String.join(", ", lines)),
			Component.translatable("pandorical.update.yes"),
			Component.translatable("pandorical.update.no"));
		// Shown before it is armed: the delay switches the buttons off, and the buttons only exist
		// once the screen has been laid out. Armed first, it threw on every ask since 26.3, and the
		// question never once reached a player.
		client.gui.setScreen(confirm);
		confirm.setDelay(ARM_TICKS);
	}

	/** Whether every one is in, or will be when the game quits: only then is quitting worth it. */
	private static boolean install(List<Staged> staged) {
		Path mods = modsDir();
		boolean all = true;
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		Map<Path, Path[]> onQuit = new LinkedHashMap<>();
		for (Staged one : staged) {
			ModContainer mod = FabricLoader.getInstance().getModContainer(one.id()).orElse(null);
			Path old = mod == null ? null : jarOf(mod);
			Path target = mods.resolve(one.id() + "-" + one.version() + ".jar");
			try {
				if (Files.exists(target) && !target.equals(old)) {
					throw new IOException(target.getFileName() + " is already in the mods folder");
				}
				if (old != null && windows) {
					onQuit.put(one.file(), new Path[] {old, target});
					continue;
				}
				Path part = target.resolveSibling(target.getFileName() + ".part");
				Files.copy(one.file(), part, StandardCopyOption.REPLACE_EXISTING);
				Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
				// Two copies of one mod stop the game from starting, so the new one stays only
				// if the old one went.
				if (old != null && !target.equals(old)) {
					try {
						Files.delete(old);
					} catch (IOException e) {
						Files.deleteIfExists(target);
						throw e;
					}
				}
				Files.deleteIfExists(one.file());
				Pandorical.LOGGER.info("[pandorical] installed {} {}", one.id(), one.version());
			} catch (Exception e) {
				all = false;
				Pandorical.LOGGER.warn("[pandorical] could not install {} {}: put {} in {} by hand",
					one.id(), one.version(), one.file(), mods, e);
			}
		}
		if (!onQuit.isEmpty()) {
			try {
				installOnQuit(onQuit);
			} catch (IOException e) {
				all = false;
				Pandorical.LOGGER.warn("[pandorical] could not arrange the update for when the game quits: {}", e.getMessage());
			}
		}
		return all;
	}

	/**
	 * Windows holds a loaded jar shut until the game exits, so a small script outlives the game,
	 * waits for each old jar to let go, and only then moves its replacement in.
	 */
	private static void installOnQuit(Map<Path, Path[]> swaps) throws IOException {
		List<String> lines = new ArrayList<>(List.of("@echo off"));
		int n = 0;
		for (Map.Entry<Path, Path[]> swap : swaps.entrySet()) {
			Path staged = swap.getKey(), old = swap.getValue()[0], target = swap.getValue()[1];
			for (Path path : new Path[] {staged, old, target}) {
				String text = path.toString();
				if (text.contains("%") || text.contains("\"") || text.contains("!")) {
					throw new IOException("the path " + text + " cannot be named safely in a script");
				}
			}
			n++;
			lines.addAll(List.of(
				"for /l %%i in (1,1,120) do (",
				"  del /f /q \"" + old + "\" 2>nul",
				"  if not exist \"" + old + "\" goto swap" + n,
				"  ping -n 2 127.0.0.1 >nul",
				")",
				"goto next" + n,
				":swap" + n,
				"move /y \"" + staged + "\" \"" + target + "\" >nul",
				":next" + n));
		}
		Path script = stageDir().resolve("install.cmd");
		Files.writeString(script, String.join("\r\n", lines) + "\r\n");
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				new ProcessBuilder("cmd.exe", "/c", script.toString()).start();
			} catch (IOException e) {
				Pandorical.LOGGER.warn("[pandorical] could not start the update script {}", script, e);
			}
		}, "pandorical-update"));
	}

	private static void decline(List<Staged> staged) {
		Properties declined = declined();
		for (Staged one : staged) {
			declined.setProperty(one.id(), one.version());
			try {
				Files.deleteIfExists(one.file());
			} catch (IOException e) {
				Pandorical.LOGGER.debug("[pandorical] could not remove {}", one.file(), e);
			}
		}
		try {
			Path file = declinedFile();
			Files.createDirectories(file.getParent());
			StringBuilder text = new StringBuilder();
			for (String id : declined.stringPropertyNames()) text.append(id).append('=').append(declined.getProperty(id)).append('\n');
			justfatlard.pandorical.ConfigFiles.write(file, text.toString());
		} catch (IOException e) {
			Pandorical.LOGGER.debug("[pandorical] could not remember what was declined", e);
		}
	}

	/** Mod id to the version the player last said no to. */
	private static Properties declined() {
		Properties out = new Properties();
		Path file = declinedFile();
		if (!Files.exists(file)) return out;
		try (Reader reader = Files.newBufferedReader(file)) {
			out.load(reader);
		} catch (IOException e) {
			Pandorical.LOGGER.debug("[pandorical] could not read {}", file, e);
		}
		return out;
	}

	/** Ids of the mods the player switched off: their jars sit in the folder as .disabled. */
	private static Set<String> switchedOff() {
		Set<String> out = new HashSet<>();
		try (Stream<Path> files = Files.list(modsDir())) {
			for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".jar.disabled")).toList()) {
				try (ZipFile zip = new ZipFile(path.toFile())) {
					ZipEntry json = zip.getEntry("fabric.mod.json");
					if (json == null) continue;
					try (Reader reader = new InputStreamReader(zip.getInputStream(json), StandardCharsets.UTF_8)) {
						JsonObject read = JsonParser.parseReader(reader).getAsJsonObject();
						if (read.has("id")) out.add(read.get("id").getAsString());
					}
				} catch (Exception e) {
					Pandorical.LOGGER.debug("[pandorical] could not read {}", path, e);
				}
			}
		} catch (IOException e) {
			Pandorical.LOGGER.debug("[pandorical] could not list the mods folder", e);
		}
		return out;
	}

	private static void clearStaged(String id) throws IOException {
		try (Stream<Path> files = Files.list(stageDir())) {
			for (Path file : files.toList()) {
				Staged staged = parseStaged(file);
				if (staged != null && staged.id().equals(id)) Files.deleteIfExists(file);
			}
		}
	}

	private static boolean newer(String offered, String mine) {
		try {
			return Version.parse(offered).compareTo(Version.parse(mine)) > 0;
		} catch (VersionParsingException e) {
			return false;
		}
	}

	/** {@code id@version.jar}: a mod id may hold a hyphen, so the hyphen cannot be the divider. */
	private static String stagedName(String id, String version) {
		return id + "@" + version + ".jar";
	}

	private static Staged parseStaged(Path file) {
		String name = file.getFileName().toString();
		int at = name.indexOf('@');
		if (at < 0 || !name.endsWith(".jar")) return null;
		String id = name.substring(0, at);
		String version = name.substring(at + 1, name.length() - ".jar".length());
		if (!id.matches(ID_SHAPE) || !version.matches(VERSION_SHAPE)) return null;
		return new Staged(id, version, file);
	}

	private static Path modsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	private static Path stageDir() {
		return FabricLoader.getInstance().getGameDir().resolve("pandorical-update");
	}

	private static Path declinedFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("pandorical").resolve("client-mods-declined.properties");
	}

	/** A mod's own jar in the mods folder, or null when it is not one this can replace. */
	private static Path jarOf(ModContainer mod) {
		if (mod.getContainingMod().isPresent()) return null;
		for (Path path : mod.getOrigin().getPaths()) {
			if (path.getFileName() != null && path.getFileName().toString().endsWith(".jar")
					&& inModsDir(path) && Files.isRegularFile(path)) {
				return path;
			}
		}
		return null;
	}

	/** The loader resolves symlinks in a mod's path but not in the game dir's, so compare the files. */
	private static boolean inModsDir(Path path) {
		try {
			return path.getParent() != null && Files.isSameFile(modsDir(), path.getParent());
		} catch (IOException e) {
			return false;
		}
	}
}
