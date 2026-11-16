package justfatlard.pandorical.gametest;

import justfatlard.pandorical.client.item.CellsItemRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * An item drawn as the build its data holds, each check paired with the way it could go wrong:
 * the cells hidden inside a build left out and the ones on its face kept, a build shorter than it
 * is wide read at its own height, data of the wrong length drawing nothing rather than garbage;
 * and the model under {@code pandorical/items/} loaded in place of the plain one, its fallback
 * taken by a stack with no data. Paper is the stand-in, given such a model by this test's assets.
 */
public final class CellsItems implements FabricClientGameTest {
	private static final String KEY = "build";

	@Override
	public void runTest(ClientGameTestContext context) {
		reading();
		if (!Smoke.full()) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();

			ItemStack built = withCells(3, 3, cube(3, 3));
			context.runOnClient(client -> {
				float drawn = depth(client, built);
				if (drawn < 0.99F) throw new AssertionError("a stack with cells drew " + drawn + " deep, not the block deep the cells model draws: the pandorical/items model was not loaded in place of the plain one");
				float plain = depth(client, new ItemStack(Items.PAPER));
				if (plain > 0.5F) throw new AssertionError("a stack without data drew " + plain + " deep: the condition's fallback, flat paper, was not taken");
			});

			world.getServer().runOnServer(s -> {
				var pack = connection.getServerPlayer().getInventory();
				pack.setItem(0, built);
				pack.setItem(1, withCells(4, 2, slab()));
				pack.setItem(2, new ItemStack(Items.PAPER));
			});
			context.waitTicks(5);
			context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(context.computeOnClient(client -> client.player)));
			context.waitTicks(5);
			context.takeScreenshot("cells-items");
		}
	}

	private static void reading() {
		check(CellsItemRenderer.read(cellsTag(2, 2, cube(2, 2))), 8, "a 2-cube of stone shows all 8 of its cells");
		check(CellsItemRenderer.read(cellsTag(3, 3, cube(3, 3))), 26, "a 3-cube of stone hides only its middle cell");
		check(CellsItemRenderer.read(cellsTag(4, 2, slab())), 4 * 4 * 2, "a 4-wide, 2-high slab shows every cell, read at its own height");
		if (CellsItemRenderer.read(cellsTag(3, 3, new byte[5])) != null) throw new AssertionError("cells of the wrong length drew something");
		if (CellsItemRenderer.read(null) != null) throw new AssertionError("no data drew something");
		if (CellsItemRenderer.read(cellsTag(2, 2, new byte[8])) != null) throw new AssertionError("a build of nothing but air drew something");
	}

	private static void check(CellsItemRenderer.Cells cells, int showing, String what) {
		if (cells == null) throw new AssertionError(what + ", but it read as nothing");
		if (cells.showing().size() != showing) throw new AssertionError(what + ", but " + cells.showing().size() + " showed");
	}

	private static byte[] cube(int n, int height) {
		byte[] cells = new byte[n * height * n];
		java.util.Arrays.fill(cells, (byte) 1);
		return cells;
	}

	/** A 4-wide slab, 2 high: stone below, glass above, so the picture shows both layers. */
	private static byte[] slab() {
		byte[] cells = new byte[4 * 2 * 4];
		for (int i = 0; i < cells.length; i++) cells[i] = (byte) (i < 16 ? 1 : 2);
		return cells;
	}

	private static Tag cellsTag(int n, int height, byte[] cells) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("n", n);
		if (height != n) tag.putInt("height", height);
		ListTag palette = new ListTag();
		for (BlockState state : List.of(Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState())) {
			palette.add(BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
		}
		tag.put("palette", palette);
		tag.putByteArray("cells", cells);
		return tag;
	}

	private static ItemStack withCells(int n, int height, byte[] cells) {
		CompoundTag root = new CompoundTag();
		root.put(KEY, cellsTag(n, height, cells));
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		return stack;
	}

	/** How deep the stack's model is front to back: a block deep for cells, a sliver for flat paper. */
	private static float depth(net.minecraft.client.Minecraft client, ItemStack stack) {
		ItemStackRenderState state = new ItemStackRenderState();
		client.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, client.level, null, 0);
		float[] z = {Float.MAX_VALUE, -Float.MAX_VALUE};
		state.visitExtents(v -> {
			z[0] = Math.min(z[0], v.z());
			z[1] = Math.max(z[1], v.z());
		});
		return z[1] - z[0];
	}
}
