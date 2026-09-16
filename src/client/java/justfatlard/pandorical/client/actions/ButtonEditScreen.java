package justfatlard.pandorical.client.actions;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One button of an action menu: its icon, its label, and whether it runs a command or presses a
 * key. Worked on as a copy and written back on Save, so Cancel leaves it as it was.
 */
final class ButtonEditScreen extends Screen {
	private final Screen parent;
	private final ActionMenus.Menu menu;
	/** Its place in the menu; -1 while it is a new one not yet added. */
	private int index;
	private final ActionMenus.Entry entry;

	ButtonEditScreen(Screen parent, ActionMenus.Menu menu, int index) {
		super(Component.literal(index < 0 ? "New button" : "Button"));
		this.parent = parent;
		this.menu = menu;
		this.index = index;
		this.entry = index < 0 ? new ActionMenus.Entry() : menu.buttons.get(index).copy();
	}

	@Override
	protected void init() {
		int left = width / 2 - 150;
		int y = 36;

		addRenderableWidget(new IconButton(left, y - 2, IconButton.iconOf(entry.icon), Component.literal("Choose the icon"),
			b -> minecraft.gui.setScreen(new ItemPickerScreen(this, id -> entry.icon = id))));
		addRenderableWidget(Button.builder(Component.literal("Choose icon..."),
			b -> minecraft.gui.setScreen(new ItemPickerScreen(this, id -> entry.icon = id)))
			.bounds(left + 30, y, 132, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Use held item"), b -> {
			ItemStack held = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
			if (!held.isEmpty()) {
				entry.icon = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
				rebuildWidgets();
			}
		}).bounds(left + 168, y, 132, 20).build());
		y += 30;

		EditBox label = new EditBox(font, left, y, 300, 20, Component.literal("Label"));
		label.setMaxLength(60);
		label.setHint(Component.literal("Label, shown when you point at it"));
		label.setValue(entry.label);
		label.setResponder(v -> entry.label = v);
		addRenderableWidget(label);
		y += 30;

		String does = switch (entry.type) {
			case ActionMenus.KEY -> "Does: press a key";
			case ActionMenus.MENU -> "Does: open another menu";
			default -> "Does: run a command";
		};
		addRenderableWidget(Button.builder(Component.literal(does), b -> {
			entry.type = switch (entry.type) {
				case ActionMenus.COMMAND -> ActionMenus.KEY;
				case ActionMenus.KEY -> ActionMenus.MENU;
				default -> ActionMenus.COMMAND;
			};
			rebuildWidgets();
		}).bounds(left, y, 300, 20).build());
		y += 24;

		if (ActionMenus.MENU.equals(entry.type)) {
			ActionMenus.Menu target = ActionMenus.menuById(entry.menu);
			Component shown = target == null
				? Component.literal(entry.menu.isEmpty() ? "Choose a menu..." : "Choose a menu... (that one was removed)")
				: Component.literal("Opens: " + target.name);
			addRenderableWidget(Button.builder(shown,
				b -> minecraft.gui.setScreen(new MenuPickerScreen(this, menu, id -> entry.menu = id)))
				.bounds(left, y, 300, 20).build());
		} else if (ActionMenus.COMMAND.equals(entry.type)) {
			EditBox commandBox = new EditBox(font, left, y, 300, 20, Component.literal("Command"));
			commandBox.setMaxLength(256);
			commandBox.setHint(Component.literal("/command to run, as if you typed it"));
			commandBox.setValue(entry.command);
			commandBox.setResponder(v -> entry.command = v);
			addRenderableWidget(commandBox);
		} else {
			KeyMapping mapping = ActionMenus.mapping(entry.keyMapping);
			Component shown = mapping == null ? Component.literal("Choose a key...")
				: Component.translatable(mapping.getName()).append("  (").append(mapping.getTranslatedKeyMessage()).append(")");
			addRenderableWidget(Button.builder(shown,
				b -> minecraft.gui.setScreen(new KeyPickerScreen(this, name -> entry.keyMapping = name)))
				.bounds(left, y, 300, 20).build());
		}
		y += 34;

		if (index >= 0) {
			addRenderableWidget(Button.builder(Component.literal("Move earlier"), b -> move(-1))
				.bounds(left, y, 146, 20).build()).active = index > 0;
			addRenderableWidget(Button.builder(Component.literal("Move later"), b -> move(1))
				.bounds(left + 154, y, 146, 20).build()).active = index < menu.buttons.size() - 1;
		}

		int bottom = height - 30;
		addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
			if (index < 0) menu.buttons.add(entry);
			else menu.buttons.set(index, entry);
			ActionMenus.save();
			minecraft.gui.setScreen(parent);
		}).bounds(width / 2 - 154, bottom, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Remove"), b -> {
			if (index >= 0) menu.buttons.remove(index);
			ActionMenus.save();
			minecraft.gui.setScreen(parent);
		}).bounds(width / 2 - 50, bottom, 100, 20).build()).active = index >= 0;
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
			.bounds(width / 2 + 54, bottom, 100, 20).build());
	}

	/** Swap places with its neighbour, saved at once: the order is the menu's, not this edit's. */
	private void move(int direction) {
		List<ActionMenus.Entry> buttons = menu.buttons;
		int to = index + direction;
		if (to < 0 || to >= buttons.size()) return;
		ActionMenus.Entry neighbour = buttons.get(to);
		buttons.set(to, buttons.get(index));
		buttons.set(index, neighbour);
		index = to;
		ActionMenus.save();
		rebuildWidgets();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
