package justfatlard.pandorical.gametest;

import justfatlard.pandorical.form.Forms;
import justfatlard.pandorical.form.SimpleForm;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A form's answer is the player's own upload, so it is only believed for what the form offered.
 * Each forgery here goes in through the game's own packet handling, the way a modified client
 * would send it, and must reach no handler. An honest answer goes the same way and must arrive
 * with every value read as its field's type.
 */
public final class FormsRefuseForgeries implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, "pandorical", session -> {
			AtomicReference<String> complaint = new AtomicReference<>();
			session.onServer(server -> {
				try {
					honest(session.player());
					forgeries(session.player());
					buttons(session.player());
					check(!Forms.handle(session.player(), Identifier.fromNamespaceAndPath("minecraft", "elsewhere"), Optional.empty()),
						"a click that isn't Pandorical's was taken instead of passed on");
				} catch (AssertionError e) {
					complaint.set(e.getMessage());
				} finally {
					session.player().connection.send(ClientboundClearDialogPacket.INSTANCE);
				}
			});
			check(complaint.get() == null, complaint.get());
		});
	}

	private static SimpleForm fields(List<SimpleForm.Answer> answers) {
		return SimpleForm.titled("Forgery test")
			.toggle("toggle", "A toggle", false)
			.choice("choice", "A choice", Map.of("a", "A", "b", "B"), "a")
			.number("number", "A number", 0, 10, 1, 5)
			.text("text", "Some text", "", 5)
			.onAnswer(answers::add);
	}

	private static CompoundTag submitted(Consumer<CompoundTag> change) {
		CompoundTag tag = new CompoundTag();
		tag.putString("button", SimpleForm.SUBMIT);
		tag.putByte("f0", (byte) 1);
		tag.putString("f1", "b");
		tag.putFloat("f2", 7);
		tag.putString("f3", "hey");
		change.accept(tag);
		return tag;
	}

	private static void send(ServerPlayer player, Identifier id, Tag payload) {
		player.connection.handleCustomClickAction(new ServerboundCustomClickActionPacket(id, Optional.ofNullable(payload)));
	}

	private static void honest(ServerPlayer player) {
		List<SimpleForm.Answer> answers = new ArrayList<>();
		Identifier id = fields(answers).open(player);
		send(player, id, submitted(tag -> {}));
		check(answers.size() == 1, "an honest answer reached " + answers.size() + " handlers, not one");
		Map<String, String> values = answers.getFirst().values();
		check("true".equals(values.get("toggle")), "the toggle came back as " + values.get("toggle"));
		check("b".equals(values.get("choice")), "the choice came back as " + values.get("choice"));
		check("7".equals(values.get("number")), "the number came back as " + values.get("number"));
		check("hey".equals(values.get("text")), "the text came back as " + values.get("text"));

		send(player, id, submitted(tag -> {}));
		check(answers.size() == 1, "a form answered twice: the same id was believed again");
	}

	private static void forgeries(ServerPlayer player) {
		refused(player, "a button the form never had", tag -> tag.putString("button", "op_me"));
		refused(player, "a choice that wasn't offered", tag -> tag.putString("f1", "z"));
		refused(player, "a number past the field's end", tag -> tag.putFloat("f2", 99));
		refused(player, "a number below the field's start", tag -> tag.putFloat("f2", -1));
		refused(player, "a number that isn't one", tag -> tag.putFloat("f2", Float.NaN));
		refused(player, "text longer than the field takes", tag -> tag.putString("f3", "far too long"));
		refused(player, "a toggle sent as words that aren't true or false", tag -> tag.putString("f0", "maybe"));
		refused(player, "a field left out", tag -> tag.remove("f2"));

		List<SimpleForm.Answer> answers = new ArrayList<>();
		Identifier id = fields(answers).open(player);
		send(player, id, StringTag.valueOf("submit"));
		check(answers.isEmpty(), "a payload that isn't a compound was taken as an answer");
		send(player, id, null);
		check(answers.isEmpty(), "an empty payload was taken as an answer");

		Identifier never = Identifier.fromNamespaceAndPath("pandorical", "form/999999999");
		check(Forms.handle(player, never, Optional.of(submitted(tag -> {}))),
			"an answer to a form never sent was passed on instead of dropped");
	}

	private static void refused(ServerPlayer player, String what, Consumer<CompoundTag> forge) {
		List<SimpleForm.Answer> answers = new ArrayList<>();
		Identifier id = fields(answers).open(player);
		send(player, id, submitted(forge));
		check(answers.isEmpty(), what + " was believed: " + (answers.isEmpty() ? "" : answers.getFirst().values()));
	}

	private static void buttons(ServerPlayer player) {
		List<SimpleForm.Answer> answers = new ArrayList<>();
		Identifier id = SimpleForm.titled("Buttons").button("go", "Go").onAnswer(answers::add).open(player);
		CompoundTag submit = new CompoundTag();
		submit.putString("button", SimpleForm.SUBMIT);
		send(player, id, submit);
		check(answers.isEmpty(), "a buttons-only form took a submit it never offered");

		Identifier again = SimpleForm.titled("Buttons").button("go", "Go").onAnswer(answers::add).open(player);
		CompoundTag go = new CompoundTag();
		go.putString("button", "go");
		send(player, again, go);
		check(answers.size() == 1 && answers.getFirst().button().equals("go") && answers.getFirst().values().isEmpty(),
			"pressing a button the form offered didn't arrive as that button: " + answers);
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
