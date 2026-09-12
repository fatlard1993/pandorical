package justfatlard.pandorical.settings;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;

/**
 * Which mod each command came from. Nothing in the game records who registered a command, so the
 * owner is read off the classes of each node's command and requirement lambdas.
 */
public final class ModCommands {
	private ModCommands() {}

	public record Entry(String usage, boolean ops) {}

	private static final int MAX_DEPTH = 6;
	private static final int MAX_ENTRIES = 60;

	private static Map<String, List<Entry>> byMod;
	private static Map<Class<?>, String> owners = new HashMap<>();

	public static synchronized List<Entry> of(String modId, ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return List.of();
		if (byMod == null) byMod = read(server, player);
		return byMod.getOrDefault(modId, List.of());
	}

	/** The dispatcher is rebuilt on a datapack reload. */
	public static synchronized void forget() {
		byMod = null;
		owners = new HashMap<>();
	}

	/** From the player's own source: a command that needs a player refuses the console. */
	private static Map<String, List<Entry>> read(MinecraftServer server, ServerPlayer player) {
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		CommandSourceStack ops = player.createCommandSourceStack()
			.withPermission(PermissionSet.ALL_PERMISSIONS);
		CommandSourceStack anyone = ops
			.withPermission(PermissionSet.NO_PERMISSIONS);
		Map<String, List<Entry>> found = new LinkedHashMap<>();
		for (CommandNode<CommandSourceStack> root : dispatcher.getRoot().getChildren()) {
			String mod = ownerOf(root);
			if (mod == null || mod.equals("minecraft")) continue;
			List<Entry> entries = found.computeIfAbsent(mod, key -> new ArrayList<>());
			walk(root, "", false, ops, anyone, entries, 0);
		}
		return found;
	}

	private static void walk(CommandNode<CommandSourceStack> node, String prefix, boolean opsSoFar,
			CommandSourceStack ops, CommandSourceStack anyone, List<Entry> out, int depth) {
		if (depth > MAX_DEPTH || out.size() >= MAX_ENTRIES) return;
		boolean gated = opsSoFar || !node.canUse(anyone);
		String here = prefix.isEmpty() ? node.getUsageText() : prefix + " " + node.getUsageText();
		if (node.getCommand() != null) out.add(new Entry("/" + here, gated));
		CommandNode<CommandSourceStack> redirect = node.getRedirect();
		if (redirect != null) {
			out.add(new Entry("/" + here + " → /" + redirect.getName(), gated));
			return;
		}
		for (CommandNode<CommandSourceStack> child : node.getChildren()) {
			if (!child.canUse(ops)) continue;
			walk(child, here, gated, ops, anyone, out, depth + 1);
		}
	}

	private static String ownerOf(CommandNode<CommandSourceStack> root) {
		Deque<CommandNode<CommandSourceStack>> pending = new ArrayDeque<>();
		Set<CommandNode<CommandSourceStack>> seen = new HashSet<>();
		pending.add(root);
		seen.add(root);
		int looked = 0;
		while (!pending.isEmpty() && looked++ < 200) {
			CommandNode<CommandSourceStack> node = pending.poll();
			for (Object part : new Object[] {node.getCommand(), node.getRequirement()}) {
				if (part == null) continue;
				String mod = ownerOfClass(part.getClass());
				if (mod != null) return mod;
			}
			for (CommandNode<CommandSourceStack> child : node.getChildren()) {
				if (seen.add(child)) pending.add(child);
			}
		}
		return null;
	}

	/**
	 * A lambda has no code source of its own, so its declaring class is asked. The package match
	 * places classes the loader cannot, as in a dev run where every mod is a directory.
	 */
	private static String ownerOfClass(Class<?> type) {
		Class<?> owner = type;
		String name = owner.getName();
		int lambda = name.indexOf("$$Lambda");
		if (lambda > 0) {
			try {
				owner = Class.forName(name.substring(0, lambda), false, type.getClassLoader());
			} catch (Throwable ignored) {
			}
		}
		String cached = owners.get(owner);
		if (cached != null) return cached.isEmpty() ? null : cached;
		String mod = byJar(owner);
		if (mod == null) mod = byPackage(owner.getName());
		owners.put(owner, mod == null ? "" : mod);
		return mod;
	}

	private static String byJar(Class<?> type) {
		Path from;
		try {
			var source = type.getProtectionDomain().getCodeSource();
			if (source == null || source.getLocation() == null) return null;
			from = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
		} catch (Throwable ignored) {
			return null;
		}
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			// Per mod: a mod nested in another's jar throws here, and must not end the search.
			try {
				for (Path path : mod.getOrigin().getPaths()) {
					if (from.startsWith(path.toAbsolutePath().normalize())) return mod.getMetadata().getId();
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static String byPackage(String className) {
		Set<String> words = words(className);
		String best = null;
		int longest = 0;
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String id = mod.getMetadata().getId();
			if (id.equals("minecraft") || id.equals("java") || id.startsWith("fabric")) continue;
			Set<String> wanted = words(id);
			if (wanted.isEmpty() || wanted.size() <= longest || !words.containsAll(wanted)) continue;
			best = id;
			longest = wanted.size();
		}
		return best;
	}

	private static Set<String> words(String text) {
		Set<String> out = new HashSet<>();
		StringBuilder word = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			boolean breaks = c == '.' || c == '-' || c == '_' || c == '$'
				|| (Character.isUpperCase(c) && word.length() > 0);
			if (breaks && word.length() > 0) {
				if (word.length() > 2) out.add(word.toString());
				word.setLength(0);
			}
			if (Character.isLetterOrDigit(c)) word.append(Character.toLowerCase(c));
		}
		if (word.length() > 2) out.add(word.toString());
		return out;
	}
}
