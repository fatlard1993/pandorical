package justfatlard.pandorical.mixin;

import justfatlard.pandorical.rules.WorldRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * XP from a mob nobody killed: vanilla pays only for a death a player had a recent hand in, so a fall,
 * lava, a golem or a trapdoor pays nothing. This pays a share for those, on vanilla's other terms: the
 * mob has to be one that drops, and the game rule has to be on. Deaths vanilla pays for are left to it.
 */
@Mixin(LivingEntity.class)
public abstract class UnattendedXpMixin {
	@Shadow protected int lastHurtByPlayerMemoryTime;

	@Shadow protected abstract boolean isAlwaysExperienceDropper();

	@Shadow public abstract boolean shouldDropExperience();

	@Shadow public abstract boolean wasExperienceConsumed();

	@Shadow public abstract int getExperienceReward(ServerLevel level, Entity killer);

	@Inject(method = "dropExperience", at = @At("HEAD"))
	private void pandorical$unattendedXp(ServerLevel level, Entity killer, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof Mob) || !WorldRules.unattendedKillXp(level.getServer())) return;
		if (wasExperienceConsumed() || isAlwaysExperienceDropper() || lastHurtByPlayerMemoryTime > 0) return;
		if (!shouldDropExperience() || !level.getGameRules().get(GameRules.MOB_DROPS)) return;
		double share = getExperienceReward(level, killer) * WorldRules.unattendedPercent(level.getServer()) / 100.0;
		int xp = (int) Math.floor(share);
		if (level.getRandom().nextDouble() < share - xp) xp++;
		if (xp > 0) ExperienceOrb.award(level, self.position(), xp);
	}
}
