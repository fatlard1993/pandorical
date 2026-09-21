package justfatlard.pandorical.client.settings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.ClientModFilesC2S;
import justfatlard.pandorical.settings.ModCatalog;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * The player's own mods folder: which jars are in it, and switching one off or on.
 *
 * <p>Switching off is renaming the jar to {@code .disabled}, which is the only thing the loader
 * understands: it takes what it finds at startup and there is no unloading afterwards. So nothing
 * here takes effect until the game is restarted, and every message says so.
 *
 * <p>A disabled jar is invisible to the loader, so its name and version are read straight out of
 * its own {@code fabric.mod.json}. Without that a mod switched off would vanish from the screen
 * that switched it off, and the only way back would be a file manager.
 *
 * <p>What leaves the machine is the list this builds: every mod's name, version and jar file name,
 * switched-off ones included, and nothing from inside the files. That is written down in the
 * readme, where somebody deciding whether to install this can read it before they do, rather than
 * said in chat to somebody who has already joined and is trying to play.
 */
public final class ClientModFiles {
	private ClientModFiles() {}

	private static final String OFF = ".disabled";

	/** Ticks the confirm buttons stay dead, so a click already falling cannot land on one. */
	private static final int ARM_TICKS = 20;

	/** File names this client has reported, and may therefore be asked to rename. */
	private static final Map<String, Path> reported = new LinkedHashMap<>();

	/**
	 * Files this screen will not offer to rename whatever the server asks. Pandorical's own jar is
	 * the one that matters: switching it off takes away the screen that would switch it back on,
	 * and the way back is a file manager and knowing that {@code .disabled} is a thing. The
	 * loader's plumbing is here because renaming it breaks the game rather than a feature.
	 */
	private static final Set<String> locked = new HashSet<>();

	/** The question already on screen, so a server cannot stack a tower of them. */
	private static Screen asking;

	/** A jar is off when its name says so, and its name is the only thing that ever says so. */
	private static boolean isOff(String file) {
		return file.endsWith(OFF);
	}

	/** The name the file takes when switched the other way. */
	private static String flipped(String file) {
		return isOff(file) ? file.substring(0, file.length() - OFF.length()) : file + OFF;
	}

	private static Path modsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	/** Tell the server what is in the folder, on joining and after every change. */
	public static void report() {
		List<ClientModFilesC2S.Entry> entries = new ArrayList<>();
		reported.clear();
		locked.clear();

		// The loaded ones, by their own metadata: the loader already read all of it.
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			Path jar = jarOf(container);
			if (jar == null) continue;
			String file = jar.getFileName().toString();
			String id = container.getMetadata().getId();
			if (id.equals(Pandorical.MOD_ID) || ModCatalog.isPlumbing(id)) locked.add(file);
			reported.put(file, jar);
			entries.add(new ClientModFilesC2S.Entry(container.getMetadata().getId(),
				container.getMetadata().getName(),
				container.getMetadata().getVersion().getFriendlyString(), file, true));
		}

		// The switched-off ones, which the loader has never heard of.
		try (Stream<Path> files = Files.list(modsDir())) {
			for (Path path : files.filter(p -> p.getFileName().toString().endsWith(OFF)).toList()) {
				String file = path.getFileName().toString();
				reported.put(file, path);
				entries.add(read(path, file));
			}
		} catch (Exception e) {
			Pandorical.LOGGER.debug("[pandorical] could not list the mods folder", e);
		}

