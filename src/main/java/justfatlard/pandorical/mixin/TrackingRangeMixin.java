package justfatlard.pandorical.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import justfatlard.pandorical.drops.DropsPolicy;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Dropped items and XP orbs are sent to players from closer in. See DropsPolicy. The range is
 * fixed as an entity starts being tracked, so a change reaches only those tracked after it.
 */
@Mixin(ChunkMap.class)
public abstract class TrackingRangeMixin {

	@ModifyVariable(method = "addEntity", at = @At("STORE"), ordinal = 0)
	private int pandorical$closerDrops(int range, @Local(argsOnly = true) Entity entity) {
		if (!(entity instanceof ItemEntity || entity instanceof ExperienceOrb) || !(entity.level() instanceof ServerLevel level)) return range;
		int blocks = DropsPolicy.trackingRange(level.getServer());
		if (blocks <= 0) return range;
		return Math.min(range, blocks);
	}
}
