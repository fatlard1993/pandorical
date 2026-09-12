package justfatlard.pandorical.client.diag;

import java.util.Map;
import justfatlard.pandorical.Diagnostics;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Where the game's working threads are, ten times a second: for the first ten minutes on the
 * diagnostic jar, and for each window the load guard raises (see Diagnostics).
 *
 * <p>A native crash takes the process with it before anything can be caught or reported, so
 * the only record of where it happened is whatever was written down just before. This writes
 * down the render thread and the resource loaders as they go - only when what they are doing has
 * changed, so a thread sitting still costs one line - and the last lines before a crash name
 * the code, and the call into native code, that it died in.
 */
public final class StackSampler {
	private StackSampler() {}

	private static final long EVERY_MILLIS = 100;
	private static final long FOR_MILLIS = 600_000;
	private static final int FRAMES = 14;

	/** One sampler at a time; a window opening while one runs is simply more of the same run. */
	private static final AtomicBoolean running = new AtomicBoolean();

	/**
	 * Start sampling, for as long as {@link Diagnostics#active} says: ten minutes on the diagnostic
	 * jar, the length of a window for the load guard. Called at launch and again as each join
	 * raises the guard.
	 */
	public static void start() {
		if (!Diagnostics.active()) return;
		Diagnostics.jvmLog();
		if (!running.compareAndSet(false, true)) return;
		Thread sampler = new Thread(StackSampler::run, "pandorical-stack-sampler");
		sampler.setDaemon(true);
		sampler.start();
		Diagnostics.mark("stack sampler started: " + System.getProperty("java.vendor") + " "
			+ System.getProperty("java.version") + ", " + System.getProperty("os.name") + " "
			+ System.getProperty("os.version"));
	}

	private static void run() {
		Map<Thread, String> last = new HashMap<>();
		String compilers = "";
		long until = System.currentTimeMillis() + FOR_MILLIS;
		try {
			sample(last, compilers, until);
		} finally {
			Diagnostics.mark("stack sampler finished");
			Diagnostics.jvmLogOff();
			running.set(false);
			// A join that raised the guard in the moment this one was stopping gets its own.
			if (Diagnostics.active()) start();
		}
	}

	private static void sample(Map<Thread, String> last, String compilers, long until) {
		for (int round = 0; Diagnostics.ON ? System.currentTimeMillis() < until : Diagnostics.active(); round++) {
			// A second's heartbeat, so the log bounds when the process went even while nothing moves,
			// and the compiler threads' ids, since the JVM starts more of them as the load grows.
			if (round % 10 == 0) {
				String now = Diagnostics.compilerThreads();
				if (!now.equals(compilers)) {
					compilers = now;
					Diagnostics.mark("compiler threads: " + now);
				} else {
					Diagnostics.mark("alive");
				}
			}
			for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
				Thread thread = entry.getKey();
				String name = thread.getName();
				if (!(name.equals("Render thread") || name.startsWith("Worker-Main") || name.startsWith("Netty")
						|| name.startsWith("IO-Worker") || name.startsWith("Sound") || name.startsWith("Download"))) {
					continue;
				}
				StackTraceElement[] stack = entry.getValue();
				if (stack.length == 0) continue;
				StringBuilder line = new StringBuilder(thread.getState().toString()).append(':');
				for (int i = 0; i < Math.min(FRAMES, stack.length); i++) {
					line.append(i == 0 ? " " : " < ").append(stack[i].getClassName())
						.append('.').append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber());
				}
				String now = line.toString();
				if (now.equals(last.get(thread))) continue;
				last.put(thread, now);
				Diagnostics.mark("sample " + name + " " + now);
			}
			try {
				Thread.sleep(EVERY_MILLIS);
			} catch (InterruptedException e) {
				return;
			}
		}
	}
}
