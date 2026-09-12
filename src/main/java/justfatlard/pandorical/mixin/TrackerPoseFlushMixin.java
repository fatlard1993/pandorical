package justfatlard.pandorical.mixin;

import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Structure poses go out with the entity positions.
 *
 * <p>{@code ChunkMap.tick()} is where the server tells every client where each tracked entity
 * is, once per tick, before any entity has moved this tick. A structure's pose is held until
 * this moment and sent from it, so the deck and the entities riding it describe the same tick
 * and arrive in the same breath.
 */
@Mixin(ChunkMap.class)
public abstract class TrackerPoseFlushMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void pandorical$flushStructurePoses(CallbackInfo ci) {
        PandoricalApi.structuresImpl().flushPoses(level);
    }
}