		if (ClientPlayNetworking.canSend(ClientModFilesC2S.TYPE)) {
			ClientPlayNetworking.send(new ClientModFilesC2S(entries));
		}
	}

	/** Its own metadata, out of the jar the loader skipped. */
	private static ClientModFilesC2S.Entry read(Path jar, String file) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			var entry = zip.getEntry("fabric.mod.json");
			if (entry != null) {
				try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
					JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
					String id = json.has("id") ? json.get("id").getAsString() : file;
					String name = json.has("name") ? json.get("name").getAsString() : id;
					String version = json.has("version") ? json.get("version").getAsString() : "";
					return new ClientModFilesC2S.Entry(id, name, version, file, false);
				}
			}
		} catch (Exception e) {
			Pandorical.LOGGER.debug("[pandorical] could not read {}", file, e);
		}
		// Unreadable, but still switchable: the file name is the only handle needed.
		return new ClientModFilesC2S.Entry(file, flipped(file), "", file, false);
	}

	/**
	 * A jar's path, where the mod is one: a mod nested inside another, or one loaded from a
	 * directory, has nothing here to rename.
	 */
	private static Path jarOf(ModContainer container) {
		if (container.getContainingMod().isPresent()) return null;
		for (Path path : container.getOrigin().getPaths()) {
			if (path.getFileName() == null) continue;
			String name = path.getFileName().toString();
			// The loader read its origin at startup and never revisits it, so a jar renamed
			// since is still named here. Without this check the same mod is reported twice,
			// once live and once off, and the screen offers to disable the one already gone.
			if (name.endsWith(".jar") && path.getParent() != null
					&& path.getParent().equals(modsDir()) && Files.isRegularFile(path)) {
				return path;
			}
		}
		return null;
	}

	/**
	 * Put the server's request to the player. The screen is the server's, but the mods folder is
	 * the player's: a server that can rename a jar unasked can switch off Pandorical itself, or
	 * every mod the player has. So the client asks, every time, and the player answers.
	 *
	 * <p>The server names a file and nothing else. Which way it goes, what the question says, and
	 * what the rename does are all read off that one name here, so the question the player answers
	 * is always the operation that runs.
	 */
	public static void ask(String file) {
		if (reported.get(file) == null) {
			say("Pandorical did not recognise that mod file, so nothing was changed.");
			return;
		}
		if (locked.contains(file)) {
			say("Pandorical will not switch " + file + " off from here: the game, or this screen,"
				+ " needs it to run.");
			return;
		}
		Minecraft client = Minecraft.getInstance();
		// One question at a time. Otherwise a server sends one request per tick and buries the
		// player under a tower of dialogs, each one holding the last as the screen to go back to.
		if (asking != null && client.gui.screen() == asking) return;

		boolean enable = isOff(file);
		Screen previous = client.gui.screen();
		ConfirmScreen confirm = new ConfirmScreen(
			yes -> {
				asking = null;
				if (yes) toggle(file);
				client.gui.setScreen(previous);
			},
			Component.translatable(enable ? "pandorical.mods.enable_title" : "pandorical.mods.disable_title"),
			Component.translatable(enable ? "pandorical.mods.enable_message" : "pandorical.mods.disable_message", file),
			Component.translatable(enable ? "pandorical.mods.enable_yes" : "pandorical.mods.disable_yes"),
			CommonComponents.GUI_CANCEL);
		// The server chooses the moment this appears, and it can appear under a click already on
		// its way down. Vanilla arms its own confirm screens on a clock for the same reason.
		confirm.setDelay(ARM_TICKS);
		asking = confirm;
		client.gui.setScreen(confirm);
	}

	/** Do what the player agreed to, to a file this client has already named. */
	public static void toggle(String file) {
		Path path = reported.get(file);
		if (path == null) {
			say("Pandorical did not recognise that mod file, so nothing was changed.");
			return;
		}
		if (locked.contains(file)) return;

		boolean enable = isOff(file);
		Path to = path.resolveSibling(flipped(file));
		// Renaming onto a name already taken destroys what is there, and ATOMIC_MOVE does it
		// without raising anything at all, so the catch below would never see it and the player
		// would be told it worked. Whoever disabled create.jar last week and has installed a
		// fresh one since is one press from losing it.
		if (Files.exists(to)) {
			say("There is already a file called " + to.getFileName() + " in your mods folder, so"
				+ " nothing has been changed. Move or delete that one first.");
			return;
		}
		try {
			// No ATOMIC_MOVE: without it the filesystem refuses a target that exists, which is
			// the same guard as above enforced where the race cannot reach it.
			Files.move(path, to);
		} catch (Exception e) {
			say("Pandorical could not " + (enable ? "enable " : "disable ") + file + ", so nothing"
				+ " has been changed. On Windows the game holds a loaded jar open, and that one"
				+ " has to wait until you have quit.");
			Pandorical.LOGGER.warn("[pandorical] could not rename {} to {}", file, to.getFileName(), e);
			return;
		}
		say((enable ? "Enabled " : "Disabled ") + to.getFileName()
			+ ". Restart your game for it to take effect.");
		report();
	}

	/** Leaving a server ends its business with this folder. */
	public static void forget() {
		reported.clear();
		locked.clear();
		asking = null;
	}

	private static void say(String message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) client.player.sendSystemMessage(Component.literal(message));
		Pandorical.LOGGER.info("[pandorical] {}", message);
	}
}
