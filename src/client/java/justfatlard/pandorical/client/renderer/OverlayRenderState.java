package justfatlard.pandorical.client.renderer;

import net.minecraft.resources.Identifier;

/**
 * A render state that carries the texture a server asked for in place of the entity's own.
 *
 * <p>The renderer only ever sees the state, not the entity, by the time it picks a texture;
 * the entity is in hand one step earlier, when the state is extracted. The overlay is looked
 * up there and rides along on the state.
 */
public interface OverlayRenderState {
	Identifier pandorical$overlay();

	void pandorical$setOverlay(Identifier texture);
}
