package justfatlard.pandorical.client.actions;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Everything the mods on this server put their name to, as a list to pick from.
 *
 * <p>Adding a button by hand means knowing the command, writing a label and hunting an item to
 * stand for it - three things a mod already decided when it promoted the button. This is the same
 * button, arriving finished.
 *
 * <p>Held for the session rather than saved: it is whatever the server you are on offers, and a
 * list that outlived the server would be a list of commands that no longer exist.
 */
final class PromotedPickerScreen extends Screen {
	private static final int ROW = 22;
	private static final int PER_PAGE = 12;

	private final Screen parent;
	private final Consumer<ActionMenus.Entry> chosen;
	private int page;

	PromotedPickerScreen(Screen parent, Consumer<ActionMenus.Entry> chosen) {
		super(Component.literal("Add what this server offers"));
		this.parent = parent;
		this.chosen = chosen;
	}

	@Override
	protected void init() {
		var all = ActionMenus.promoted();
		int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.clamp(page, 0, pages - 1);

		int y = 40;
		for (int i = page * PER_PAGE; i < Math.min(all.size(), (page + 1) * PER_PAGE); i++) {
			ActionMenus.Promoted promoted = all.get(i);
			String label = promoted.button().label.isEmpty()
				? promoted.button().command : promoted.button().label;

			addRenderableWidget(new IconButton(width / 2 - 160, y - 2,
				IconButton.iconOf(promoted.button().icon), Component.literal(label), b -> {}));
			addRenderableWidget(Button.builder(
				Component.literal(promoted.group() + " - " + label), b -> {
					// A copy, so editing the one that was added never changes the offer.
					chosen.accept(promoted.button().copy());
					onClose();
				}).bounds(width / 2 - 128, y, 288, 20).build());
			y += ROW;
		}

		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(width / 2 - 160, height - 30, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(width / 2 + 140, height - 30, 20, 20).build());
		}
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
			.bounds(width / 2 - 50, height - 30, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
		if (ActionMenus.promoted().isEmpty()) {
			graphics.centeredText(font, Component.literal(
					"This server offers nothing of its own. Add a button by hand instead."),
				width / 2, 44, 0xFFA0A0A0);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
