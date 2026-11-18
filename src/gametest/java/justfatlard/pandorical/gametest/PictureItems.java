package justfatlard.pandorical.gametest;

import justfatlard.pandorical.client.item.PictureItemRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * An item drawn with the picture its data holds, each check paired with the way it could go
 * wrong: cells read at their palette colours, an index past the palette drawn magenta rather than
 * failing, data of the wrong length drawing nothing; and the model under
 * {@code pandorical/items_v2/} preferred over the one under {@code pandorical/items/}. An empty
 * map is the stand-in: this test's assets give it a cells model in the old folder, which would
 * draw a block deep, and a picture laid over the plain map in the new one.
 */
public final class PictureItems implements FabricClientGameTest {
	private static final String KEY = "picture";
	private static final int RED = 0xFFFF0000, BLUE = 0xFF0000FF;

	@Override
	public void runTest(ClientGameTestContext context) {
		reading();
		if (!Smoke.full()) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();

			ItemStack pictured = withPicture(pictureTag(2, 2, new int[] {RED, BLUE}, new byte[] {0, 1, 1, 0}));
			context.runOnClient(client -> {
				float[] z = depth(client, pictured);
				if (z[1] - z[0] > 0.5F) throw new AssertionError("a pictured map drew " + (z[1] - z[0]) + " deep: the pandorical/items model was loaded, not the pandorical/items_v2 one");
				// The sprite is a sixteenth deep; the picture lies a hair in front of it.
				if (z[1] - z[0] <= 1F / 16 + 0.0005F) throw new AssertionError("a pictured map drew nothing in front of its sprite: neither Pandorical model was loaded");
			});

			world.getServer().runOnServer(s -> {
				var pack = connection.getServerPlayer().getInventory();
				pack.setItem(0, pictured);
				pack.setItem(1, new ItemStack(Items.MAP));
			});
			context.waitTicks(5);
			context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(context.computeOnClient(client -> client.player)));
			context.waitTicks(5);
			context.takeScreenshot("picture-items");
			context.setScreen(() -> null);
		}
	}

	private static void reading() {
		PictureItemRenderer.Drawn drawn = PictureItemRenderer.read(pictureTag(2, 2, new int[] {RED, BLUE}, new byte[] {0, 1, 1, 0}));
		if (drawn == null) throw new AssertionError("a 2x2 picture read as nothing");
		if (drawn.color(0, 0) != RED || drawn.color(1, 0) != BLUE || drawn.color(0, 1) != BLUE)
			throw new AssertionError("a picture's cells did not read at their palette colours, row-major from the top left");
		PictureItemRenderer.Drawn past = PictureItemRenderer.read(pictureTag(1, 1, new int[] {RED}, new byte[] {5}));
		if (past == null || past.color(0, 0) != 0xFFFF00FF) throw new AssertionError("an index past the palette did not draw magenta");
		if (PictureItemRenderer.read(pictureTag(2, 2, new int[] {RED}, new byte[3])) != null) throw new AssertionError("cells of the wrong length drew something");
		if (PictureItemRenderer.read(pictureTag(PictureItemRenderer.LARGEST_SIDE + 1, 1, new int[] {RED}, new byte[PictureItemRenderer.LARGEST_SIDE + 1])) != null)
			throw new AssertionError("a picture wider than the largest side drew something");
		if (PictureItemRenderer.read(null) != null) throw new AssertionError("no data drew something");
	}

	private static Tag pictureTag(int columns, int rows, int[] palette, byte[] cells) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("columns", columns);
		tag.putInt("rows", rows);
		tag.putIntArray("palette", palette);
		tag.putByteArray("cells", cells);
		return tag;
	}

	private static ItemStack withPicture(Tag picture) {
		CompoundTag root = new CompoundTag();
		root.put(KEY, picture);
		ItemStack stack = new ItemStack(Items.MAP);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		return stack;
	}

	/** The nearest and farthest the stack's model reaches, front to back. */
	private static float[] depth(net.minecraft.client.Minecraft client, ItemStack stack) {
		ItemStackRenderState state = new ItemStackRenderState();
		client.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, client.level, null, 0);
		float[] z = {Float.MAX_VALUE, -Float.MAX_VALUE};
		state.visitExtents(v -> {
			z[0] = Math.min(z[0], v.z());
			z[1] = Math.max(z[1], v.z());
		});
		return z;
	}
}
