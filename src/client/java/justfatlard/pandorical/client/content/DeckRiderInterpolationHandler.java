package justfatlard.pandorical.client.content;

import justfatlard.pandorical.client.structure.StructureDecks;
import justfatlard.pandorical.client.structure.StructureManager;
import justfatlard.pandorical.client.structure.StructureManager.ClientStructure;
import justfatlard.pandorical.client.structure.StructureManager.StructurePoseSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.InterpolationTracker;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Another player standing on a walkable deck, drawn on the deck as this client draws it.
 *
 * <p>Where they say they stand was measured against the deck as their own client drew it, and
 * reaches here a round trip of theirs later, plus up to a tracker interval waiting on the server.
 * Both clients draw a deck by the same rule, so that delay is the whole gap: placed in the world
 * as sent, a rider falls behind the deck by its speed times their ping. So the position is taken
 * into the deck's frame at the pose drawn that long ago, blended there, and carried with the deck
 * between updates. Off a deck, or seated where the vehicle places them, the vanilla handler does
 * everything.
 */
public final class DeckRiderInterpolationHandler implements InterpolationHandler {
	private static final int TICKS = StructureManager.INTERPOLATION_TICKS;

	/** The server sends a player's position every other tick; half that, on average, is waiting. */
	private static final int RELAY_TICKS = 1;

	private final Entity entity;
	private final InterpolationHandler vanilla;

	private @Nullable ClientStructure deck;
	/** In the deck's frame. */
	private Vec3 from = Vec3.ZERO;
	private Vec3 to = Vec3.ZERO;
	private float fromYaw;
	private float toYaw;
	private float fromPitch;
	private float toPitch;
	private int ticks = TICKS;

	public DeckRiderInterpolationHandler(Entity entity, InterpolationHandler vanilla) {
		this.entity = entity;
		this.vanilla = vanilla;
	}

	@Override
	public InterpolationTracker interpolationTracker() {
		return vanilla.interpolationTracker();
	}

	@Override
	public @Nullable PositionAndRotation target() {
		if (deck == null) return vanilla.target();
		return PositionAndRotation.of(StructureDecks.toWorld(to, deck.thisTick()), toYaw, toPitch);
	}

	@Override
	public boolean interpolateTo(@Nullable PositionPath path, float yaw, float pitch, boolean withRotation) {
		ClientStructure aboard = null;
		int lag = 0;
		if (path != null && !entity.isPassenger()) {
			lag = lagTicks();
			aboard = StructureDecks.deckUnder(path.endPosition(), entity.getBbWidth() / 2, lag, deck);
		} else if (path == null) {
			aboard = deck;
		}

		if (aboard == null) {
			deck = null;
			ticks = TICKS;
			return vanilla.interpolateTo(path, yaw, pitch, withRotation);
		}
		if (deck == null) vanilla.cancel();

		StructurePoseSnapshot now = aboard.thisTick();
		StructurePoseSnapshot then = aboard.drawnAgo(lag);
		float turn = Mth.wrapDegrees(now.yaw() - then.yaw());

		from = StructureDecks.toLocal(entity.position(), now);
		fromYaw = entity.getYRot();
		fromPitch = entity.getXRot();
		if (path != null) to = StructureDecks.toLocal(path.endPosition(), then);
		toYaw = withRotation ? yaw + turn : fromYaw;
		toPitch = withRotation ? pitch : fromPitch;
		deck = aboard;
		ticks = 0;
		return true;
	}

	@Override
	public void interpolate() {
		if (deck == null) {
			vanilla.interpolate();
			return;
		}
		if (!deck.visible || !deck.walkable || !StructureManager.getActive().contains(deck)) {
			deck = null;
			ticks = TICKS;
			return;
		}

		boolean blending = ticks < TICKS;
		if (blending) ticks++;
		float t = ticks / (float) TICKS;
		entity.setPos(StructureDecks.toWorld(from.lerp(to, t), deck.thisTick()));
		if (blending) {
			entity.setYRot(Mth.rotLerp(t, fromYaw, toYaw));
			entity.setXRot(Mth.lerp(t, fromPitch, toPitch));
		}
	}

	@Override
	public void applyPredictedMovement(Vec3 movement) {
		if (deck == null) vanilla.applyPredictedMovement(movement);
	}

	@Override
	public boolean hasActiveInterpolation() {
		return deck != null ? ticks < TICKS : vanilla.hasActiveInterpolation();
	}

	@Override
	public void cancel() {
		deck = null;
		ticks = TICKS;
		vanilla.cancel();
	}

	/** How many ticks behind this client's drawn deck the rider's reported position is. */
	private int lagTicks() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(entity.getUUID());
		int ping = info == null ? 0 : Math.max(0, info.getLatency());
		return Math.round(ping / 50.0f) + RELAY_TICKS;
	}
}
