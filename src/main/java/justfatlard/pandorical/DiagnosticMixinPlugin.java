package justfatlard.pandorical;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Leaves out the mixins named in {@code -Dpandorical.skipMixins}, for finding which one a machine
 * will not run: simple class names, or {@code all}. {@code all} spares accessors and invokers,
 * which code calls directly; name one to leave it out anyway.
 */
public final class DiagnosticMixinPlugin implements IMixinConfigPlugin {
	private static final Set<String> SKIP = Arrays.stream(System.getProperty("pandorical.skipMixins", "").split(","))
		.map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());

	private static final Set<String> MATCHED = ConcurrentHashMap.newKeySet();

	/** Call from a mod initializer: mixin configs are prepared before any runs. */
	public static void reportUnmatched() {
		for (String name : SKIP) {
			if (!name.equals("all") && !MATCHED.contains(name)) {
				System.out.println("[pandorical] diagnostic: pandorical.skipMixins names " + name + ", which is no mixin of Pandorical's");
			}
		}
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (SKIP.isEmpty()) return true;
		String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		if (SKIP.contains(simple)) MATCHED.add(simple);
		boolean skip = SKIP.contains(simple)
			|| (SKIP.contains("all") && !simple.endsWith("Accessor") && !simple.endsWith("Invoker"));
		if (skip) System.out.println("[pandorical] diagnostic: leaving out mixin " + simple);
		return !skip;
	}

	@Override public void onLoad(String mixinPackage) {}
	@Override public String getRefMapperConfig() { return null; }
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
	@Override public List<String> getMixins() { return null; }
	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		Diagnostics.mark("applying " + mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1) + " to " + targetClassName);
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		Diagnostics.mark("applied " + mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
	}
}
