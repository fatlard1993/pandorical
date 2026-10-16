package justfatlard.pandorical.client.mixin;

import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(SectionOcclusionGraph.class)
public interface SectionOcclusionGraphAccessor {
	@Accessor("fullUpdateTask") Future<?> pandorical$fullUpdateTask();

	/** Holds a private record, so as Object. */
	@Accessor("currentGraph") AtomicReference<Object> pandorical$currentGraph();

	@Accessor("needsFrustumUpdate") AtomicBoolean pandorical$needsFrustumUpdate();
}
