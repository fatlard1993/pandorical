package justfatlard.pandorical.mixin;

import justfatlard.pandorical.drops.DropsPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * XP orbs of any value clump into one, and a touch takes a whole orb. See DropsPolicy.
 *
 * <p>A clump is an ordinary orb whose value is the sum and whose count is 1, so a world saved with
 * clumps loads as vanilla orbs without Pandorical.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbClumpMixin {
	/** {@code Value} is saved as a short. */
	private static final int MOST_VALUE = Short.MAX_VALUE;

	@Shadow private int age;
	@Shadow private int count;

	@Shadow public abstract int getValue();
	@Shadow private void setValue(int value) {}
	@Shadow private int repairPlayerItems(ServerPlayer player, int amount) { return 0; }

	private static long pandorical$total(ExperienceOrb orb) {
		return (long) orb.getValue() * ((ExperienceOrbClumpMixin) (Object) orb).count;
	}

	private static boolean pandorical$clumping(ExperienceOrb orb) {
		return orb.level() instanceof ServerLevel level && DropsPolicy.clumping(level.getServer());
	}

	@Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z", at = @At("HEAD"), cancellable = true)
	private void pandorical$anyValue(ExperienceOrb other, CallbackInfoReturnable<Boolean> cir) {
		ExperienceOrb self = (ExperienceOrb) (Object) this;
		if (!pandorical$clumping(self)) return;
		cir.setReturnValue(other != self && !other.isRemoved() && pandorical$total(self) + pandorical$total(other) <= MOST_VALUE);
	}

	@Inject(method = "merge(Lnet/minecraft/world/entity/ExperienceOrb;)V", at = @At("HEAD"), cancellable = true)
	private void pandorical$clump(ExperienceOrb other, CallbackInfo ci) {
		ExperienceOrb self = (ExperienceOrb) (Object) this;
		if (!pandorical$clumping(self)) return;
		ci.cancel();
		long total = pandorical$total(self) + pandorical$total(other);
		if (total > MOST_VALUE) return;
		setValue((int) total);
		count = 1;
		age = Math.min(age, ((ExperienceOrbClumpMixin) (Object) other).age);
		other.discard();
	}

	@Inject(method = "tryMergeToExisting(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)Z", at = @At("HEAD"), cancellable = true)
	private static void pandorical$clumpNew(ServerLevel level, Vec3 pos, int value, CallbackInfoReturnable<Boolean> cir) {
		if (!DropsPolicy.clumping(level.getServer())) return;
		List<ExperienceOrb> near = level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class), AABB.ofSize(pos, 1, 1, 1),
			orb -> !orb.isRemoved() && pandorical$total(orb) + value <= MOST_VALUE);
		if (near.isEmpty()) {
			cir.setReturnValue(false);
			return;
		}
		ExperienceOrb orb = near.getFirst();
		ExperienceOrbClumpMixin into = (ExperienceOrbClumpMixin) (Object) orb;
		into.setValue((int) (pandorical$total(orb) + value));
		into.count = 1;
		into.age = 0;
		cir.setReturnValue(true);
	}

	@Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
	private void pandorical$takeAll(Player player, CallbackInfo ci) {
		ExperienceOrb self = (ExperienceOrb) (Object) this;
		if (!pandorical$clumping(self) || !(player instanceof ServerPlayer serverPlayer) || player.takeXpDelay != 0) return;
		long total = pandorical$total(self);
		if (total > Integer.MAX_VALUE) return;
		ci.cancel();
		player.takeXpDelay = 2;
		player.take(self, 1);
		int left = repairPlayerItems(serverPlayer, (int) total);
		if (left > 0) player.giveExperiencePoints(left);
		self.discard();
	}
}
