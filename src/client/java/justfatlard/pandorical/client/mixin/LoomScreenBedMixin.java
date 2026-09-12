package justfatlard.pandorical.client.mixin;

import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A server mod may allow a bed in the loom's banner slot. The screen casts that slot's item to a
 * banner for its colour, which throws for a bed, so it is handed the banner of the bed's colour.
 */
@Mixin(LoomScreen.class)
public abstract class LoomScreenBedMixin {
	@Redirect(method = "extractBackground",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;"))
	private Item pandorical$bannerOfBed(ItemStack stack) {
		Item item = stack.getItem();
		if (!(Block.byItem(item) instanceof BedBlock bed)) return item;
		Item banner = BuiltInRegistries.ITEM.getValue(
			Identifier.withDefaultNamespace(bed.getColor().getSerializedName() + "_banner"));
		return banner != null && banner != Items.AIR ? banner : item;
	}
}
