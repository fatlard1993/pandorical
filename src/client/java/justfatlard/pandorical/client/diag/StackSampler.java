package justfatlard.pandorical.client.diag;

import java.util.Map;
import justfatlard.pandorical.Diagnostics;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logs each changed stack of the game's working threads while {@link Diagnostics#active}: a
 * native crash leaves no record but what was written before it.
 */
public final class StackSampler {
	private StackSampler() {}

	private static final long EVERY_MILLIS = 100;
	private static final long FOR_MILLIS = 600_000;
	private static final int FRAMES = 14;

	private static final AtomicBoolean running = new AtomicBoolean();

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
			// The guard may have been raised again while this run was stopping.
			if (Diagnostics.active()) start();
		}
	}

	private static void sample(Map<Thread, String> last, String compilers, long until) {
		for (int round = 0; Diagnostics.ON ? System.currentTimeMillis() < until : Diagnostics.active(); round++) {
			// A heartbeat bounds when the process died; the JVM adds compiler threads under load.
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
