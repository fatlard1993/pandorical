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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * For the {@code "invisible"} renderer key. Its synched data is the base Entity's only, which
 * matches a server entity that defines no tracked data of its own.
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

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return new StructureInterpolationHandler(this);
	}

	/**
	 * Riders sit at the stub's position: its box is arbitrary, so the server-side mod places its
	 * anchor at seat height.
	 */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return Vec3.ZERO;
	}
}
