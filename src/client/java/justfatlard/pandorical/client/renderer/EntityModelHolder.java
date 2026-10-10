package justfatlard.pandorical.client.renderer;

import justfatlard.pandorical.client.entitymodel.ClientEntityModels;
import org.jspecify.annotations.Nullable;

/**
 * The server-assigned model a render state is drawn with. Outside the mixin package: Mixin rejects
 * non-mixin classes in a mixin package at class load.
 */
public interface EntityModelHolder {
	void pandorical$setEntityModel(ClientEntityModels.@Nullable Drawn drawn);

	ClientEntityModels.@Nullable Drawn pandorical$getEntityModel();
}
