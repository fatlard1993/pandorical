package justfatlard.pandorical.client.renderer;

import org.joml.Matrix4f;

/** Carried by a display's render state: the turn its drawing goes through, or null. */
public interface DisplaySpinHolder {
	void pandorical$setSpin(Matrix4f spin);

	Matrix4f pandorical$getSpin();
}
