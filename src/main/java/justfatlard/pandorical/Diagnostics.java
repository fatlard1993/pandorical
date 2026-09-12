package justfatlard.pandorical;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * A trail of what Pandorical was doing, for a crash on a machine nobody here can reach.
 *
 * <p>On when this jar's file name says {@code diagnostic}, so turning it on is handing somebody a
 * differently named jar and nothing else: no setting, no launcher arguments. Every line goes to
 * standard out, which is the log a launcher shows and a player can paste, and to
 * {@code logs/pandorical-trace.log}, written unbuffered so a process that dies mid-sentence still
 * leaves everything it had said. The run before keeps its trail as
 * {@code logs/pandorical-trace-previous.log}, so relaunching after a crash does not write over it.
 *
 * <p>The same trail doubles as the <b>load guard</b>, on every Windows client unless turned off:
 * everything the diagnostic jar does, but only while the game is loading - the first three minutes
 * after launch, and the first ninety seconds of every join - and nothing after. Windows players
 * crashed natively inside the JVM while loading, at a different point each time, and a player on
 * the diagnostic jar stopped crashing at all. Nobody knows which part of it is doing that: the
 * JVM's own log is written a line at a time and so queues up threads loading classes and compiling
 * at once, and the sampler stops the whole JVM to read every thread's stack ten times a second and
 * for a full thread dump once a second. So the guard is all of it, in the
 * windows where the crashes were and nowhere else, and should a crash get through anyway, the logs
 * it leaves are the ones that say where.
 */
public final class Diagnostics {
	private Diagnostics() {}

	/** This jar is named "diagnostic": everything, from launch, for as long as the sampler runs. */
	public static final boolean ON = detect();
	private static final long START = System.currentTimeMillis();
	private static FileOutputStream file;
	/** Past this the trail goes to standard out alone: a guard raised over and over must not fill a disk. */
	private static final long MOST_TRACE_BYTES = 64L << 20;
	private static long traceBytes;

	private static final boolean WINDOWS_CLIENT = detectWindowsClient();
	private static final Path GUARD_SETTING = FabricLoader.getInstance().getConfigDir()
		.resolve("pandorical").resolve("load-guard.properties");
	/** How long after launch the guard stands: startup, the first resource load and the title screen. */
	private static final long STARTUP_WINDOW_MILLIS = 180_000;
	/** How long a join is guarded: the configuration phase, its resource reload and arriving in the world. */
	public static final long JOIN_WINDOW_MILLIS = 90_000;

	private static volatile boolean guard = WINDOWS_CLIENT && readGuard();
	/** Until when the guard is up, in wall-clock millis; nothing past it. */
	private static volatile long guardUntil = guard ? START + STARTUP_WINDOW_MILLIS : 0L;
	private static volatile boolean jvmLogging;

	/** Whether the trail is being kept right now: always on the diagnostic jar, within a window for the guard. */
	public static boolean active() {
		return ON || (guard && System.currentTimeMillis() < guardUntil);
	}

	/** Whether this is a client the load guard would stand over at all. */
	public static boolean windowsClient() {
		return WINDOWS_CLIENT;
	}

	public static boolean guarding() {
		return guard;
	}

	/** The player's choice, kept in a file of its own, and in force from the next window. */
	public static void setGuarding(boolean on) {
		guard = WINDOWS_CLIENT && on;
		try {
			java.nio.file.Files.createDirectories(GUARD_SETTING.getParent());
			java.nio.file.Files.writeString(GUARD_SETTING, "# Windows only: see Diagnostics in Pandorical\nenabled=" + on + "\n");
		} catch (IOException ignored) {
			// A setting that did not save is a setting that reverts next launch, nothing worse.
		}
	}

	/** Raise the guard for a while from now, if it is on. A later window extends an open one. */
	public static void guardFor(long millis) {
		if (!guard) return;
		guardUntil = Math.max(guardUntil, System.currentTimeMillis() + millis);
		mark("load guard up for " + millis / 1000 + "s");
		jvmLog();
	}

	private static boolean readGuard() {
		try {
			if (!java.nio.file.Files.exists(GUARD_SETTING)) return true;
			java.util.Properties props = new java.util.Properties();
			try (var reader = java.nio.file.Files.newBufferedReader(GUARD_SETTING)) {
				props.load(reader);
			}
			return !"false".equalsIgnoreCase(props.getProperty("enabled", "true").trim());
		} catch (Throwable ignored) {
			return true;
		}
	}

