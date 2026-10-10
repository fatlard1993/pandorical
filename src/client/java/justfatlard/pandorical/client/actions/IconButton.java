package justfatlard.pandorical.client.actions;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** A square button with an item or a sprite drawn on it and its name on hover. */
final class IconButton extends Button {
	static final int SIZE = 24;

	/** An icon id with this in front names a GUI sprite rather than an item: a mod's own, sent with its assets. */
	static final String SPRITE = "sprite:";

	/** What a button wears: an item, or a GUI sprite. */
	record Icon(@Nullable ItemStack item, @Nullable Identifier sprite) {
		static Icon of(ItemStack item) {
			return new Icon(item, null);
		}
	}

	private final Icon icon;

	IconButton(int x, int y, Icon icon, Component label, OnPress onPress) {
		super(x, y, SIZE, SIZE, label, onPress, DEFAULT_NARRATION);
		this.icon = icon;
		if (!label.getString().isEmpty()) setTooltip(Tooltip.create(label));
	}

	IconButton(int x, int y, ItemStack icon, Component label, OnPress onPress) {
		this(x, y, Icon.of(icon), label, onPress);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractDefaultSprite(graphics);
		int x = getX() + (width - 16) / 2;
		int y = getY() + (height - 16) / 2;
		if (icon.sprite() != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon.sprite(), x, y, 16, 16);
		else graphics.item(icon.item(), x, y);
	}

	/** What an icon id names; a barrier for an item that no longer exists, so the gap is visible. */
	static Icon iconOf(String id) {
		if (id != null && id.startsWith(SPRITE)) {
			Identifier sprite = Identifier.tryParse(id.substring(SPRITE.length()));
			if (sprite != null) return new Icon(null, sprite);
		}
		Identifier parsed = id == null ? null : Identifier.tryParse(id);
		Item item = parsed == null ? null : BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
		return Icon.of(new ItemStack(item == null || item == Items.AIR ? Items.BARRIER : item));
	}
}
