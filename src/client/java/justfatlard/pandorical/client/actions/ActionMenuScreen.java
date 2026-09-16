package justfatlard.pandorical.client.actions;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * An action menu, open: its buttons in a grid in the middle of the screen. A click does the
 * button's action, which puts the menu away - or, for a button that opens another menu, opens that
 * one over it. Escape goes back a menu; the key that opened the first one puts them all away.
 */
final class ActionMenuScreen extends Screen {
	private static final int GAP = 4;

	private final ActionMenus.Menu menu;
	/** The menu this one was opened from, to go back to; null for one opened by its key. */
	private final ActionMenuScreen from;

	ActionMenuScreen(ActionMenus.Menu menu, ActionMenuScreen from) {
		super(Component.literal(menu.name));
		this.menu = menu;
		this.from = from;
	}

	/** The key of the menu at the bottom of the stack: pressed again, it puts every one away. */
	private String openingKey() {
		return from != null ? from.openingKey() : menu.key;
	}

	/** As near square as the count allows, never wider than it needs. */
	static int columns(int count) {
		return Math.max(1, Math.min(count, (int) Math.ceil(Math.sqrt(count))));
	}

	@Override
	protected void init() {
		int count = menu.buttons.size();
		int cols = columns(count);
		int rows = (count + cols - 1) / cols;
		int step = IconButton.SIZE + GAP;
		int left = (width - (cols * step - GAP)) / 2;
		int top = (height - (rows * step - GAP)) / 2;
		for (int i = 0; i < count; i++) {
			ActionMenus.Entry entry = menu.buttons.get(i);
			addRenderableWidget(new IconButton(left + (i % cols) * step, top + (i / cols) * step,
				IconButton.iconOf(entry.icon), Component.literal(entry.label), b -> {
					if (ActionMenus.MENU.equals(entry.type)) {
						ActionMenus.Menu next = ActionMenus.menuById(entry.menu);
						if (next != null && !next.buttons.isEmpty()) minecraft.gui.setScreen(new ActionMenuScreen(next, this));
						return;
					}
					minecraft.gui.setScreen(null);
					ActionMenus.run(entry);
				}));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int rows = (menu.buttons.size() + columns(menu.buttons.size()) - 1) / columns(menu.buttons.size());
		int top = (height - (rows * (IconButton.SIZE + GAP) - GAP)) / 2;
		graphics.centeredText(font, title, width / 2, top - 14, 0xFFFFFFFF);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!openingKey().isEmpty() && InputConstants.getKey(event).getName().equals(openingKey())) {
			minecraft.gui.setScreen(null);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(from);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
