package justfatlard.pandorical.client.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A key press from an action menu button counts as one click of the key it names. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int pandorical$getClickCount();

	@Accessor("clickCount")
	void pandorical$setClickCount(int count);
}
