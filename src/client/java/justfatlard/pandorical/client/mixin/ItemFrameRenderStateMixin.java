package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.maprelief.ClientMapReliefs;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Rewritten on every extraction by {@link ItemFrameReliefMixin}, so a reused state carries no stale relief. */
@Mixin(ItemFrameRenderState.class)
public class ItemFrameRenderStateMixin implements ClientMapReliefs.Holder {
    @Unique
    private ClientMapReliefs.Drawn pandorical$relief;

    @Override
    public ClientMapReliefs.Drawn pandorical$relief() {
        return pandorical$relief;
    }

    @Override
    public void pandorical$setRelief(ClientMapReliefs.Drawn relief) {
        this.pandorical$relief = relief;
    }
}
