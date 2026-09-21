package justfatlard.pandorical.client.actions;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Every action menu of the player's, to add, edit or remove. */
final class MenuListScreen extends Screen {
	private static final int ROW = 24;
	private static final int MOST_ROWS = 7;

	private final Screen parent;
	private int page;

	MenuListScreen(Screen parent) {
		super(Component.literal("Action menus"));
		this.parent = parent;
	}

	static Component keyName(String key) {
		return key.isEmpty() ? Component.literal("no key") : InputConstants.getKey(key).getDisplayName();
	}

	@Override
	protected void init() {
		// The player's own first. all() leads with the server's, and six suite mods offering menus
		// put a player's first menu on page two of a screen they opened to make it - behind rows
		// they cannot edit, under a message saying they have none.
		List<ActionMenus.Menu> menus = new java.util.ArrayList<>(ActionMenus.mine());
		menus.addAll(ActionMenus.all().stream().filter(menu -> menu.builtin).toList());
		int pages = Math.max(1, (menus.size() + MOST_ROWS - 1) / MOST_ROWS);
		page = Math.min(page, pages - 1);
		int left = width / 2 - 150;
		int y = 40;
		for (int i = page * MOST_ROWS; i < Math.min(menus.size(), (page + 1) * MOST_ROWS); i++) {
			ActionMenus.Menu menu = menus.get(i);
			Component name = Component.literal(menu.name + "  (" + menu.buttons.size() + ")  ").append(keyName(menu.key));
			addRenderableWidget(Button.builder(name, b -> minecraft.gui.setScreen(new MenuEditScreen(this, menu)))
				.bounds(left, y, 230, 20).build());
			if (menu.builtin) {
				// The server's, not theirs: there is nothing here to remove, and removing it would
				// only mean getting it back on the next join.
				addRenderableWidget(Button.builder(Component.literal("Server's"), b -> {})
					.bounds(left + 236, y, 64, 20).build()).active = false;
			} else {
				addRenderableWidget(Button.builder(Component.literal("Remove"), b -> {
					// Six pixels from the button that opens it, and it takes every button on the
					// menu with it. Nothing here can put one back, so it gets asked first.
					minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
						yes -> {
							if (yes) {
								ActionMenus.mine().remove(menu);
								ActionMenus.save();
							}
							minecraft.gui.setScreen(this);
						},
						Component.literal("Remove this menu?"),
						Component.literal("\"" + menu.name + "\" and its "
							+ menu.buttons.size() + " button" + (menu.buttons.size() == 1 ? "" : "s")
							+ " go for good. Nothing here can put them back."),
						Component.literal("Remove it"),
						net.minecraft.network.chat.CommonComponents.GUI_CANCEL));
				}).bounds(left + 236, y, 64, 20).build());
			}
			y += ROW;
		}
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = Math.floorMod(page - 1, pages); rebuildWidgets(); })
				.bounds(left, y + 4, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> { page = Math.floorMod(page + 1, pages); rebuildWidgets(); })
				.bounds(left + 280, y + 4, 20, 20).build());
		}

		int bottom = height - 30;
		addRenderableWidget(Button.builder(Component.literal("Add a menu"), b -> {
			ActionMenus.Menu menu = new ActionMenus.Menu();
			menu.name = "Menu " + (ActionMenus.mine().size() + 1);
			ActionMenus.mine().add(menu);
			ActionMenus.save();
			minecraft.gui.setScreen(new MenuEditScreen(this, menu));
		}).bounds(width / 2 - 154, bottom, 150, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(width / 2 + 4, bottom, 150, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
		int shown = ActionMenus.all().size();
		int pages = Math.max(1, (shown + MOST_ROWS - 1) / MOST_ROWS);
		if (pages > 1) {
			graphics.centeredText(font, Component.literal("Page " + (page + 1) + " of " + pages),
				width / 2, height - 46, 0xFFA0A0A0);
		}
		if (ActionMenus.mine().isEmpty()) {
			// Said under the list, not over it. At the top it landed on the first row, so a server
			// offering menus drew a screen full of them with "no menus yet" written across them.
			graphics.centeredText(font, Component.literal(
					shown == 0
						? "No menus yet. Add one, give it a key, and fill it with buttons."
						: "None of your own yet - the rows above are this server's. \"Add a menu\" starts one."),
				width / 2, height - 62, 0xFFA0A0A0);
		}
	}

	@Override
	public void onClose() {
		ActionMenus.save();
		minecraft.gui.setScreen(parent);
	}
}
