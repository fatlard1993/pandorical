package justfatlard.pandorical.shape;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;

/**
 * Whether a block face reaches a fixed support shape, worked out once per face shape. Vanilla asks
 * this for every state, face and support type as it builds each state's cache, and a face shape is
 * one object shared by every state with that shape, so most of the asking repeats itself.
 *
 * <p>Keyed by identity and held weakly: a shape no block uses any more is not kept.
 */
public final class SupportMemo {
	private static final Map<VoxelShape, Cache<VoxelShape, Boolean>> BY_SUPPORT = new ConcurrentHashMap<>();

	private SupportMemo() {}

	/** {@code reaches} decides it when this face has not been asked about against this support before. */
	public static boolean remembered(VoxelShape face, VoxelShape support, BooleanSupplier reaches) {
		Cache<VoxelShape, Boolean> answers = BY_SUPPORT.computeIfAbsent(support,
			unused -> CacheBuilder.newBuilder().weakKeys().build());
		try {
			return answers.get(face, reaches::getAsBoolean);
		} catch (ExecutionException e) {
			throw new IllegalStateException(e.getCause());
		}
	}
}
