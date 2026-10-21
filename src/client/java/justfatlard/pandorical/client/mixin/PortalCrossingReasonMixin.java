package justfatlard.pandorical.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import justfatlard.pandorical.client.portal.Crossing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A trip through an immersive portal into any world is drawn as the swirl, not as a menu. */
@Mixin(ClientPacketListener.class)
public abstract class PortalCrossingReasonMixin {
	@ModifyReturnValue(method = "determineLevelLoadingReason", at = @At("RETURN"))
	private LevelLoadingScreen.Reason pandorical$throughTheSwirl(LevelLoadingScreen.Reason vanilla, boolean playerDied) {
		if (playerDied || vanilla == LevelLoadingScreen.Reason.END_PORTAL || !Crossing.active()) return vanilla;
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.portalEffectIntensity > 0.0F ? LevelLoadingScreen.Reason.NETHER_PORTAL : vanilla;
	}
}
