package justfatlard.pandorical.client.mixin;

import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets the loom screen draw a preview for a bed.
 *
 * <p>A server mod may let a bed into the loom's banner slot (bed-banners does). The screen asks
 * that slot for its item and casts it to a banner to learn the flag's colour, and a bed is a
 * block item, so the cast threw and the client went down the moment a pattern was clicked.
 * Handing the screen the banner of the bed's colour answers the only question it asks. Here,
 * in the client layer, so no server mod needs a client jar to keep a vanilla screen standing.
 */
@Mixin(LoomScreen.class)
public abstract class LoomScreenBedMixin {
	@Redirect(method = "extractBackground",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;"))
	private Item pandorical$bannerOfBed(ItemStack stack) {
		Item item = stack.getItem();
		if (!(Block.byItem(item) instanceof BedBlock bed)) return item;
		// Paired by colour name through the registry, so the sixteen stay in step and a straw
		// bed, which has no colour, falls through to the item it was.
		Item banner = BuiltInRegistries.ITEM.getValue(
			Identifier.withDefaultNamespace(bed.getColor().getSerializedName() + "_banner"));
		return banner != null && banner != Items.AIR ? banner : item;
	}
}
