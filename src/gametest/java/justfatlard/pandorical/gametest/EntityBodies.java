package justfatlard.pandorical.gametest;

import java.util.ArrayList;
import java.util.List;

import justfatlard.pandorical.client.entitykind.AgeableBodyMob;
import justfatlard.pandorical.client.entitykind.BodyRenderState;
import justfatlard.pandorical.client.entitykind.BodyRenderer;
import justfatlard.pandorical.client.entitykind.ClientEntityKinds;
import justfatlard.pandorical.entitykind.EntityKinds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * An entity with no vanilla mob under it: a client without its mod builds it as a generic mob that
 * holds every value the server syncs, the mod's own included, and draws it from its model file,
 * animated as the file says. A vanilla client is shown it only as a fallback that can read it.
 *
 * <p>This world is singleplayer, so the blob's own class is on the client too; the generic mob a
 * remote client builds is made here directly, for the blob's type, and handed the server blob's
 * values the way a packet would.
 */
public final class EntityBodies implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");

			// Told: the client knows the blob's body.
			context.waitFor(client -> ClientEntityKinds.bodyOf(GateContent.BLOB) != null);

			int[] made = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				ServerPlayer player = connection.getServerPlayer();
				Blob blob = GateContent.BLOB.create(level, EntitySpawnReason.COMMAND);
				blob.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0F, 0F);
				blob.setNoAi(true);
				blob.setBaby(true);
				blob.getEntityData().set(Blob.MOOD, 42);
				level.addFreshEntity(blob);
				return new int[] {blob.getId()};
			});
			int blob = made[0];

			// The server pairs a body with all its values, defaults too: the generic class knows none of them.
			String paired = server.computeOnServer(s -> {
				Blob b = (Blob) s.overworld().getEntity(blob);
				List<SynchedEntityData.DataValue<?>> values = b.getEntityData().getNonDefaultValues();
				int all = ((justfatlard.pandorical.mixin.SynchedEntityDataAccessor) b.getEntityData()).pandorical$itemsById().length;
				return values != null && values.size() == all ? "" : "paired with " + (values == null ? 0 : values.size()) + " of " + all;
			});
			check(paired.isEmpty(), paired);

			// Who is shown it: a client told the bodies, yes; told only the kinds on a base, no, because
			// a cow cannot read a blob's own values and so is no fallback for it.
			String shown = server.computeOnServer(s -> {
				List<String> wrong = new ArrayList<>();
				ServerPlayer player = connection.getServerPlayer();
				Entity b = s.overworld().getEntity(blob);
				if (!EntityKinds.mayShow(b, player)) wrong.add("a client told the bodies is not shown one");
				EntityKinds.understood(player.getUUID(), true, false);
				try {
					if (EntityKinds.mayShow(b, player)) wrong.add("a client that cannot build bodies was shown one, as a cow that cannot read it");
				} finally {
					EntityKinds.understood(player.getUUID(), true, true);
				}
				var packet = EntityKinds.forPlayer(new ClientboundAddEntityPacket(b.getId(), b.getUUID(),
					b.getX(), b.getY(), b.getZ(), 0F, 0F, b.getType(), 0, Vec3.ZERO, 0), player);
				if (packet.getType() != GateContent.BLOB) wrong.add("a client told the bodies was sent " + packet.getType());
				return String.join("; ", wrong);
			});
			check(shown.isEmpty(), shown);

			// The generic mob a remote client builds: the blob's layout, every value in its place.
			List<SynchedEntityData.DataValue<?>> values = server.computeOnServer(s ->
				EntityKinds.allValues(((Blob) s.overworld().getEntity(blob)).getEntityData()));
			String built = context.computeOnClient(client -> {
				List<String> wrong = new ArrayList<>();
				ClientEntityKinds.Body body = ClientEntityKinds.bodyOf(GateContent.BLOB);
				Mob mob = ClientEntityKinds.create(body, GateContent.BLOB, client.level);
				if (!(mob instanceof AgeableBodyMob)) {
					wrong.add("the blob was built as " + (mob == null ? "nothing" : mob.getClass().getSimpleName()));
					return String.join("; ", wrong);
				}
				mob.getEntityData().assignValues(values);
				if (!mob.isBaby()) wrong.add("the generic blob does not know it is a baby");
				int mood = mob.getEntityData().get(new EntityDataAccessor<>(Blob.MOOD.id(), EntityDataSerializers.INT));
				if (mood != 42) wrong.add("the blob's own value read " + mood);

				// Told a layout its class does not lead with - its first value, every entity's flags, as a
				// number - it is not built at all rather than read wrong.
				List<net.minecraft.network.syncher.EntityDataSerializer<?>> skewed = new ArrayList<>(body.serializers());
				skewed.set(0, EntityDataSerializers.INT);
				ClientEntityKinds.Body misread = new ClientEntityKinds.Body(body.tier(), skewed,
					body.width(), body.height(), body.eyeHeight(), body.category(), body.trackingRange(),
					body.updateInterval(), body.fireImmune(), body.model(), body.texture(), body.drawScale());
				if (ClientEntityKinds.create(misread, GateContent.BLOB, client.level) != null) {
					wrong.add("a body whose layout its class does not match was built anyway");
				}
				return String.join("; ", wrong);
			});
			check(built.isEmpty(), built);

			// Drawn from its own model, small as a baby, its tail swinging as it moves.
			context.waitFor(client -> client.level.getEntity(blob) != null);
			String drawn = context.computeOnClient(client -> {
				List<String> wrong = new ArrayList<>();
				Entity entity = client.level.getEntity(blob);
				@SuppressWarnings({"unchecked", "rawtypes"})
				EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(entity);
				if (!(renderer instanceof BodyRenderer bodies)) {
					wrong.add("the blob is drawn by " + (renderer == null ? "nothing" : renderer.getClass().getSimpleName()));
					return String.join("; ", wrong);
				}
				@SuppressWarnings("unchecked")
				BodyRenderState state = (BodyRenderState) renderer.createRenderState(entity, 1F);
				if (state.texture == null || !state.texture.getPath().contains("slime")) wrong.add("the blob wears " + state.texture);
				if (state.scale >= 1F) wrong.add("a baby blob is drawn at " + state.scale);
				var model = bodies.getModel();
				if (!model.root().hasChild("body")) {
					wrong.add("the blob's geometry was not built");
					return String.join("; ", wrong);
				}
				var tail = model.root().getChild("body").getChild("tail");
				state.walkAnimationSpeed = 1F;
				float[] swings = new float[4];
				for (int i = 0; i < swings.length; i++) {
					state.walkAnimationPos = i * 2.5F;
					Models.pose(model, state);
					swings[i] = tail.yRot;
				}
				float least = swings[0], most = swings[0];
				for (float swing : swings) {
					least = Math.min(least, swing);
					most = Math.max(most, swing);
				}
				if (most - least < 0.3F) wrong.add("the tail barely moves side to side as it swims: " + java.util.Arrays.toString(swings));
				if (Math.abs(tail.xRot) > 0.01F) wrong.add("the tail flexes up and down: " + tail.xRot);
				state.walkAnimationSpeed = 0F;
				state.yRot = 30F;
				Models.pose(model, state);
				if (Math.abs(model.root().getChild("body").yRot - (float) Math.toRadians(30)) > 0.01F) {
					wrong.add("the body does not turn where the blob looks");
				}
				return String.join("; ", wrong);
			});
			check(drawn.isEmpty(), drawn);
		}
		System.out.println("[pandorical test] PASSED EntityBodies");
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
