package justfatlard.pandorical.client.content;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.SteppedInterpolationHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Minimal client-side stand-in for server-only entity types registered with
 * the {@code "invisible"} renderer key (e.g. big-boats' ship anchor). It
 * carries only the base entity synched data, which matches any server entity
 * that extends Entity without defining extra tracked data; it exists so the
 * client has a real entity to position, track, and ride, while NoopRenderer
 * draws nothing.
 */
@Environment(EnvType.CLIENT)
public class StubEntity extends Entity {
	public StubEntity(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	/**
	 * Vanilla's stepped position lerp instead of the base Entity's snap:
	 * server position updates arrive at tracking cadence, and a rider (e.g.
	 * on big-boats' ship anchor) must glide with them, not jitter against
	 * the smoothly interpolated structure rendering.
	 */
	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return SteppedInterpolationHandler.create(this);
	}

	/**
	 * Riders sit exactly AT the anchor entity's position: the stub cannot
	 * know the server entity's real dimensions or seat layout, so the
	 * contract is that the server-side mod places its anchor at seat height.
	 * The default (bounding-box-derived) attachment would perch riders on
	 * top of the stub's arbitrary box instead.
	 */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return Vec3.ZERO;
	}
}
