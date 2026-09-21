package justfatlard.pandorical.client.mixin;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The death screen's one-second delay, counted in wall clock as well as in ticks.
 *
 * <p>Vanilla arms its buttons from {@code tick()}, on the twentieth one. A client that has stopped
 * running game ticks still draws the screen and still takes clicks, so the player sits in front of
 * a death screen whose buttons are greyed out and stay that way for as long as the stall lasts -
 * with no way out, because the screen also refuses escape. Two players here have sat through
 * minutes of it.
 *
 * <p>Rendering keeps running when ticking does not, so the delay is armed from there too.
 */
@Mixin(DeathScreen.class)
public abstract class DeathScreenDelayMixin {

	/** The count vanilla arms its buttons on. Standing the counter here is spending that moment. */
	private static final int ARMED = 20;

	/** Vanilla's own counter, compared against {@link #ARMED} once and only once. */
	@Shadow
	private int delayTicker;

	@Shadow
	@Final
	private List<Button> exitButtons;

	@Unique
	private long pandorical$shownAt;

	@Inject(method = "init", at = @At("TAIL"))
	private void pandorical$startTheClock(CallbackInfo info) {
		this.pandorical$shownAt = System.currentTimeMillis();
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void pandorical$armWhenTheSecondIsUp(
		GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
		if (this.delayTicker >= ARMED) return;
		if (System.currentTimeMillis() - this.pandorical$shownAt < 1000L) return;

		// Vanilla increments before it compares, so leaving the counter here means its own test
		// can never come along later and re-enable a button the player has already spent, which
		// would offer a second press that does nothing.
		this.delayTicker = ARMED;
		for (Button button : this.exitButtons) button.active = true;
	}
}
