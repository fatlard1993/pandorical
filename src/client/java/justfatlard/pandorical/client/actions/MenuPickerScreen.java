package justfatlard.pandorical.client.actions;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** The player's other menus, for a button that opens one of them. */
final class MenuPickerScreen extends Screen {
	private static final int ROW = 22;

	private final Screen parent;
	private final ActionMenus.Menu editing;
	private final Consumer<String> chosen;

	MenuPickerScreen(Screen parent, ActionMenus.Menu editing, Consumer<String> chosen) {
		super(Component.literal("Choose a menu"));
		this.parent = parent;
		this.editing = editing;
		this.chosen = chosen;
	}

	@Override
	protected void init() {
		int y = 40;
		for (ActionMenus.Menu menu : ActionMenus.mine()) {
			// A button opening the menu it sits in would only open it again.
			if (menu == editing) continue;
			if (y > height - 60) break;
			addRenderableWidget(Button.builder(Component.literal(menu.name + "  (" + menu.buttons.size() + ")"), b -> {
				chosen.accept(menu.id);
				onClose();
			}).bounds(width / 2 - 150, y, 300, 20).build());
			y += ROW;
		}
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
			.bounds(width / 2 - 50, height - 30, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
		if (ActionMenus.mine().size() < 2) {
			graphics.centeredText(font, Component.literal("There is no other menu yet: add one from the list of menus."),
				width / 2, 44, 0xFFA0A0A0);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
