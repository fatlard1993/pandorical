package justfatlard.pandorical.gametest;

import com.mojang.authlib.GameProfile;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.Trust;
import justfatlard.pandorical.form.Forms;
import justfatlard.pandorical.form.SimpleForm;
import justfatlard.pandorical.settings.SettingsRegistry;
import justfatlard.pandorical.trust.TrustRules;
import justfatlard.pandorical.trust.TrustScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What only an op may change stays theirs through the forms a player without Pandorical gets: a
 * player who isn't one is never shown them, and one who stops being one between seeing a form and
 * answering it has the answer thrown away. Each refusal sits beside the same answer from an op
 * going through, so a check that refused everything would fail here too.
 *
 * <p>A fake player stands in for that player: it counts as a client without Pandorical, its
 * connection drops what is sent to it, and its op status can be taken away, which the test world's
 * owner's cannot.</p>
 */
public final class OpsOnlyFormsHold implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			AtomicReference<String> complaint = new AtomicReference<>();
			session.onServer(server -> {
				FakePlayer someone = FakePlayer.get(session.player().level(), new GameProfile(UUID.randomUUID(), "Someone"));
				NameAndId who = new NameAndId(someone.getGameProfile());
				try {
					trust(server, someone, who);
					settings(server, someone, who);
				} catch (AssertionError e) {
					complaint.set(e.getMessage());
				} finally {
					server.getPlayerList().deop(who);
				}
			});
			check(complaint.get() == null, complaint.get());
		});
	}

	private static void trust(MinecraftServer server, FakePlayer someone, NameAndId who) {
		server.getPlayerList().deop(who);
		Optional<Identifier> before = Forms.newest(someone);
		TrustScreen.openForm(someone);
		check(Forms.newest(someone).equals(before), "a player who isn't an op was shown the trust forms");

		boolean fire = TrustRules.byDefault(server.overworld(), Trust.FIRE);
		server.getPlayerList().op(who);
		check(SettingsRegistry.isOp(someone), "the fake player didn't become an op, so this test proves nothing");

		Identifier everyone = everyonePage(someone);
		server.getPlayerList().deop(who);
		Forms.handle(someone, everyone, Optional.of(defaults(server, Trust.FIRE)));
		check(TrustRules.byDefault(server.overworld(), Trust.FIRE) == fire,
			"an op who lost op before answering still changed everyone's fire default");

		server.getPlayerList().op(who);
		Forms.handle(someone, everyonePage(someone), Optional.of(defaults(server, Trust.FIRE)));
		check(TrustRules.byDefault(server.overworld(), Trust.FIRE) != fire, "an op's answer to the same page didn't change it");

		// And back, through the same form, so the world is left as it was found.
		Forms.handle(someone, everyonePage(someone), Optional.of(defaults(server, Trust.FIRE)));
		check(TrustRules.byDefault(server.overworld(), Trust.FIRE) == fire, "the fire default wasn't put back");
	}

	/** The everyone page, reached the way a player reaches it: the list first, then its button. */
	private static Identifier everyonePage(FakePlayer someone) {
		TrustScreen.openForm(someone);
		CompoundTag press = new CompoundTag();
		press.putString("button", "everyone");
		Forms.handle(someone, Forms.newest(someone).orElseThrow(), Optional.of(press));
		return Forms.newest(someone).orElseThrow();
	}

	/** Every rule as it is now, except {@code flipped}; toggles go by position, in Trust's order. */
	private static CompoundTag defaults(MinecraftServer server, Trust flipped) {
		CompoundTag tag = new CompoundTag();
		tag.putString("button", SimpleForm.SUBMIT);
		Trust[] all = Trust.values();
		for (int i = 0; i < all.length; i++) {
			boolean on = TrustRules.byDefault(server.overworld(), all[i]);
			tag.putByte("f" + i, (byte) ((all[i] == flipped) != on ? 1 : 0));
		}
		return tag;
	}

	private static void settings(MinecraftServer server, FakePlayer someone, NameAndId who) {
		SettingsRegistry.GroupImpl group = PandoricalApi.settingsImpl().groups().stream()
			.filter(g -> g.server && g.modId.equals("pandorical")).findFirst().orElse(null);
		check(group != null, "Pandorical has no server settings to test with");
		SettingsRegistry.SettingImpl<?> toggle = group.settings.stream()
			.filter(s -> !(s instanceof SettingsRegistry.ListImpl) && s.get(someone) instanceof Boolean)
			.findFirst().orElse(null);
		check(toggle != null, "Pandorical's server settings have no toggle to test with");
		Object was = toggle.get(someone);

		server.getPlayerList().deop(who);
		Optional<Identifier> before = Forms.newest(someone);
		run(server, someone, "pandorical settings");
		Optional<Identifier> shown = Forms.newest(someone);
		check(shown.equals(before) || !reaches(server, someone, shown.get(), group),
			"a player who isn't an op was shown Pandorical's server settings");

		server.getPlayerList().op(who);
		Identifier page = settingsPage(server, someone, group);
		server.getPlayerList().deop(who);
		Forms.handle(someone, page, Optional.of(answer(someone, group, toggle)));
		check(toggle.get(someone).equals(was), "an op who lost op before answering still changed " + toggle.id());

		server.getPlayerList().op(who);
		Forms.handle(someone, settingsPage(server, someone, group), Optional.of(answer(someone, group, toggle)));
		check(!toggle.get(someone).equals(was), "an op's answer to the same page didn't change " + toggle.id());
		Forms.handle(someone, settingsPage(server, someone, group), Optional.of(answer(someone, group, toggle)));
		check(toggle.get(someone).equals(was), toggle.id() + " wasn't put back");
	}

	/** Whether pressing through the newest form would land on the group's page: its button is there. */
	private static boolean reaches(MinecraftServer server, FakePlayer someone, Identifier form, SettingsRegistry.GroupImpl group) {
		CompoundTag press = new CompoundTag();
		press.putString("button", "server:" + group.modId);
		Forms.handle(someone, form, Optional.of(press));
		return !Forms.newest(someone).map(form::equals).orElse(true);
	}

	/** The group's own page, from the command, through the list of mods when there is one. */
	private static Identifier settingsPage(MinecraftServer server, FakePlayer someone, SettingsRegistry.GroupImpl group) {
		run(server, someone, "pandorical settings");
		Identifier first = Forms.newest(someone).orElseThrow(() -> new AssertionError("/pandorical settings showed an op no form"));
		CompoundTag press = new CompoundTag();
		press.putString("button", "server:" + group.modId);
		Forms.handle(someone, first, Optional.of(press));
		// With one group the command opened its page directly, and the press above spent it.
		return Forms.newest(someone).filter(id -> !id.equals(first)).orElseGet(() -> {
			run(server, someone, "pandorical settings");
			return Forms.newest(someone).orElseThrow();
		});
	}

	/** Every field the page shows, as it is now, with {@code flipped} turned over. */
	private static CompoundTag answer(ServerPlayer someone, SettingsRegistry.GroupImpl group, SettingsRegistry.SettingImpl<?> flipped) {
		CompoundTag tag = new CompoundTag();
		tag.putString("button", SimpleForm.SUBMIT);
		int index = 0;
		for (SettingsRegistry.SettingImpl<?> setting : group.settings) {
			if (setting instanceof SettingsRegistry.ListImpl || !setting.visible(someone)) continue;
			String key = "f" + index++;
			Object value = setting.get(someone);
			switch (value) {
				case Boolean on -> tag.putByte(key, (byte) ((setting == flipped) != on ? 1 : 0));
				case Integer number -> tag.putFloat(key, number);
				default -> tag.putString(key, String.valueOf(value));
			}
		}
		return tag;
	}

	private static void run(MinecraftServer server, ServerPlayer player, String command) {
		server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
