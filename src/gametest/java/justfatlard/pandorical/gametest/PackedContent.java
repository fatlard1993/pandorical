package justfatlard.pandorical.gametest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import justfatlard.pandorical.protocol.BlockShapeTable;
import justfatlard.pandorical.protocol.SyncContentConfigS2C;
import justfatlard.pandorical.protocol.SyncContentConfigV2S2C;
import justfatlard.pandorical.protocol.SyncContentS2C;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.codec.StreamCodec;

import java.util.Arrays;
import java.util.List;

/**
 * Block content reads back as it was sent, in either layout: the per-state one older clients read
 * and the packed one, which must name a repeated shape table rather than resend it and still hand
 * the client every state's shapes.
 */
public final class PackedContent implements FabricClientGameTest {

	private static final BlockShapeTable.Shape POST =
		new BlockShapeTable.Shape(new float[] {0.375F, 0F, 0.375F, 0.625F, 1F, 0.625F});
	private static final BlockShapeTable.Shape ARM =
		new BlockShapeTable.Shape(new float[] {0.375F, 0F, 0.375F, 0.625F, 1F, 0.625F, 0.375F, 0F, 0F, 0.625F, 1F, 0.5F});

	@Override
	public void runTest(ClientGameTestContext context) {
		BlockShapeTable panel = new BlockShapeTable.Builder()
			.add(POST, POST).add(ARM, ARM).add(ARM, BlockShapeTable.Shape.EMPTY).add(POST, POST).build();
		assertThat(panel.distinct().size() == 3, "a table should hold each distinct shape once, not "
			+ panel.distinct().size());

		List<SyncContentS2C.BlockEntry> blocks = List.of(
			entry("mymod:panel", List.of(400, 401, 402, 403), panel, new byte[] {0, 0, 0, 0}),
			entry("mymod:red_panel", List.of(404, 405, 406, 407), sameAs(panel), new byte[] {0, 0, 0, 0}),
			entry("mymod:lamp", List.of(9, 7, 8, 30), BlockShapeTable.NONE, new byte[] {15, 0, 7, 0}),
			entry("mymod:empty", List.of(), BlockShapeTable.NONE, new byte[0]));

		// Per state, as older clients read it.
		for (SyncContentS2C.BlockEntry sent : blocks) {
			SyncContentS2C.BlockEntry read = roundTrip(SyncContentS2C.BlockEntry.STREAM_CODEC, sent);
			assertSame(sent, read, "the per-state layout");
		}

		// Packed.
		var packed = new SyncContentConfigV2S2C(new SyncContentConfigS2C(blocks, List.of(), 0, 1, 0,
			List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false));
		List<SyncContentS2C.BlockEntry> read = roundTrip(SyncContentConfigV2S2C.STREAM_CODEC, packed).content().blocks();
		assertThat(read.size() == blocks.size(), "the packed layout lost blocks");
		for (int i = 0; i < blocks.size(); i++) assertSame(blocks.get(i), read.get(i), "the packed layout");
		assertThat(read.get(0).shapes() == read.get(1).shapes(),
			"a repeated shape table should arrive as the one table named twice");

		int perState = size(SyncContentS2C.BlockEntry.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()), blocks);
		int packedSize = size(SyncContentConfigV2S2C.STREAM_CODEC, packed);
		assertThat(packedSize < perState, "packed should be smaller than per state: " + packedSize + " vs " + perState);
	}

	/** Equal but not the same object, as two blocks' tables are when each is read from its own block. */
	private static BlockShapeTable sameAs(BlockShapeTable table) {
		return new BlockShapeTable(List.copyOf(table.distinct()), table.outline().clone(), table.collision().clone());
	}

	private static SyncContentS2C.BlockEntry entry(String id, List<Integer> stateIds, BlockShapeTable shapes, byte[] light) {
		return new SyncContentS2C.BlockEntry(id, "minecraft:white_wool", List.of("solid:b"), "mymod:block/x",
			stateIds, shapes, light, false, true, 1.5F, 0);
	}

	private static void assertSame(SyncContentS2C.BlockEntry sent, SyncContentS2C.BlockEntry read, String layout) {
		String where = layout + ", " + sent.id() + ": ";
		assertThat(sent.id().equals(read.id()) && sent.baseBlockId().equals(read.baseBlockId())
			&& sent.stateProperties().equals(read.stateProperties()) && sent.modelId().equals(read.modelId()),
			where + "names changed");
		assertThat(sent.stateIds().equals(read.stateIds()), where + "state ids " + sent.stateIds() + " read as " + read.stateIds());
		assertThat(sent.shapes().equals(read.shapes()), where + "shapes changed");
		assertThat(Arrays.equals(sent.lightData(), read.lightData()), where + "light changed");
		assertThat(sent.climbable() == read.climbable() && sent.interactive() == read.interactive()
			&& sent.destroyTime() == read.destroyTime() && sent.requiresCorrectTool() == read.requiresCorrectTool(),
			where + "flags changed");
	}

	private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		try {
			codec.encode(buf, value);
			T read = codec.decode(buf);
			assertThat(buf.readableBytes() == 0, buf.readableBytes() + " bytes left over after reading");
			return read;
		} finally {
			buf.release();
		}
	}

	private static <T> int size(StreamCodec<ByteBuf, T> codec, T value) {
		return ProtocolFrozen.encode(codec, value).length;
	}

	private static void assertThat(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
