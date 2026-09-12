package justfatlard.pandorical;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.management.ObjectName;

/**
 * A trace of what Pandorical is doing, for crashes on machines nobody here can reach. On when this
 * jar's file name contains {@code diagnostic}.
 *
 * <p>The same trace is the Windows client <b>load guard</b>, kept only while the game loads. Which
 * part of the guard (trace, JVM log, sampler) stops the native load crash is unknown; keep it
 * whole.
 */
public final class Diagnostics {
	private Diagnostics() {}

	public static final boolean ON = detect();
	private static final long START = System.currentTimeMillis();
	private static FileOutputStream file;
	/** Past this, standard out only: a guard raised over and over must not fill a disk. */
	private static final long MOST_TRACE_BYTES = 64L << 20;
	private static long traceBytes;

	private static final boolean WINDOWS_CLIENT = detectWindowsClient();
	private static final Path GUARD_SETTING = FabricLoader.getInstance().getConfigDir()
		.resolve("pandorical").resolve("load-guard.properties");
	private static final long STARTUP_WINDOW_MILLIS = 180_000;
	public static final long JOIN_WINDOW_MILLIS = 90_000;

	private static volatile boolean guard = WINDOWS_CLIENT && readGuard();
	private static volatile long guardUntil = guard ? START + STARTUP_WINDOW_MILLIS : 0L;
	private static volatile boolean jvmLogging;

	public static boolean active() {
		return ON || (guard && System.currentTimeMillis() < guardUntil);
	}

	public static boolean windowsClient() {
		return WINDOWS_CLIENT;
	}

	public static boolean guarding() {
		return guard;
	}

	public static void setGuarding(boolean on) {
		guard = WINDOWS_CLIENT && on;
		try {
			Files.createDirectories(GUARD_SETTING.getParent());
			Files.writeString(GUARD_SETTING, "# Windows only: see Diagnostics in Pandorical\nenabled=" + on + "\n");
		} catch (IOException ignored) {
		}
	}

	public static void guardFor(long millis) {
		if (!guard) return;
		guardUntil = Math.max(guardUntil, System.currentTimeMillis() + millis);
		mark("load guard up for " + millis / 1000 + "s");
		jvmLog();
	}

	private static boolean readGuard() {
		try {
			if (!Files.exists(GUARD_SETTING)) return true;
			Properties props = new Properties();
			try (var reader = Files.newBufferedReader(GUARD_SETTING)) {
				props.load(reader);
			}
			return !"false".equalsIgnoreCase(props.getProperty("enabled", "true").trim());
		} catch (Throwable ignored) {
			return true;
		}
	}

	private static boolean detectWindowsClient() {
		try {
			return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
				&& System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * Has the JVM keep its own log beside the trace. The crash this is for kills the JVM before it
	 * can write a report; this log is written a line at a time, so each compiler thread's last line
	 * names what it was compiling.
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

	public static synchronized void jvmLogOff() {
		if (ON || !jvmLogging) return;
		try {
			diagnosticCommand("vmLog", jvmLogOutput(), "what=all=off");
			jvmLogging = false;
		} catch (Throwable ignored) {
			// Left on, it only grows to its own size cap.
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

	/** Compiler thread names with their native ids, the ids the JVM log uses. */
	public static String compilerThreads() {
		try {
			StringBuilder found = new StringBuilder();
			for (String line : diagnosticCommand("threadPrint").split("\n")) {
				if (!line.contains("CompilerThread")) continue;
				Matcher name = Pattern.compile("\"([^\"]+)\"").matcher(line);
				Matcher nid = Pattern.compile("nid=(0x[0-9a-fA-F]+|\\d+)").matcher(line);
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
		Object result = ManagementFactory.getPlatformMBeanServer().invoke(
			new ObjectName("com.sun.management:type=DiagnosticCommand"), operation,
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
				if (Files.exists(path)) {
					Files.move(path, path.resolveSibling("pandorical-trace-previous.log"),
						StandardCopyOption.REPLACE_EXISTING);
				}
				file = new FileOutputStream(path.toFile(), false);
			}
			if (traceBytes > MOST_TRACE_BYTES) return;
			byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
			traceBytes += bytes.length;
			file.write(traceBytes > MOST_TRACE_BYTES ? "[pandorical-trace] full; standard out only from here\n".getBytes(StandardCharsets.UTF_8) : bytes);
		} catch (IOException ignored) {
		}
	}
}
