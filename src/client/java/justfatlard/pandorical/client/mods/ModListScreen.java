package justfatlard.pandorical.client.mods;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.client.settings.ClientModFiles;
import justfatlard.pandorical.client.settings.ModsScreenActions;
import justfatlard.pandorical.protocol.ClientModAboutC2S;
import justfatlard.pandorical.protocol.ClientModFilesC2S;
import justfatlard.pandorical.settings.ModCatalog;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The mods the player's own game is running, drawn by that game: from the title screen, and on a
 * server without Pandorical, where the server-drawn mods screen is not on offer. Each mod's readme,
 * licence, contributors and links, a Configure button for a config screen it declared for Mod
 * Menu, and switching its jar off or on.
 */
@Environment(EnvType.CLIENT)
public final class ModListScreen extends Screen {
	private static final int ROW = 22;
	private static final int LINE = 10;
	private static final int ICON = 32;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFA0A0A0;
	private static final int DIM = 0xFF707070;

	private record Entry(String id, String name, String version, String authors, String description,
			List<ModCatalog.Line> readme, ClientModAboutC2S.@Nullable About about, @Nullable String file, boolean on) {}

	private record Shown(String text, int color, int indent) {}

	private final Screen parent;
	private List<Entry> entries = List.of();
	private String query = "";
	private @Nullable String selected;
	private int listOffset;
	private int paneOffset;
	private List<Shown> lines = List.of();
	private int paneTop;
	/** Typing rebuilds the screen, and a rebuild drops focus; this puts it back in the box. */
	private boolean searching;

	public ModListScreen(Screen parent) {
		super(Component.translatable("pandorical.mods.client_title"));
		this.parent = parent;
	}

	private int listW() { return Math.clamp(width * 35 / 100, 120, 200); }
	private int paneX() { return 8 + listW() + 8; }
	private int paneW() { return width - paneX() - 8; }
	private int listTop() { return 48; }
	private int bottom() { return height - 32; }

