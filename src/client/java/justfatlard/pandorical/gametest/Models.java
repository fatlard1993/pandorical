package justfatlard.pandorical.gametest;

import justfatlard.pandorical.client.entitymodel.ClientEntityModels;
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

	/** Pose a model for a render state as it is posed to be drawn: its own animation, then any part transforms. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void pose(Model model, Object state) {
		model.setupAnim(state);
		ClientEntityModels.afterPose(model, state);
	}
}
