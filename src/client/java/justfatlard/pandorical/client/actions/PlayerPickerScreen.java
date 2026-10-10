package justfatlard.pandorical.client.actions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import justfatlard.pandorical.client.component.PlayerFaceComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Who a button is for, picked by face rather than typed: everybody else online, in a grid. Picking
 * one runs the button for them; a button for several has them ticked and runs once each on Done.
 */
final class PlayerPickerScreen extends Screen {
	private static final int FACE = 24;
	private static final int GAP = 4;
	private static final int SELECTED = 0xFF55FF55;

	private final ActionMenus.Entry entry;
	private final boolean many;
	private final Set<String> picked = new LinkedHashSet<>();
	private Button done;

	PlayerPickerScreen(ActionMenus.Entry entry, boolean many) {
		super(Component.literal(entry.label.isEmpty() ? "Who?" : entry.label + ": who?"));
		this.entry = entry;
		this.many = many;
	}

	private List<PlayerInfo> others() {
		List<PlayerInfo> out = new ArrayList<>();
		if (minecraft.getConnection() == null || minecraft.player == null) return out;
		UUID me = minecraft.player.getUUID();
		for (PlayerInfo info : minecraft.getConnection().getListedOnlinePlayers()) {
			if (!info.getProfile().id().equals(me)) out.add(info);
		}
		out.sort(Comparator.comparing(info -> info.getProfile().name().toLowerCase(java.util.Locale.ROOT)));
		return out;
	}

	@Override
	protected void init() {
		List<PlayerInfo> players = others();
		if (players.isEmpty()) {
			addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(width / 2 - 40, height / 2 + 10, 80, 20).build());
			return;
		}
		int cols = ActionMenuScreen.columns(players.size());
		int rows = (players.size() + cols - 1) / cols;
		int step = FACE + GAP;
		int left = (width - (cols * step - GAP)) / 2;
		int top = (height - (rows * step - GAP)) / 2;
		for (int i = 0; i < players.size(); i++) {
			PlayerInfo info = players.get(i);
			addRenderableWidget(new Face(left + (i % cols) * step, top + (i / cols) * step, info));
		}
		if (many) {
			done = addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
				List<String> who = List.copyOf(picked);
				minecraft.gui.setScreen(null);
				for (String name : who) ActionMenus.runFor(entry, name);
			}).bounds(width / 2 - 40, top + rows * step + 6, 80, 20).build());
			done.active = false;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		List<PlayerInfo> players = others();
		if (players.isEmpty()) {
			graphics.centeredText(font, Component.literal("Nobody else is here"), width / 2, height / 2 - 10, 0xFFFFFFFF);
			return;
		}
		int cols = ActionMenuScreen.columns(players.size());
		int rows = (players.size() + cols - 1) / cols;
		int top = (height - (rows * (FACE + GAP) - GAP)) / 2;
		graphics.centeredText(font, title, width / 2, top - 14, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** A face to pick: their name on hover, a green edge while ticked. */
	private final class Face extends Button {
		private final PlayerInfo info;

		Face(int x, int y, PlayerInfo info) {
			super(x, y, FACE, FACE, Component.literal(info.getProfile().name()), b -> {}, DEFAULT_NARRATION);
			this.info = info;
			setTooltip(Tooltip.create(getMessage()));
		}

		@Override
		public void onPress(net.minecraft.client.input.InputWithModifiers input) {
			String name = info.getProfile().name();
			if (!many) {
				minecraft.gui.setScreen(null);
				ActionMenus.runFor(entry, name);
				return;
			}
			if (!picked.remove(name)) picked.add(name);
			done.active = !picked.isEmpty();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			extractDefaultSprite(graphics);
			if (picked.contains(info.getProfile().name())) {
				graphics.fill(getX(), getY(), getX() + width, getY() + 2, SELECTED);
				graphics.fill(getX(), getY() + height - 2, getX() + width, getY() + height, SELECTED);
				graphics.fill(getX(), getY(), getX() + 2, getY() + height, SELECTED);
				graphics.fill(getX() + width - 2, getY(), getX() + width, getY() + height, SELECTED);
			}
			PlayerFaceExtractor.extractRenderState(graphics,
				PlayerFaceComponent.skinOf(minecraft, info.getProfile().id()), getX() + 4, getY() + 4, FACE - 8);
		}
	}
}
