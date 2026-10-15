package justfatlard.pandorical.client.entitykind;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** {@link BodyMob} for an entity that grows up, so a baby is known as one and drawn small. */
public class AgeableBodyMob extends AgeableMob {
    public AgeableBodyMob(EntityType<? extends AgeableBodyMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        BodyData.defineRest(builder);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return BodyData.breathesWater(this.getType().getCategory());
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }
}
