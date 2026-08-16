package justfatlard.pandorical.client.content;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Client-side stand-in for server-only entity types registered with the
 * {@code "thrown_item"} renderer key. Extending ThrowableItemProjectile gives
 * the stub the exact synched-data layout of any server entity that extends it
 * (the base entity data plus the projectile's item stack), so the server's
 * item sync lands in the right slot and ThrownItemRenderer draws the real
 * item. The default item only matters for the frames before the first data
 * sync arrives; AIR draws nothing rather than something wrong.
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
