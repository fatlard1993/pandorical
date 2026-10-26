package justfatlard.pandorical.client.entitykind;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class BodyRenderState extends LivingEntityRenderState {
    /** How far through a strike, nought to one; nought when it is not striking. */
    public float swing;
    /** How far through flinching from a hit, nought to one; nought when it has not been hit. */
    public float hurt;
    public @Nullable Identifier texture;
}
