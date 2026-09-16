package justfatlard.pandorical.client.actions;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Every key in the controls screen, searchable, for a button that presses one. */
final class KeyPickerScreen extends Screen {
	private static final int ROW = 22;

	private final Screen parent;
	private final Consumer<String> chosen;
	private String query = "";
	private int page;

	KeyPickerScreen(Screen parent, Consumer<String> chosen) {
		super(Component.literal("Choose a key"));
		this.parent = parent;
		this.chosen = chosen;
	}

	private List<KeyMapping> matching() {
		String q = query.toLowerCase(Locale.ROOT).strip();
		List<KeyMapping> out = new ArrayList<>();
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			String name = Component.translatable(mapping.getName()).getString().toLowerCase(Locale.ROOT);
			if (q.isEmpty() || name.contains(q)) out.add(mapping);
		}
		return out;
	}

	@Override
	protected void init() {
		EditBox search = new EditBox(font, width / 2 - 100, 30, 200, 20, Component.literal("Search"));
		search.setHint(Component.literal("Search keys"));
		search.setValue(query);
		search.setResponder(v -> {
			if (v.equals(query)) return;
			query = v;
			page = 0;
			rebuildWidgets();
		});
		addRenderableWidget(search);
		setInitialFocus(search);

		int perPage = Math.max(1, (height - 110) / ROW);
		List<KeyMapping> mappings = matching();
		int pages = Math.max(1, (mappings.size() + perPage - 1) / perPage);
		page = Math.min(page, pages - 1);
		for (int i = 0; i < perPage && page * perPage + i < mappings.size(); i++) {
			KeyMapping mapping = mappings.get(page * perPage + i);
			Component shown = Component.translatable(mapping.getName())
				.append("  (").append(mapping.getTranslatedKeyMessage()).append(")");
			addRenderableWidget(Button.builder(shown, b -> {
				chosen.accept(mapping.getName());
				onClose();
			}).bounds(width / 2 - 150, 58 + i * ROW, 300, 20).build());
		}

		int bottom = height - 30;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = Math.floorMod(page - 1, pages); rebuildWidgets(); })
			.bounds(width / 2 - 154, bottom, 40, 20).build()).active = pages > 1;
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
			.bounds(width / 2 - 50, bottom, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> { page = Math.floorMod(page + 1, pages); rebuildWidgets(); })
			.bounds(width / 2 + 114, bottom, 40, 20).build()).active = pages > 1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
