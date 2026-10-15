package justfatlard.pandorical.client.entitykind;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class BodyRenderState extends LivingEntityRenderState {
    /** How far through a strike, nought to one; nought when it is not striking. */
    public float swing;
    public @Nullable Identifier texture;
}
