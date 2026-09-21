package justfatlard.pandorical.gametest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.protocol.ActionMenusS2C;
import justfatlard.pandorical.protocol.AddToMenuS2C;
import justfatlard.pandorical.protocol.CameraHintS2C;
import justfatlard.pandorical.protocol.ChestOverlayS2C;
import justfatlard.pandorical.protocol.ClientModFilesC2S;
import justfatlard.pandorical.protocol.ClientModToggleS2C;
import justfatlard.pandorical.protocol.CloseScreenS2C;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.ContentReadyC2S;
import justfatlard.pandorical.protocol.EntityOverlayS2C;
import justfatlard.pandorical.protocol.HelloC2S;
import justfatlard.pandorical.protocol.HelloS2C;
import justfatlard.pandorical.protocol.HideHudS2C;
import justfatlard.pandorical.protocol.KeyPressC2S;
import justfatlard.pandorical.protocol.KeybindDeclarationsS2C;
import justfatlard.pandorical.protocol.KeybindDefaultsS2C;
import justfatlard.pandorical.protocol.KeybindRebindS2C;
import justfatlard.pandorical.protocol.NotUnderstoodC2S;
import justfatlard.pandorical.protocol.OpenScreenS2C;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import justfatlard.pandorical.protocol.RequirementS2C;
import justfatlard.pandorical.protocol.ScreenActionC2S;
import justfatlard.pandorical.protocol.ShowHudS2C;
import justfatlard.pandorical.protocol.UpdateHudS2C;
import justfatlard.pandorical.protocol.UpdateScreenS2C;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.codec.StreamCodec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The shape of the payloads already in players' hands, written down in bytes.
 *
 * <p>A client cannot be recalled. Whatever a released Pandorical reads, it reads forever, and
 * Minecraft disconnects a reader that finds bytes left over, so adding one field to a payload
 * already shipped kicks everyone running the old client. The rule is that a shipped layout never
 * changes and a changed word becomes a new channel beside the old one, but a rule nothing checks
 * is a rule somebody breaks on a Tuesday.
 *
 * <p>So this encodes one fixed value per frozen channel and compares it to the bytes recorded for
 * it. Which channels those are is not a list anybody keeps up to date: the test
 * finds every payload the mod defines and fails on one it has no sample for, because a freeze
 * that quietly covers a third of the protocol reads exactly like a freeze that covers all of it. A failure here is not a broken test: it is a change that would have broken
 * every client of this protocol. Either put the layout back, or give the new shape its own channel
 * name and leave this one alone. The recorded file is only ever replaced when
 * {@link Pandorical#PROTOCOL_VERSION} is raised, which is the version that refuses old clients
 * outright.
 */
public final class ProtocolFrozen implements FabricClientGameTest {

	private static final ComponentDef PANEL = new ComponentDef("bg", "panel", 1, 2, 3, 4,
		Map.of("border", "beveled"), List.of());
	private static final ComponentDef TEXT = new ComponentDef("line", "text", 5, 6, 7, 8,
		Map.of("text", "frozen"), List.of(PANEL));

	/**
	 * Channels this test does not yet hold, named so the gap can be counted.
	 *
	 * <p>Every one of these is a layout a released client already reads and nothing here would
	 * notice changing. They are listed rather than silently absent because a freeze that covers
	 * some of the protocol and reads as covering all of it is worse than no freeze: it is a green
	 * that gets spent. This list is meant to shrink, and a channel added from here on lands in the
	 * failure above rather than in here.
	 */
	private static final java.util.Set<String> NOT_YET_FROZEN = java.util.Set.of(
		"banner_decals", "block_marks", "block_tint_positions", "block_tints_config",
		"client_setting", "client_settings", "content_ready_config", "despawn_structure",
		"entity_renderers", "inventory_button", "inventory_buttons", "keepsake_store",
		"keepsakes", "keepsakes_ask", "key_release", "keybind_bindings",
		"map_relief_v2", "mount_policy", "open_settings", "pictures",
		"play_animation", "player_inv_registrations", "render_policy", "set_structure_visible",
		"set_structure_walkable", "set_vanilla_hud_elements", "skin_override", "spawn_structure",
		"sync_assets", "sync_assets_config", "sync_content", "sync_content_config",
		"update_structure_blocks", "update_structure_pose", "viewport");

	/** One fixed value per frozen channel. Never edit these: new bytes here mean a changed layout. */
	private static Map<String, byte[]> samples() {
		Map<String, byte[]> out = new LinkedHashMap<>();
		out.put("hello_s2c", encode(HelloS2C.STREAM_CODEC, new HelloS2C(15, List.of("screens", "hud"))));
		out.put("hello_c2s", encode(HelloC2S.STREAM_CODEC, new HelloC2S(15, List.of("screens", "hud"))));
		out.put("requirement", encode(RequirementS2C.STREAM_CODEC, new RequirementS2C(15, 15, "15.0.0")));
		out.put("open_screen", encode(OpenScreenS2C.STREAM_CODEC, new OpenScreenS2C(
			"mymod:s1", "mymod:type", 176, 166, true, "Title", List.of(TEXT),
			Optional.empty(), Optional.empty())));
		out.put("update_screen", encode(UpdateScreenS2C.STREAM_CODEC, new UpdateScreenS2C(
			"mymod:s1", List.of(new ComponentUpdate("line", Map.of("text", "changed"))))));
		out.put("close_screen", encode(CloseScreenS2C.STREAM_CODEC, new CloseScreenS2C("mymod:s1")));
		out.put("show_hud", encode(ShowHudS2C.STREAM_CODEC, new ShowHudS2C(
			"mymod:h1", "top_left", 4, 5, List.of(TEXT))));
		out.put("update_hud", encode(UpdateHudS2C.STREAM_CODEC, new UpdateHudS2C(
			"mymod:h1", List.of(new ComponentUpdate("line", Map.of("text", "changed"))))));
		out.put("hide_hud", encode(HideHudS2C.STREAM_CODEC, new HideHudS2C("mymod:h1")));
		out.put("screen_action", encode(ScreenActionC2S.STREAM_CODEC, new ScreenActionC2S(
			"mymod:s1", "ok", "click", Map.of("value", "1"))));
		out.put("content_ready", encode(ContentReadyC2S.STREAM_CODEC, new ContentReadyC2S()));
		out.put("key_press", encode(KeyPressC2S.STREAM_CODEC, new KeyPressC2S(3)));
		out.put("keybind_rebind", encode(KeybindRebindS2C.STREAM_CODEC, new KeybindRebindS2C(3)));
		out.put("keybind_declarations", encode(KeybindDeclarationsS2C.STREAM_CODEC,
			new KeybindDeclarationsS2C(List.of(0, 1, 2))));
		out.put("chest_overlay", encode(ChestOverlayS2C.STREAM_CODEC, new ChestOverlayS2C(
			ChestOverlayS2C.OP_ADD, "minecraft:entity/chest/christmas", new long[] {1L, 2L})));
		out.put("entity_overlay", encode(EntityOverlayS2C.STREAM_CODEC, new EntityOverlayS2C(
			7, "mymod:textures/entity/glow.png")));
		out.put("camera_hint", encode(CameraHintS2C.STREAM_CODEC, new CameraHintS2C(
			"perspective", Map.of("mode", "third_person_back"))));
		out.put("keybind_defaults", encode(KeybindDefaultsS2C.STREAM_CODEC,
			new KeybindDefaultsS2C(List.of(new KeybindDefaultsS2C.Entry(0, "mymod:k1", 65)))));
		out.put("action_menus", encode(ActionMenusS2C.STREAM_CODEC, new ActionMenusS2C(
			List.of(new ActionMenusS2C.Menu("mymod:m1", "Menu", "key.keyboard.j",
				List.of(new ActionMenusS2C.Button("minecraft:stone", "Home", "home", "")))))));
		out.put("add_to_menu", encode(AddToMenuS2C.STREAM_CODEC, new AddToMenuS2C("home")));
		out.put("client_mod_toggle", encode(ClientModToggleS2C.STREAM_CODEC,
			new ClientModToggleS2C("mymod.jar")));
		out.put("client_mod_files", encode(ClientModFilesC2S.STREAM_CODEC, new ClientModFilesC2S(
			List.of(new ClientModFilesC2S.Entry("mymod", "My Mod", "1.0", "mymod.jar", true)))));
		out.put("not_understood", encode(NotUnderstoodC2S.STREAM_CODEC,
			new NotUnderstoodC2S("component_type", "mymod:not_invented_yet")));
		return out;
	}

	/**
	 * Every channel this mod defines, found rather than listed.
	 *
	 * <p>Walks the mod's own class files for the payload types and reads the channel name off
	 * each one. A list written by hand is a list that stops being true the first time somebody
	 * adds a payload and forgets this file, which is the one failure a freeze cannot afford.
	 */
	private static java.util.Set<String> allChannels() {
		java.util.Set<String> out = new java.util.TreeSet<>();
		var mod = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("pandorical");
		if (mod.isEmpty()) return out;
		for (java.nio.file.Path root : mod.get().getRootPaths()) {
			try (var files = java.nio.file.Files.walk(root)) {
				for (java.nio.file.Path file : files.toList()) {
					String name = file.getFileName() == null ? "" : file.getFileName().toString();
					// The house calls them all S2C or C2S, and loading only those keeps this from
					// running the static initialiser of every class in the mod.
					if (!name.endsWith("S2C.class") && !name.endsWith("C2S.class")) continue;
					String binary = root.relativize(file).toString()
						.replace(java.io.File.separatorChar, '.')
						.replace('/', '.');
					binary = binary.substring(0, binary.length() - ".class".length());
					channelOf(binary).ifPresent(out::add);
				}
			} catch (Exception e) {
				Pandorical.LOGGER.warn("could not walk {} for payload types", root, e);
			}
		}
		return out;
	}

	/** The channel name a payload class declares, if it is one. */
	private static Optional<String> channelOf(String binaryName) {
		try {
			Class<?> type = Class.forName(binaryName, false, ProtocolFrozen.class.getClassLoader());
			if (!CustomPacketPayload.class.isAssignableFrom(type)) return Optional.empty();
			Class<?> loaded = Class.forName(binaryName, true, ProtocolFrozen.class.getClassLoader());
			Object id = loaded.getField("TYPE").get(null);
			return Optional.of(((CustomPacketPayload.Type<?>) id).id().getPath());
		} catch (ReflectiveOperationException | RuntimeException e) {
			return Optional.empty();
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		Map<String, byte[]> now = samples();
		Map<String, String> recorded = readRecorded();

		List<String> wrong = new ArrayList<>();
		StringBuilder actual = new StringBuilder();
		for (Map.Entry<String, byte[]> entry : now.entrySet()) {
			String hex = hex(entry.getValue());
			actual.append(entry.getKey()).append(' ').append(hex).append('\n');
			String was = recorded.get(entry.getKey());
			if (was == null) {
				wrong.add(entry.getKey() + " is not recorded yet");
			} else if (!was.equals(hex)) {
				wrong.add(entry.getKey() + " changed shape:\n      was " + was + "\n      now " + hex);
			}
		}
		for (String gone : recorded.keySet()) {
			if (!now.containsKey(gone)) wrong.add(gone + " is recorded but no longer encoded here");
		}
		for (String channel : allChannels()) {
			if (!now.containsKey(channel) && !NOT_YET_FROZEN.contains(channel)) {
				wrong.add(channel + " is a channel with no sample: add one to samples(), or name it"
					+ " in NOT_YET_FROZEN with a reason, so the gap is a decision and not an oversight");
			}
		}

		if (!wrong.isEmpty()) {
			// Logged rather than written to a file: the game's working directory is wiped between runs.
			for (String line : actual.toString().split("\n")) {
				Pandorical.LOGGER.error("PROTOCOL-BYTES {}", line);
			}
			throw new AssertionError("The protocol this build speaks does not match what is recorded,"
				+ " and a layout already in players' hands changing shape"
				+ " disconnects every client running protocol v" + Pandorical.PROTOCOL_VERSION + ":\n  "
				+ String.join("\n  ", wrong)
				+ "\nGive the new shape its own channel name and leave the old one alone. What this build"
				+ " encodes is in the log, on the PROTOCOL-BYTES lines, ready to record if the change"
				+ " was meant and the protocol version moved with it.");
		}
	}

	private static <T> byte[] encode(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		try {
			codec.encode(buf, value);
			byte[] bytes = new byte[buf.readableBytes()];
			buf.getBytes(buf.readerIndex(), bytes);
			return bytes;
		} finally {
			buf.release();
		}
	}

	private static Map<String, String> readRecorded() {
		Map<String, String> out = new LinkedHashMap<>();
		try (InputStream in = ProtocolFrozen.class.getResourceAsStream("/protocol/v" + Pandorical.PROTOCOL_VERSION + ".txt")) {
			if (in == null) return out;
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				int space = line.indexOf(' ');
				// A payload with no fields encodes to nothing, so its line is a name on its own.
				if (space < 0) out.put(line, "");
				else out.put(line.substring(0, space), line.substring(space + 1).trim());
			}
		} catch (IOException e) {
			throw new AssertionError("could not read the recorded protocol bytes", e);
		}
		return out;
	}

	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) out.append("%02x".formatted(b));
		return out.toString();
	}
}
