package justfatlard.pandorical.gametest;

import justfatlard.pandorical.client.entitykind.BodyRenderState;
import justfatlard.pandorical.client.entitykind.BodyRenderer;
import justfatlard.pandorical.client.entitymodel.ClientEntityModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.model.Model;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * For a mod's tests: what the client was told to draw an entity with, and a model posed as the
 * game would pose it for drawing, entity models included. In the test kit because a mod's tests
 * compile against the kit and not against Pandorical's client.
 */
public final class Models {
	private Models() {}

	/** The entity model this client has for an entity, by its network id, or null. */
	public static @Nullable Identifier assigned(int entityId) {
		return ClientEntityModels.assigned(entityId);
	}

	/**
	 * The model an entity with its own body is drawn with, posed as it would be moving: {@code speed}
	 * as the game's walk speed (nought to one), {@code distance} as how far along its stride it is.
	 * Null if the entity is not drawn from its own body, or its model has not been built yet.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static @Nullable ModelPart posedBody(Entity entity, float speed, float distance) {
		EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
		if (!(renderer instanceof BodyRenderer body)) return null;
		BodyRenderState state = (BodyRenderState) renderer.createRenderState(entity, 1F);
		if (body.getModel().root().getAllParts().size() <= 1) return null;
		state.walkAnimationSpeed = speed;
		state.walkAnimationPos = distance;
		pose(body.getModel(), state);
		return body.getModel().root();
	}

	/** Pose a model for a render state as it is posed to be drawn: its own animation, then any part transforms. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void pose(Model model, Object state) {
		model.setupAnim(state);
		ClientEntityModels.afterPose(model, state);
	}
}
