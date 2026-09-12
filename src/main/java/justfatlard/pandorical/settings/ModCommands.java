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

/**
 * Which mod each command came from, and what it looks like typed out.
 *
 * <p>Nothing in the game records who registered a command: the dispatcher is one tree with every
 * mod's branches grafted onto it and no label saying whose is whose. So the owner is read off the
 * code instead. Every node carries the lambdas the mod wrote - what to run, and who may run it -
 * and a class knows which jar it was loaded from. The first node in a command's subtree that
 * resolves to a mod names the command; one that resolves to nothing is the game's own and is
 * left out of every mod's page.
 *
 * <p>Read once per server, because the dispatcher is built once and does not change while it
 * runs, and forgotten when the server stops.
 */
public final class ModCommands {
	private ModCommands() {}

	/** One usable form of a command: what to type, and whether it takes an operator to type it. */
	public record Entry(String usage, boolean ops) {}

	/** Deep enough for any command worth documenting; a redirect is followed no further than this. */
	private static final int MAX_DEPTH = 6;
	/** Enough for the wordiest command in the suite, and a stop against a tree that loops. */
	private static final int MAX_ENTRIES = 60;

	private static Map<String, List<Entry>> byMod;
	private static Map<Class<?>, String> owners = new HashMap<>();

	/** Every form of every command this mod registered, ops-only ones marked. Never null. */
	public static synchronized List<Entry> of(String modId, ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return List.of();
		if (byMod == null) byMod = read(server);
		return byMod.getOrDefault(modId, List.of());
	}

	/** The dispatcher is rebuilt on a datapack reload, so what was read off it no longer holds. */
	public static synchronized void forget() {
		byMod = null;
		owners = new HashMap<>();
	}

	private static Map<String, List<Entry>> read(MinecraftServer server) {
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		CommandSourceStack ops = server.createCommandSourceStack()
			.withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);
		CommandSourceStack anyone = ops
			.withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS);
		Map<String, List<Entry>> found = new LinkedHashMap<>();
		for (CommandNode<CommandSourceStack> root : dispatcher.getRoot().getChildren()) {
			String mod = ownerOf(root);
			// The game's own commands are the game's; this page is for what the mods added.
			if (mod == null || mod.equals("minecraft")) continue;
			List<Entry> entries = found.computeIfAbsent(mod, key -> new ArrayList<>());
			walk(root, "", false, ops, anyone, entries, 0);
		}
		return found;
	}

	/**
	 * Every path through this command that can actually be run, written the way it is typed.
	 *
	 * @param opsSoFar whether some step already taken needs an operator, since a branch under a
	 *                 gate is behind that gate however open its own door is
	 */
	private static void walk(CommandNode<CommandSourceStack> node, String prefix, boolean opsSoFar,
			CommandSourceStack ops, CommandSourceStack anyone, List<Entry> out, int depth) {
		if (depth > MAX_DEPTH || out.size() >= MAX_ENTRIES) return;
		boolean gated = opsSoFar || !node.canUse(anyone);
		String here = prefix.isEmpty() ? node.getUsageText() : prefix + " " + node.getUsageText();
		if (node.getCommand() != null) out.add(new Entry("/" + here, gated));
		// A redirect is another command's tree wearing this name; saying where it goes is more
		// use than copying it out, and it is how the game's own help writes one.
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

	/**
	 * The mod this command belongs to, or null for the game's own.
	 *
	 * <p>Breadth first, so the answer comes from the shallowest node that can give one: a mod's
	 * root literal usually carries its own permission check, and the run itself is a lambda in
	 * the mod either way.
	 */
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
	 * Which mod's jar a class came from.
	 *
	 * <p>The jar is the honest answer and the one asked for first: a class knows where it was
	 * loaded from, and the loader knows which mod owns that file. A lambda has no code source of
	 * its own, so it is asked about the class that declares it. Failing all of that the package
	 * is compared with the mod ids, which catches a class the loader cannot place - a dev run
	 * where every mod is a directory on one classpath, most of all.
	 */
	private static String ownerOfClass(Class<?> type) {
		Class<?> owner = type;
		String name = owner.getName();
		int lambda = name.indexOf("$$Lambda");
		if (lambda > 0) {
			try {
				owner = Class.forName(name.substring(0, lambda), false, type.getClassLoader());
			} catch (Throwable ignored) {
				// The declaring class is gone or unnameable; the package below still says enough.
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
			// Asked one mod at a time: a mod inside another mod's jar has no path of its own and
			// says so by throwing, and one such mod used to end the search for every mod after it.
			try {
				for (Path path : mod.getOrigin().getPaths()) {
					if (from.startsWith(path.toAbsolutePath().normalize())) return mod.getMetadata().getId();
				}
			} catch (Throwable ignored) {
				// Nested, or from nowhere on disk. Not this one, then.
			}
		}
		return null;
	}

	/**
	 * The mod whose id best matches this class's package, or null. Only ever a fallback: it is
	 * how a class gets placed in a development run, where every mod is a directory of classes
	 * rather than a jar the loader can name.
	 *
	 * <p>A mod id and a package say the same words in different dialects and not always in the
	 * same order - {@code spawn-lock-justfatlard} against {@code justfatlard.spawn_lock} - so
	 * both are cut into words and the id matches when every word of it is a word of the package.
	 * The id with the most words wins, so {@code village-mail} is not answered by {@code village}.
	 */
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

	/** The words in a name, however it spells its joins: dots, dashes, underscores, or capitals. */
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