	private static boolean detectWindowsClient() {
		try {
			return FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT
				&& System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * Has the JVM itself keep a log beside ours, from here on, of what its compilers start on,
	 * which threads come and go, and its collections and safepoints.
	 *
	 * <p>The crash this is for dies inside the JVM, and so suddenly that it cannot write its own
	 * report. The JVM writes this log a line at a time as it goes, so the last line each compiler
	 * thread wrote is what it was compiling when the process went. Asked for through the same
	 * command interface {@code jcmd} uses, so nobody has to add a launcher argument.
	 */
	public static synchronized void jvmLog() {
		if (!active() || jvmLogging) return;
		try {
			String result = diagnosticCommand("vmLog", jvmLogOutput(),
				"what=jit+compilation=debug,os+thread=info,class+load=info,safepoint=info,gc=info",
				"decorators=uptimemillis,tid,tags");
			jvmLogging = result.isBlank();
			mark("jvm log " + (jvmLogging ? "on: " + jvmLogPath() : "refused: " + result.strip()));
		} catch (Throwable e) {
			mark("jvm log unavailable: " + e);
		}
	}

	/** The guard's window closed: the JVM stops writing its log, and the file stays for reading. */
	public static synchronized void jvmLogOff() {
		if (ON || !jvmLogging) return;
		try {
			diagnosticCommand("vmLog", jvmLogOutput(), "what=all=off");
			jvmLogging = false;
		} catch (Throwable ignored) {
			// Left on, it is a log that keeps growing to its own size cap. Harmless.
		}
	}

	private static Path jvmLogPath() {
		return FabricLoader.getInstance().getGameDir().resolve("logs").resolve("pandorical-jvm.log");
	}

	private static String jvmLogOutput() {
		Path path = jvmLogPath();
		path.getParent().toFile().mkdirs();
		return "output=\"file=" + path.toAbsolutePath().toString().replace('\\', '/') + "\"";
	}

	/** The compiler threads and their native ids, which are the ids the JVM log names them by. */
	public static String compilerThreads() {
		try {
			StringBuilder found = new StringBuilder();
			for (String line : diagnosticCommand("threadPrint").split("\n")) {
				if (!line.contains("CompilerThread")) continue;
				java.util.regex.Matcher name = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(line);
				java.util.regex.Matcher nid = java.util.regex.Pattern.compile("nid=(0x[0-9a-fA-F]+|\\d+)").matcher(line);
				if (!name.find() || !nid.find()) continue;
				String id = nid.group(1);
				long decimal = id.startsWith("0x") ? Long.parseLong(id.substring(2), 16) : Long.parseLong(id);
				found.append(found.isEmpty() ? "" : ", ").append(name.group(1)).append("=").append(decimal);
			}
			return found.toString();
		} catch (Throwable e) {
			return "unavailable: " + e;
		}
	}

	private static String diagnosticCommand(String operation, String... args) throws Exception {
		Object result = java.lang.management.ManagementFactory.getPlatformMBeanServer().invoke(
			new javax.management.ObjectName("com.sun.management:type=DiagnosticCommand"), operation,
			new Object[] {args}, new String[] {String[].class.getName()});
		return result == null ? "" : result.toString();
	}

	private static boolean detect() {
		try {
			return FabricLoader.getInstance().getModContainer("pandorical")
				.map(mod -> mod.getOrigin().getPaths().stream()
					.anyMatch(path -> path.getFileName().toString().toLowerCase().contains("diagnostic")))
				.orElse(false);
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static synchronized void mark(String what) {
		if (!active()) return;
		String line = String.format("[pandorical-trace] +%6dms [%s] %s", System.currentTimeMillis() - START,
			Thread.currentThread().getName(), what);
		System.out.println(line);
		System.out.flush();
		try {
			if (file == null) {
				Path path = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("pandorical-trace.log");
				path.getParent().toFile().mkdirs();
				if (java.nio.file.Files.exists(path)) {
					java.nio.file.Files.move(path, path.resolveSibling("pandorical-trace-previous.log"),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				file = new FileOutputStream(path.toFile(), false);
			}
			if (traceBytes > MOST_TRACE_BYTES) return;
			byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
			traceBytes += bytes.length;
			file.write(traceBytes > MOST_TRACE_BYTES ? "[pandorical-trace] full; standard out only from here\n".getBytes(StandardCharsets.UTF_8) : bytes);
		} catch (IOException ignored) {
			// The trail is for somebody else's crash; it does not get to cause one.
		}
	}
}
