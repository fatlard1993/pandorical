package justfatlard.pandorical.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import justfatlard.pandorical.api.NotUnderstood;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.client.entitymodel.ClientEntityModels;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A server gives an entity a model and a Pandorical client draws it: part transforms on the
 * entity's own model, geometry in place of it, a texture, and a draw scale apart from the hitbox.
 * And what it cannot draw, it leaves alone and says so.
 *
 * <p>The models are this test mod's own content, in {@code assets/pandorical-gametest/pandorical/entity_models}.
 */
public final class EntityModels implements FabricClientGameTest {

	private static final Identifier BIG_HEAD = Identifier.fromNamespaceAndPath("pandorical-gametest", "big_head");
	private static final Identifier CHIMNEY = Identifier.fromNamespaceAndPath("pandorical-gametest", "chimney");
	private static final Identifier HEADLESS = Identifier.fromNamespaceAndPath("pandorical-gametest", "headless");
	private static final Identifier MISSING = Identifier.fromNamespaceAndPath("pandorical-gametest", "not_shipped");
	private static final Identifier HUSK = Identifier.withDefaultNamespace("textures/entity/zombie/husk.png");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isContentReady(connection.getServerPlayer()));
			server.runCommand("difficulty normal");
			Pictures.stage(context, server, 18000);
			server.runCommand("effect give @a minecraft:night_vision infinite 0 true");

			List<String> reported = new CopyOnWriteArrayList<>();
			server.runOnServer(s -> PandoricalApi.onNotUnderstood((player, kind, value) -> reported.add(kind + " " + value)));

			BlockPos base = server.computeOnServer(s -> Pictures.ground(s.overworld(),
				connection.getServerPlayer().getBlockX(), connection.getServerPlayer().getBlockZ()));

			int[] ids = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				Mob zombie = place(level, EntityTypes.ZOMBIE, base, -1.5, 0);
				PandoricalApi.entityModels().set(zombie, BIG_HEAD, null, 0.8F);
				Mob skeleton = place(level, EntityTypes.SKELETON, base, 1.5, 0);
				skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
				PandoricalApi.entityModels().set(skeleton, CHIMNEY);
				Mob plain = place(level, EntityTypes.ZOMBIE, base, 0, -6);
				Mob headless = place(level, EntityTypes.STRAY, base, 3, -6);
				PandoricalApi.entityModels().set(headless, HEADLESS);
				Mob missing = place(level, EntityTypes.HUSK, base, -3, -6);
				PandoricalApi.entityModels().set(missing, MISSING);
				return new int[] {zombie.getId(), skeleton.getId(), plain.getId(), headless.getId(), missing.getId()};
			});
			int zombie = ids[0], skeleton = ids[1], plain = ids[2], headless = ids[3], missing = ids[4];
			check(server.computeOnServer(s -> CHIMNEY.equals(PandoricalApi.entityModels().get((LivingEntity) s.overworld().getEntity(skeleton)))),
				"the server does not say what the skeleton was given");
			context.waitFor(client -> ClientEntityModels.assigned(zombie) != null && ClientEntityModels.assigned(skeleton) != null
				&& ClientEntityModels.assigned(headless) != null && ClientEntityModels.assigned(missing) != null
				&& client.level.getEntity(plain) != null);

			Pictures.look(server, Vec3.atBottomCenterOf(base).add(0, 0.6, 5), Vec3.atBottomCenterOf(base).add(0, 1.1, 0));
			System.out.println("[pandorical test] picture " + Pictures.shoot(context, connection, "entity-models").toAbsolutePath());

			String drawn = context.computeOnClient(client -> {
				List<String> wrong = new ArrayList<>();

				// Transforms: the zombie's own model, animated as ever, its head half again as big;
				// its texture the husk's; and drawn at 0.8 of its size, its hitbox untouched.
				LivingEntityRenderState big = state(client, zombie);
				LivingEntityRenderState normal = state(client, plain);
				ClientEntityModels.Drawn bigDrawn = ClientEntityModels.drawn(big);
				if (bigDrawn == null) wrong.add("the zombie carries no model");
				else if (!HUSK.equals(bigDrawn.texture())) wrong.add("the zombie's texture is " + bigDrawn.texture());
				if (big.scale != normal.scale * 0.8F) wrong.add("the zombie is drawn at " + big.scale + ", not 0.8 of " + normal.scale);
				EntityModel<?> zombieModel = model(client, zombie);
				posed(zombieModel, big);
				float bigHead = zombieModel.root().getChild("head").xScale;
				posed(zombieModel, normal);
				float normalHead = zombieModel.root().getChild("head").xScale;
				if (bigHead != normalHead * 1.5F) wrong.add("the big head is " + bigHead + " against " + normalHead);
				if (client.level.getEntity(zombie).getBbHeight() != client.level.getEntity(plain).getBbHeight()) {
					wrong.add("the draw scale moved the hitbox");
				}

				// Geometry: built into the skeleton's own model class, so it animates as a skeleton,
				// with the extra block on its head; the renderer's own model given back afterwards.
				ClientEntityModels.Drawn chimney = ClientEntityModels.drawn(state(client, skeleton));
				if (chimney == null || chimney.model() == null) wrong.add("the skeleton has no new geometry");
				else {
					if (chimney.model().getClass() != SkeletonModel.class) wrong.add("the geometry is a " + chimney.model().getClass().getSimpleName());
					int cubes = countCubes(chimney.model().root().getChild("head"));
					if (cubes != 2) wrong.add("the new head has " + cubes + " blocks");
					if (model(client, skeleton) == chimney.model()) wrong.add("the renderer kept the stand-in after drawing");
				}

				// What it cannot draw is drawn as it was.
				if (ClientEntityModels.drawn(state(client, headless)) != null) wrong.add("geometry with no head was drawn on a stray");
				if (ClientEntityModels.drawn(state(client, missing)) != null) wrong.add("a model nobody shipped was drawn");
				return String.join("; ", wrong);
			});
			check(drawn.isEmpty(), drawn);
			server.waitFor(s -> reported.contains(NotUnderstood.ENTITY_MODEL + " " + MISSING)
				&& reported.stream().anyMatch(r -> r.startsWith(NotUnderstood.ENTITY_MODEL + " " + HEADLESS)));

			// Cleared, the zombie is drawn as any other.
			server.runOnServer(s -> PandoricalApi.entityModels().clear((LivingEntity) s.overworld().getEntity(zombie)));
			context.waitFor(client -> ClientEntityModels.assigned(zombie) == null);
			String cleared = context.computeOnClient(client -> {
				LivingEntityRenderState state = state(client, zombie);
				return ClientEntityModels.drawn(state) != null || state.scale != state(client, plain).scale ? "still drawn with it" : "";
			});
			check(cleared.isEmpty(), "clearing: " + cleared);
		}
		System.out.println("[pandorical test] PASSED EntityModels");
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void posed(EntityModel model, LivingEntityRenderState state) {
		model.setupAnim(state);
		ClientEntityModels.afterPose(model, state);
	}

	private static int countCubes(net.minecraft.client.model.geom.ModelPart part) {
		int[] n = {0};
		part.visit(new com.mojang.blaze3d.vertex.PoseStack(), (pose, path, index, cube) -> {
			if (path.isEmpty()) n[0]++;
		});
		return n[0];
	}

	private static EntityModel<?> model(Minecraft client, int id) {
		return ((LivingEntityRenderer<?, ?, ?>) client.getEntityRenderDispatcher().getRenderer(client.level.getEntity(id))).getModel();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static LivingEntityRenderState state(Minecraft client, int id) {
		Entity entity = client.level.getEntity(id);
		EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(entity);
		return (LivingEntityRenderState) renderer.createRenderState(entity, 1F);
	}

	private static <T extends Mob> T place(ServerLevel level, EntityType<T> type, BlockPos base, double dx, double dz) {
		T mob = type.create(level, EntitySpawnReason.COMMAND);
		mob.snapTo(base.getX() + 0.5 + dx, base.getY(), base.getZ() + 0.5 + dz, 0F, 0F);
		mob.setYHeadRot(0F);
		mob.setYBodyRot(0F);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
