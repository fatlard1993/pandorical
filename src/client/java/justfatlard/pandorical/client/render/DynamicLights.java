package justfatlard.pandorical.client.render;

import justfatlard.pandorical.client.settings.ClientPrefs;
import justfatlard.pandorical.client.settings.ClientSettings;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * A torch in hand, a glowing item on the ground or a burning mob lights the blocks around it as it
 * moves. Light is added where vanilla and Sodium both ask for it ({@code LightCoordsUtil.getLightCoords}
 * for blocks, {@code EntityRenderer.getBlockLightLevel} for entities), and the chunk sections a light
 * moves through are rebuilt through {@code ClientLevel.setSectionRangeDirty}, which Sodium also hears.
 *
 * <p>Chunk sections are built on worker threads, so the sources they read are one immutable snapshot,
 * replaced whole each tick.
 */
public final class DynamicLights {
	private DynamicLights() {}

	private static final String KEY = "dynamic_lights";
	/** How far from the camera an entity can be and still light anything. */
	private static final double REACH = 64.0;

	private static Boolean wanted;

	/** Light positions and levels, read by chunk builders without a lock. */
	private record Snapshot(double[] x, double[] y, double[] z, int[] level, int count) {
		static final Snapshot EMPTY = new Snapshot(new double[0], new double[0], new double[0], new int[0], 0);
	}

	private static volatile Snapshot snapshot = Snapshot.EMPTY;

	/** Where each lit entity was last drawn from, so a move rebuilds the sections it left and entered. */
	private record Lit(int blockX, int blockY, int blockZ, int level) {}

	private static final Int2ObjectMap<Lit> lastLit = new Int2ObjectOpenHashMap<>();

	public static void register() {
		ClientSettings.INSTANCE.group("pandorical", "Pandorical")
			.toggle(KEY, "Dynamic lights",
				lambDynamicLightsInstalled()
					? "Left to LambDynamicLights, which is installed"
					: "A torch in hand, a glowing item on the ground or a burning mob lights what is around it",
				DynamicLights::wanted, DynamicLights::want);
		ClientTickEvents.END_CLIENT_TICK.register(DynamicLights::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	public static boolean active() {
		return wanted() && !lambDynamicLightsInstalled();
	}

	/** The block light the nearest dynamic light gives this position, 0 when none reaches it. */
	public static int levelAt(BlockPos pos) {
		Snapshot lights = snapshot;
		if (lights.count == 0) return 0;

		double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
		int best = 0;
		for (int i = 0; i < lights.count; i++) {
			int level = lights.level[i];
			if (level <= best) continue;
			double dx = lights.x[i] - cx, dy = lights.y[i] - cy, dz = lights.z[i] - cz;
			double distanceSq = dx * dx + dy * dy + dz * dz;
			if (distanceSq >= level * level) continue;
			int here = (int) (level - Math.sqrt(distanceSq));
			if (here > best) best = here;
		}
		return Math.min(best, 15);
	}

	/** How brightly an entity lights its surroundings, 0 for not at all. */
	static int levelOf(Entity entity) {
		int level = entity.isOnFire() ? 15 : 0;
		if (entity instanceof LivingEntity living) {
			level = Math.max(level, levelOf(living.getMainHandItem()));
			level = Math.max(level, levelOf(living.getOffhandItem()));
		} else if (entity instanceof ItemEntity item) {
			level = Math.max(level, levelOf(item.getItem()));
		}
		return level;
	}

	static int levelOf(ItemStack stack) {
		if (stack.isEmpty()) return 0;
		if (stack.is(Items.LAVA_BUCKET)) return 15;
		if (stack.getItem() instanceof BlockItem block) return block.getBlock().defaultBlockState().getLightEmission();
		return 0;
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || client.player == null || !active()) {
			if (!lastLit.isEmpty() && level != null) dirtyAll(level);
			clear();
			return;
		}

		double reachSq = REACH * REACH;
		double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();

		int capacity = Math.max(16, snapshot.count * 2);
		double[] xs = new double[capacity], ys = new double[capacity], zs = new double[capacity];
		int[] levels = new int[capacity];
		int count = 0;
		Int2ObjectMap<Lit> seen = new Int2ObjectOpenHashMap<>();

		for (Entity entity : level.entitiesForRendering()) {
			if (entity.distanceToSqr(px, py, pz) > reachSq) continue;
			int light = levelOf(entity);
			if (light <= 0) continue;

			AABB box = entity.getBoundingBox();
			double x = (box.minX + box.maxX) / 2, y = (box.minY + box.maxY) / 2, z = (box.minZ + box.maxZ) / 2;
			if (count == xs.length) {
				int grown = xs.length * 2;
				xs = java.util.Arrays.copyOf(xs, grown);
				ys = java.util.Arrays.copyOf(ys, grown);
				zs = java.util.Arrays.copyOf(zs, grown);
				levels = java.util.Arrays.copyOf(levels, grown);
			}
			xs[count] = x;
			ys[count] = y;
			zs[count] = z;
			levels[count] = light;
			count++;

			Lit now = new Lit(Mth.floor(x), Mth.floor(y), Mth.floor(z), light);
			seen.put(entity.getId(), now);
			Lit before = lastLit.get(entity.getId());
			if (!now.equals(before)) {
				if (before != null) dirtyAround(level, before);
				dirtyAround(level, now);
			}
		}

		for (Int2ObjectMap.Entry<Lit> gone : lastLit.int2ObjectEntrySet()) {
			if (!seen.containsKey(gone.getIntKey())) dirtyAround(level, gone.getValue());
		}

		lastLit.clear();
		lastLit.putAll(seen);
		snapshot = count == 0 ? Snapshot.EMPTY : new Snapshot(xs, ys, zs, levels, count);
	}

	private static void dirtyAround(ClientLevel level, Lit lit) {
		int r = lit.level;
		level.setSectionRangeDirty(
			SectionPos.blockToSectionCoord(lit.blockX - r), SectionPos.blockToSectionCoord(lit.blockY - r),
			SectionPos.blockToSectionCoord(lit.blockZ - r), SectionPos.blockToSectionCoord(lit.blockX + r),
			SectionPos.blockToSectionCoord(lit.blockY + r), SectionPos.blockToSectionCoord(lit.blockZ + r));
	}

	private static void dirtyAll(ClientLevel level) {
		for (Lit lit : lastLit.values()) dirtyAround(level, lit);
	}

	private static void clear() {
		lastLit.clear();
		snapshot = Snapshot.EMPTY;
	}

	private static boolean wanted() {
		if (wanted == null) wanted = ClientPrefs.getBoolean(KEY, true);
		return wanted;
	}

	private static void want(boolean on) {
		wanted = on;
		ClientPrefs.set(KEY, on);
	}

	private static boolean lambDynamicLightsInstalled() {
		return FabricLoader.getInstance().isModLoaded("lambdynlights");
	}
}
