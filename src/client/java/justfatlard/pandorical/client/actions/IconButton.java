package justfatlard.pandorical.client.actions;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A square button with an item drawn on it and its name on hover. */
final class IconButton extends Button {
	static final int SIZE = 24;

	private final ItemStack icon;

	IconButton(int x, int y, ItemStack icon, Component label, OnPress onPress) {
		super(x, y, SIZE, SIZE, label, onPress, DEFAULT_NARRATION);
		this.icon = icon;
		if (!label.getString().isEmpty()) setTooltip(Tooltip.create(label));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractDefaultSprite(graphics);
		graphics.item(icon, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
	}

	/** The item an icon id names; a barrier for one that no longer exists, so the gap is visible. */
	static ItemStack iconOf(String id) {
		Identifier parsed = Identifier.tryParse(id);
		Item item = parsed == null ? null : BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
		return new ItemStack(item == null || item == Items.AIR ? Items.BARRIER : item);
	}
}
