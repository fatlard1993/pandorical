package justfatlard.pandorical.client.entitykind;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * A registered entity with its own body, on a client without its mod: a mob that only holds what
 * the server syncs and is drawn from its model. Everything it does is the server's.
 */
public class BodyMob extends PathfinderMob {
    public BodyMob(EntityType<? extends BodyMob> type, Level level) {
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
}
