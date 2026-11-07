package justfatlard.pandorical.point;

import justfatlard.pandorical.api.KeybindApi;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.SettingsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Pointing at something: a key (or {@code /point}) and whatever the player is looking at, a block
 * or a mob, is outlined for everybody nearby in the player's colour for a few seconds, with a line
 * from them to it.
 *
 * <p>All of it is particles the game already draws, so a vanilla client sees it as well as any.
 * The server traces the look itself; a press is all a client sends. The colour is the player's
 * own as vanilla gives it: their team's colour, else the colour their name is shown in, else white.
 */
public final class Pointing {
	private Pointing() {}

	public static final String KEYBIND = "pandorical:point";
	public static final double RANGE = 64;
	/** How long a point stays drawn, and how often it is drawn again: a moving mob is followed. */
	static final int SHOWN_TICKS = 60;
	private static final int EVERY = 10;
	/** The line to the target, drawn only at first: the outline is what lingers. */
	private static final int LINE_TICKS = 20;
	private static final int COOLDOWN_TICKS = 20;
	private static final float MOTE = 0.7F;
	/** Motes along an edge are this far apart, wider for a big target so no outline is a flood. */
	private static final double STEP = 0.3;
	private static final int MOST_PER_EDGE = 24;

	private static SettingsApi.Setting<Boolean> enabled;

	private record Point(UUID player, ServerLevel level, BlockPos block, AABB shape, Entity entity, int color, long until) {}

	private static final Map<UUID, Point> points = new HashMap<>();
	private static final Map<UUID, Long> lastPoint = new HashMap<>();

	public static void register(SettingsApi.Group settings) {
		enabled = settings.toggle("pointing", "Players can point at things for everyone to see", true)
			.describe("A key, or /point, outlines what a player looks at in their colour for a few seconds");
		PandoricalApi.keybinds().register(KEYBIND, KeybindApi.letter('Z'), "Point at something", Pointing::point);
		PandoricalApi.keybinds().bindByDefault(KEYBIND);
		PandoricalApi.commandHelp().describe("/point", "Outline what you are looking at, in your colour, for everyone nearby.");
	}

	/** Point at what this player looks at; false, with a word to them, if there is nothing to point at or they just did. */
	public static boolean point(ServerPlayer player) {
		if (enabled != null && !enabled.get(player)) {
			player.sendOverlayMessage(Component.translatableWithFallback("pandorical.point.off", "Pointing is turned off on this server."));
			return false;
		}
		if (player.isSpectator()) return false;
		ServerLevel level = player.level();
		long now = level.getServer().getTickCount();
		Long last = lastPoint.get(player.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS) return false;

		HitResult hit = trace(player);
		if (hit == null) {
			player.sendOverlayMessage(Component.translatableWithFallback("pandorical.point.nothing", "Nothing there to point at."));
			return false;
		}
		lastPoint.put(player.getUUID(), now);
		int color = colorOf(player);
		Point point;
		if (hit instanceof EntityHitResult entityHit) {
			point = new Point(player.getUUID(), level, null, null, entityHit.getEntity(), color, now + SHOWN_TICKS);
		} else {
			BlockPos pos = ((BlockHitResult) hit).getBlockPos();
			VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
			AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
			point = new Point(player.getUUID(), level, pos.immutable(), box, null, color, now + SHOWN_TICKS);
		}
		points.put(player.getUUID(), point);
		Vec3 at = hit.getLocation();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
		draw(point, player, true);
		return true;
	}

	/** What the player is looking at, a mob before the block behind it, out to {@link #RANGE}; null for nothing. */
	public static HitResult trace(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 reach = eye.add(player.getViewVector(1.0F).scale(RANGE));
		BlockHitResult block = player.level().clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		Vec3 end = block.getType() == HitResult.Type.MISS ? reach : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end, new AABB(eye, end).inflate(1.0),
			other -> other != player && !other.isSpectator() && other.isPickable(), eye.distanceToSqr(end));
		if (entity != null) return entity;
		return block.getType() == HitResult.Type.MISS ? null : block;
	}

	/** The player's own colour, as vanilla shows it: their team's, else their name's, else white. */
	public static int colorOf(ServerPlayer player) {
		if (player.getTeam() != null && player.getTeam().getColor().isPresent()) return player.getTeamColor();
		TextColor named = player.getDisplayName().getStyle().getColor();
		return named != null ? named.getValue() : 0xFFFFFF;
	}

	/** Every few ticks, each point drawn again where its target now is; gone when its time is up or its target is. */
	public static void tick(MinecraftServer server) {
		if (points.isEmpty()) return;
		long now = server.getTickCount();
		for (Iterator<Point> it = points.values().iterator(); it.hasNext(); ) {
			Point point = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(point.player());
			if (now >= point.until() || player == null || player.level() != point.level()
					|| point.entity() != null && point.entity().isRemoved()) {
				it.remove();
				continue;
			}
			if ((point.until() - now) % EVERY == 0) draw(point, player, point.until() - now > SHOWN_TICKS - LINE_TICKS);
		}
	}

	public static boolean pointing(UUID player) {
		return points.containsKey(player);
	}

	public static void forget(UUID player) {
		points.remove(player);
		lastPoint.remove(player);
	}

	private static void draw(Point point, ServerPlayer player, boolean line) {
		DustParticleOptions mote = new DustParticleOptions(point.color(), MOTE);
		AABB box = point.entity() != null ? point.entity().getBoundingBox() : point.shape();
		box = box.inflate(0.02);
		double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
		for (double y : ys) for (double z : zs) edge(point.level(), mote, new Vec3(box.minX, y, z), new Vec3(box.maxX, y, z));
		for (double x : xs) for (double z : zs) edge(point.level(), mote, new Vec3(x, box.minY, z), new Vec3(x, box.maxY, z));
		for (double x : xs) for (double y : ys) edge(point.level(), mote, new Vec3(x, y, box.minZ), new Vec3(x, y, box.maxZ));
		if (line) {
			Vec3 from = player.getEyePosition().subtract(0, 0.4, 0);
			Vec3 to = box.getCenter();
			int motes = (int) Math.min(48, from.distanceTo(to) / 0.8);
			for (int i = 1; i < motes; i++) {
				Vec3 at = from.lerp(to, i / (double) motes);
				point.level().sendParticles(mote, true, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
		}
	}

	private static void edge(ServerLevel level, DustParticleOptions mote, Vec3 from, Vec3 to) {
		int motes = (int) Math.max(2, Math.min(MOST_PER_EDGE, Math.ceil(from.distanceTo(to) / STEP)));
		for (int i = 0; i <= motes; i++) {
			Vec3 at = from.lerp(to, i / (double) motes);
			level.sendParticles(mote, true, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
	}
}
