package justfatlard.pandorical.client.actions;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;


/** One action menu: its name, the key that opens it, and its buttons laid out as they will be. */
final class MenuEditScreen extends Screen {
	private static final int GAP = 4;

	private final Screen parent;
	private final ActionMenus.Menu menu;
	private boolean awaitingKey;
	private Button keyButton;

	MenuEditScreen(Screen parent, ActionMenus.Menu menu) {
		super(Component.literal("Action menu"));
		this.parent = parent;
		this.menu = menu;
	}

	@Override
	protected void init() {
		int left = width / 2 - 150;
		EditBox name = new EditBox(font, left, 36, 146, 20, Component.literal("Name"));
		name.setMaxLength(40);
		name.setValue(menu.name);
		name.setResponder(v -> menu.name = v.isBlank() ? "Actions" : v);
		addRenderableWidget(name);

		keyButton = addRenderableWidget(Button.builder(keyLabel(), b -> {
			awaitingKey = true;
			b.setMessage(Component.literal("> press a key, Escape for none <"));
		}).bounds(left + 154, 36, 146, 20).build());

		// The buttons, in the grid the menu will open in, and one more square to add another.
		int count = menu.buttons.size() + 1;
		int cols = ActionMenuScreen.columns(count);
		int step = IconButton.SIZE + GAP;
		int gridLeft = (width - (cols * step - GAP)) / 2;
		for (int i = 0; i < count; i++) {
			int x = gridLeft + (i % cols) * step;
			int y = 76 + (i / cols) * step;
			if (i < menu.buttons.size()) {
				ActionMenus.Entry entry = menu.buttons.get(i);
				int index = i;
				String shown = entry.label.isEmpty() ? "(no label)" : entry.label;
				addRenderableWidget(new IconButton(x, y, IconButton.iconOf(entry.icon), Component.literal(shown),
					b -> minecraft.gui.setScreen(new ButtonEditScreen(this, menu, index))));
			} else {
				addRenderableWidget(new IconButton(x, y, new ItemStack(Items.WRITABLE_BOOK), Component.literal("Add a button"),
					b -> minecraft.gui.setScreen(new ButtonEditScreen(this, menu, -1))));
			}
		}

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(width / 2 - 75, height - 30, 150, 20).build());
	}

	private Component keyLabel() {
		return Component.literal("Opens with: ").append(MenuListScreen.keyName(menu.key));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (awaitingKey) {
			awaitingKey = false;
			menu.key = event.key() == InputConstants.KEY_ESCAPE ? "" : InputConstants.getKey(event).getName();
			ActionMenus.save();
			keyButton.setMessage(keyLabel());
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
		graphics.centeredText(font, Component.literal("Click a button to change it; the book adds one."),
			width / 2, 64, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		ActionMenus.save();
		minecraft.gui.setScreen(parent);
	}
}
