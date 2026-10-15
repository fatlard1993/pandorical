package justfatlard.pandorical.gametest;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A mob with its own body and values of its own to sync, which no vanilla class has: what a client
 * without this mod has to build from nothing.
 */
public class Blob extends AgeableMob {
	public static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(Blob.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Boolean> GLOWING = SynchedEntityData.defineId(Blob.class, EntityDataSerializers.BOOLEAN);

	public Blob(EntityType<? extends Blob> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MOOD, 7);
		builder.define(GLOWING, false);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}
}
