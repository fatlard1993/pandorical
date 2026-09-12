package justfatlard.pandorical.client.content;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * For the {@code "thrown_item"} renderer key. Extending ThrowableItemProjectile gives it the
 * synched-data layout of a server entity that does, so the item sync lands in the right slot.
 */
@Environment(EnvType.CLIENT)
public class StubThrownItemEntity extends ThrowableItemProjectile {
	public StubThrownItemEntity(EntityType<? extends ThrowableItemProjectile> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.AIR;
	}
}
