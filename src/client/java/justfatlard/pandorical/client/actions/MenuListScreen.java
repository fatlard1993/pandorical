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
		List<ActionMenus.Menu> menus = ActionMenus.mine();
		int pages = Math.max(1, (menus.size() + MOST_ROWS - 1) / MOST_ROWS);
		page = Math.min(page, pages - 1);
		int left = width / 2 - 150;
		int y = 40;
		for (int i = page * MOST_ROWS; i < Math.min(menus.size(), (page + 1) * MOST_ROWS); i++) {
			ActionMenus.Menu menu = menus.get(i);
			Component name = Component.literal(menu.name + "  (" + menu.buttons.size() + ")  ").append(keyName(menu.key));
			addRenderableWidget(Button.builder(name, b -> minecraft.gui.setScreen(new MenuEditScreen(this, menu)))
				.bounds(left, y, 230, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Remove"), b -> {
				menus.remove(menu);
				ActionMenus.save();
				rebuildWidgets();
			}).bounds(left + 236, y, 64, 20).build());
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
			menu.name = "Menu " + (menus.size() + 1);
			menus.add(menu);
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
		if (ActionMenus.mine().isEmpty()) {
			graphics.centeredText(font, Component.literal("No menus yet. Add one, give it a key, and fill it with buttons."),
				width / 2, 48, 0xFFA0A0A0);
		}
	}

	@Override
	public void onClose() {
		ActionMenus.save();
		minecraft.gui.setScreen(parent);
	}
}
