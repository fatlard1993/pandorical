package justfatlard.pandorical.client.entitykind;

import justfatlard.pandorical.client.entitymodel.ClientEntityModels;
import justfatlard.pandorical.client.entitymodel.EntityModelDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Draws an entity with its own body from its model file: the geometry, the skin, the animations.
 * Built per type, before the model's file has necessarily arrived with the server's assets, so the
 * model is built at the first entity drawn after it has; until then there is nothing to draw.
 *
 * <p>The game's own living renderer does the rest: the red flash when it is hurt, the topple when
 * it dies, its shadow, its name.
 */
public class BodyRenderer extends MobRenderer<Mob, BodyRenderState, BodyModel> {
    private @Nullable Identifier builtFrom;

    public BodyRenderer(EntityRendererProvider.Context context, EntityType<?> type) {
        super(context, new BodyModel(new ModelPart(java.util.List.of(), Map.of()), null), type.getWidth() * 0.5F);
    }

    @Override
    public BodyRenderState createRenderState() {
        return new BodyRenderState();
    }

    @Override
    public void extractRenderState(Mob entity, BodyRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.swing = entity.getSwingAnimation(partialTicks);
        state.hurt = entity.hurtTime > 0 && entity.hurtDuration > 0
            ? Math.clamp(1F - (entity.hurtTime - partialTicks) / entity.hurtDuration, 0F, 1F) : 0F;
        state.texture = null;
        ClientEntityKinds.Body body = ClientEntityKinds.bodyOf(entity.getType());
        if (body == null) return;
        EntityModelDefinition definition = ClientEntityModels.definition(body.model());
        if (definition == null || definition.geometry() == null) return;
        if (!body.model().equals(builtFrom)) {
            this.model = new BodyModel(definition.geometry().bakeRoot(), definition.motion());
            builtFrom = body.model();
        }
        state.texture = body.texture() != null ? body.texture() : definition.texture();
        state.scale *= body.drawScale();
        // A baby is drawn small by its model: there is one model, so by its age scale instead.
        if (state.isBaby) state.scale *= state.ageScale;
    }

    @Override
    public Identifier getTextureLocation(BodyRenderState state) {
        return state.texture != null ? state.texture : MissingTextureAtlasSprite.getLocation();
    }
}
