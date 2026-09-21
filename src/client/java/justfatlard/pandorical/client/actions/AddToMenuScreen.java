package justfatlard.pandorical.client.actions;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Which of your menus a command found in the mods screen should go on.
 *
 * <p>The moment somebody reads what a command does is the moment they want it to hand, and until
 * now that meant remembering it, leaving the screen, opening the editor, making a button and
 * typing the command back in. This is the same errand as one click and one choice.
 *
 * <p>Only the player's own menus are offered. The server's are rebuilt every join, so a button
 * added to one would last until the next login and no longer.
 */
final class AddToMenuScreen extends Screen {
	private static final int ROW = 22;
	private static final int PER_PAGE = 8;

	private final Screen parent;
	private final ActionMenus.Entry button;
	private int page;

	AddToMenuScreen(Screen parent, ActionMenus.Entry button) {
		super(Component.literal("Add to which menu?"));
		this.parent = parent;
		this.button = button;
	}

	@Override
	protected void init() {
		// Paged rather than cut off at the bottom of the window. Menus past the fold used to be
		// unreachable with nothing to say so, and the "New menu" button sat right there, so the
		// obvious move was making a second copy of a menu you already had.
		List<ActionMenus.Menu> mine = ActionMenus.mine();
		int pages = Math.max(1, (mine.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.clamp(page, 0, pages - 1);

		int y = 52;
		for (int i = page * PER_PAGE; i < Math.min(mine.size(), (page + 1) * PER_PAGE); i++) {
			ActionMenus.Menu menu = mine.get(i);
			addRenderableWidget(Button.builder(
				Component.literal(menu.name + "  (" + menu.buttons.size() + ")"), b -> {
					menu.buttons.add(button);
					ActionMenus.save();
					done();
				}).bounds(width / 2 - 150, y, 300, 20).build());
			y += ROW;
		}

		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(width / 2 - 174, height - 56, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(width / 2 + 154, height - 56, 20, 20).build());
		}

		// Somewhere to put it when there is nowhere yet, which is the first time anybody does this.
		addRenderableWidget(Button.builder(Component.literal("New menu"), b -> {
			ActionMenus.Menu menu = new ActionMenus.Menu();
			menu.name = "Menu " + (ActionMenus.mine().size() + 1);
			menu.buttons.add(button);
			ActionMenus.mine().add(menu);
			ActionMenus.save();
			// Straight into it: a new menu has no key yet, and a menu with no key never opens.
			minecraft.gui.setScreen(new MenuEditScreen(parent, menu));
		}).bounds(width / 2 - 150, height - 56, 300, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> done())
			.bounds(width / 2 - 50, height - 30, 100, 20).build());
	}

	private void done() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
		graphics.centeredText(font, Component.literal("/" + button.command), width / 2, 32, 0xFF7FB8FF);
		int pages = Math.max(1, (ActionMenus.mine().size() + PER_PAGE - 1) / PER_PAGE);
		if (pages > 1) {
			graphics.centeredText(font, Component.literal("Page " + (page + 1) + " of " + pages),
				width / 2, height - 50, 0xFFA0A0A0);
		}
		if (ActionMenus.mine().isEmpty()) {
			graphics.centeredText(font, Component.literal("You have no menus of your own yet."),
				width / 2, 54, 0xFFA0A0A0);
		}
	}

	@Override
	public void onClose() {
		done();
	}
}