	private void load() {
		List<ClientModFilesC2S.Entry> files = ClientModFiles.rescan();
		List<Entry> out = new ArrayList<>();
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			var meta = container.getMetadata();
			if (ModCatalog.isPlumbing(meta.getId()) || container.getContainingMod().isPresent()) continue;
			List<String> names = new ArrayList<>();
			for (Person person : meta.getAuthors()) names.add(person.getName());
			String file = null;
			boolean switchedOff = false;
			for (var f : files) {
				if (!f.id().equals(meta.getId())) continue;
				if (f.enabled()) file = f.file();
				else switchedOff = true;
			}
			// Switched off since launch: still running, but listed once, as off, where Enable is
			if (file == null && switchedOff) continue;
			out.add(new Entry(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(), String.join(", ", names),
				meta.getDescription(), ModCatalog.readmeOf(container), ClientModIcons.aboutOf(container), file, true));
		}
		out.sort(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
		List<Entry> off = new ArrayList<>();
		for (var f : files) {
			if (!f.enabled()) off.add(new Entry(f.id(), f.name(), f.version(), "", "", List.of(), null, f.file(), false));
		}
		off.sort(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
		out.addAll(off);
		entries = out;
	}

	private List<Entry> matching() {
		String q = query.strip().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return entries;
		List<Entry> out = new ArrayList<>();
		for (Entry entry : entries) {
			String hay = (entry.name() + "\n" + entry.id() + "\n" + entry.authors() + "\n" + entry.description()).toLowerCase(Locale.ROOT);
			if (hay.contains(q)) out.add(entry);
		}
		return out;
	}

	private @Nullable Entry chosen() {
		for (Entry entry : entries) if (entry.id().equals(selected)) return entry;
		return null;
	}

	@Override
	protected void init() {
		if (entries.isEmpty()) load();
		if (selected == null && !entries.isEmpty()) selected = entries.get(0).id();

		EditBox search = new EditBox(font, 8, 24, listW(), 18, Component.literal("Search"));
		search.setHint(Component.literal("Search mods"));
		search.setMaxLength(64);
		search.setValue(query);
		search.setResponder(value -> {
			if (value.equals(query)) return;
			query = value;
			listOffset = 0;
			searching = true;
			rebuildWidgets();
		});
		addRenderableWidget(search);
		if (searching) setFocused(search);

		List<Entry> shown = matching();
		int rows = Math.max(1, (bottom() - listTop()) / ROW);
		listOffset = Math.clamp(listOffset, 0, Math.max(0, shown.size() - rows));
		for (int i = 0; i < rows && listOffset + i < shown.size(); i++) {
			Entry entry = shown.get(listOffset + i);
			String label = (entry.id().equals(selected) ? "\u25B6 " : "") + entry.name() + (entry.on() ? "" : " (off)");
			addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label, listW() - 8)), b -> {
				selected = entry.id();
				paneOffset = 0;
				searching = false;
				rebuildWidgets();
			}).bounds(8, listTop() + i * ROW, listW(), 20).build());
		}

		Entry entry = chosen();
		int y = 24 + ICON + 6;
		if (entry != null) {
			int x = paneX();
			if (entry.on() && ModConfigScreens.has(entry.id())) {
				x = button(x, y, "Configure", () -> ModsScreenActions.openConfigOver(this, entry.id()));
			}
			if (entry.file() != null && !entry.id().equals(Pandorical.MOD_ID)) {
				x = button(x, y, entry.on() ? "Disable" : "Enable", () -> {
					ClientModFiles.ask(entry.file(), false);
					entries = List.of();
				});
			}
			if (entry.about() != null) {
				// Two rows of buttons at most, so the readme keeps the pane
				int lastRow = y + 22;
				for (var link : entry.about().links()) {
					int w = font.width(link.label()) + 12;
					if (x > paneX() && x + w > paneX() + paneW()) {
						if (y >= lastRow) break;
						x = paneX();
						y += 22;
					}
					x = button(x, y, link.label(), () -> ModsScreenActions.openLinkFrom(this, link.url()));
				}
			}
			if (x > paneX()) y += 24;
		}
		paneTop = y;
		lines = entry == null ? List.of() : describe(entry);

		addRenderableWidget(Button.builder(Component.literal("Mods folder"), b -> ModsScreenActions.openModsFolderNow()).bounds(8, height - 26, Math.min(listW(), width / 2 - 58), 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(width / 2 - 50, height - 26, 100, 20).build());
	}

	private int button(int x, int y, String label, Runnable press) {
		int w = Math.min(font.width(label) + 12, paneW());
		addRenderableWidget(Button.builder(Component.literal(label), b -> press.run()).bounds(x, y, w, 20).build());
		return x + w + 4;
	}

	/** Description, licence, contributors and readme, wrapped to the pane. */
	private List<Shown> describe(Entry entry) {
		List<Shown> out = new ArrayList<>();
		int w = paneW() - 8;
		if (!entry.on()) wrap(out, "Switched off. Enable it, then restart the game.", GREY, 0, w);
		if (!entry.description().isEmpty()) wrap(out, entry.description(), WHITE, 0, w);
		if (entry.about() != null) {
			if (!entry.about().license().isEmpty()) wrap(out, "License: " + entry.about().license(), GREY, 0, w);
			if (!entry.about().contributors().isEmpty()) wrap(out, "Contributors: " + entry.about().contributors(), GREY, 0, w);
			if (!entry.about().bundled().isEmpty()) wrap(out, "Includes: " + entry.about().bundled(), GREY, 0, w);
		}
		List<ModCatalog.Line> readme = entry.readme();
		int first = !readme.isEmpty() && readme.get(0).kind() == ModCatalog.Kind.HEADING && readme.get(0).level() == 1 ? 1 : 0;
		for (int i = first; i < readme.size(); i++) {
			ModCatalog.Line line = readme.get(i);
			switch (line.kind()) {
				case HEADING -> {
					out.add(new Shown("", WHITE, 0));
					wrap(out, line.text(), WHITE, 0, w);
				}
				case TEXT -> wrap(out, line.text(), GREY, 0, w);
				case BULLET -> wrap(out, "• " + line.text(), GREY, 8 + 8 * line.level(), w);
				case NUMBERED -> wrap(out, line.text(), GREY, 8 + 8 * line.level(), w);
				case QUOTE -> wrap(out, line.text(), DIM, 8, w);
				case CODE -> out.add(new Shown(line.text(), DIM, 8));
				case RULE -> out.add(new Shown("", GREY, 0));
			}
		}
		return out;
	}

	private void wrap(List<Shown> out, String text, int color, int indent, int width) {
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && font.width(next) > width - indent) {
				out.add(new Shown(line.toString(), color, indent));
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		out.add(new Shown(line.toString(), color, indent));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int steps = vertical > 0 ? -1 : vertical < 0 ? 1 : 0;
		if (mouseX < paneX()) {
			listOffset = Math.max(0, listOffset + steps);
			rebuildWidgets();
		} else {
			int visible = (bottom() - paneTop) / LINE;
			paneOffset = Math.clamp(paneOffset + steps * 3, 0, Math.max(0, lines.size() - visible));
		}
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 8, WHITE);
		int rows = Math.max(1, (bottom() - listTop()) / ROW);
		int count = matching().size();
		if (count > rows) {
			// Where in the list the shown rows are, beside it
			int track = rows * ROW - 2;
			int thumb = Math.max(8, track * rows / count);
			int top = listTop() + (track - thumb) * listOffset / Math.max(1, count - rows);
			graphics.fill(8 + listW() + 2, top, 8 + listW() + 4, top + thumb, GREY);
		}
		Entry entry = chosen();
		if (entry == null) return;
		int x = paneX();
		Identifier icon = entry.about() == null || entry.about().icon().isEmpty() ? null : Identifier.tryParse(entry.about().icon());
		if (icon != null && ClientModIcons.drawable(icon)) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, 24, 0.0F, 0.0F, ICON, ICON, ICON, ICON);
			x += ICON + 6;
		}
		int room = paneX() + paneW() - x;
		graphics.text(font, font.plainSubstrByWidth(entry.name() + "  " + entry.version(), room), x, 28, WHITE);
		graphics.text(font, font.plainSubstrByWidth(entry.authors().isEmpty() ? entry.id() : entry.authors(), room), x, 42, GREY);
		graphics.enableScissor(paneX(), paneTop, paneX() + paneW(), bottom());
		int y = paneTop;
		for (int i = paneOffset; i < lines.size() && y < bottom(); i++) {
			Shown line = lines.get(i);
			graphics.text(font, line.text(), paneX() + line.indent(), y, line.color());
			y += LINE;
		}
		graphics.disableScissor();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
