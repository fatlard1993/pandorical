package justfatlard.pandorical.client.mixin;

import justfatlard.pandorical.client.structure.StructureDecks;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A walkable deck carries the local player; see {@link StructureDecks}. */
@Mixin(LocalPlayer.class)
public abstract class StructureDeckCarryMixin {

	@Inject(method = "tick", at = @At("HEAD"))
	private void pandorical$rideDeck(CallbackInfo ci) {
		StructureDecks.carry((LocalPlayer) (Object) this);
	}
}
