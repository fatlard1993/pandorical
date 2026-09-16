package justfatlard.pandorical.client.actions;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Every item there is, searchable by name or id, for the picture on a button. */
final class ItemPickerScreen extends Screen {
	private static final int GAP = 2;

	private final Screen parent;
	private final Consumer<String> chosen;
	private String query = "";
	private int page;

	ItemPickerScreen(Screen parent, Consumer<String> chosen) {
		super(Component.literal("Choose an icon"));
		this.parent = parent;
		this.chosen = chosen;
	}

	private List<Item> matching() {
		String q = query.toLowerCase(Locale.ROOT).strip();
		List<Item> out = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) continue;
			if (q.isEmpty() || item.getName(new ItemStack(item)).getString().toLowerCase(Locale.ROOT).contains(q)
					|| BuiltInRegistries.ITEM.getKey(item).toString().contains(q)) {
				out.add(item);
			}
		}
		return out;
	}

	@Override
	protected void init() {
		EditBox search = new EditBox(font, width / 2 - 100, 30, 200, 20, Component.literal("Search"));
		search.setHint(Component.literal("Search items"));
		search.setValue(query);
		search.setResponder(v -> {
			if (v.equals(query)) return;
			query = v;
			page = 0;
			rebuildWidgets();
		});
		addRenderableWidget(search);
		setInitialFocus(search);

		int step = IconButton.SIZE + GAP;
		int cols = Math.max(1, (width - 40) / step);
		int rows = Math.max(1, (height - 110) / step);
		int perPage = cols * rows;
		List<Item> items = matching();
		int pages = Math.max(1, (items.size() + perPage - 1) / perPage);
		page = Math.min(page, pages - 1);
		int left = (width - (cols * step - GAP)) / 2;
		for (int i = 0; i < perPage && page * perPage + i < items.size(); i++) {
			Item item = items.get(page * perPage + i);
			addRenderableWidget(new IconButton(left + (i % cols) * step, 58 + (i / cols) * step, new ItemStack(item),
				item.getName(new ItemStack(item)), b -> {
					chosen.accept(BuiltInRegistries.ITEM.getKey(item).toString());
					onClose();
				}));
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
