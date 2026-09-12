package justfatlard.pandorical;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Leaves out the mixins named in {@code -Dpandorical.skipMixins}, for finding which one a machine
 * will not run.
 *
 * <p>For a crash that happens on one player's computer and nowhere else, where the only way to
 * learn anything is for them to try again: a list of simple class names, or {@code all}. Accessors
 * and invokers are never left out by {@code all}, since code calls them directly and taking one
 * away is an ordinary Java error rather than a clue; name one to leave it out anyway.
 *
 * <p>Nothing happens without the property, which nobody sets by accident.
 */
public final class DiagnosticMixinPlugin implements IMixinConfigPlugin {
	private static final Set<String> SKIP = Arrays.stream(System.getProperty("pandorical.skipMixins", "").split(","))
		.map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (SKIP.isEmpty()) return true;
		String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
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
