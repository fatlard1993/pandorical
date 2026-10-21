package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player would see through the portal nearest them: the blocks either side of the portal it
 * leads to, as far as a short look reaches. An empty one takes the view away.
 *
 * @param near   the sheet the player is near, in their own world
 * @param far    the sheet it leads to, in the far world
 * @param sky    the far world's colour where no block stands, as RGB
 * @param biome  the far side's biome, for grass and leaves; empty for the client's own default
 * @param blocks the far side, positions relative to {@code far}'s corner
 */
public record PortalViewS2C(Sheet near, Sheet far, int sky, String biome, List<Block> blocks) implements CustomPacketPayload {

	public static final PortalViewS2C NONE = new PortalViewS2C(Sheet.NONE, Sheet.NONE, 0, "", List.of());

	public static final Type<PortalViewS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "portal_view"));

	/**
	 * A portal's sheet of blocks: a rectangle standing on its lowest, most north-westerly block.
	 *
	 * @param alongX true when it spans east to west, as a portal lit facing north does
	 */
	public record Sheet(BlockPos corner, boolean alongX, int width, int height) {
		public static final Sheet NONE = new Sheet(BlockPos.ZERO, true, 0, 0);

		static final StreamCodec<ByteBuf, Sheet> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Sheet::corner,
			ByteBufCodecs.BOOL, Sheet::alongX,
			ByteBufCodecs.VAR_INT, Sheet::width,
			ByteBufCodecs.VAR_INT, Sheet::height,
			Sheet::new
		);
	}

	/**
	 * @param state a block state's id, as {@code Block.getId} gives it
	 * @param light the light reaching its open faces: block light in the high four bits, sky in the low
	 */
	public record Block(int dx, int dy, int dz, int state, int light) {}

	public boolean shown() {
		return near.width() > 0;
	}

	public static final StreamCodec<ByteBuf, PortalViewS2C> STREAM_CODEC = StreamCodec.of(
		(buf, view) -> {
			Sheet.STREAM_CODEC.encode(buf, view.near);
			Sheet.STREAM_CODEC.encode(buf, view.far);
			buf.writeInt(view.sky);
			ByteBufCodecs.STRING_UTF8.encode(buf, view.biome);
			VarInt.write(buf, view.blocks.size());
			for (Block block : view.blocks) {
				VarInt.write(buf, zigzag(block.dx));
				VarInt.write(buf, zigzag(block.dy));
				VarInt.write(buf, zigzag(block.dz));
				VarInt.write(buf, block.state);
				buf.writeByte(block.light);
			}
		},
		buf -> {
			Sheet near = Sheet.STREAM_CODEC.decode(buf);
			Sheet far = Sheet.STREAM_CODEC.decode(buf);
			int sky = buf.readInt();
			String biome = ByteBufCodecs.STRING_UTF8.decode(buf);
			int count = VarInt.read(buf);
			List<Block> blocks = new ArrayList<>(Math.min(count, 65536));
			for (int i = 0; i < count; i++) {
				blocks.add(new Block(unzigzag(VarInt.read(buf)), unzigzag(VarInt.read(buf)), unzigzag(VarInt.read(buf)),
					VarInt.read(buf), buf.readUnsignedByte()));
			}
			return new PortalViewS2C(near, far, sky, biome, blocks);
		}
	);

	private static int zigzag(int n) {
		return (n << 1) ^ (n >> 31);
	}

	private static int unzigzag(int n) {
		return (n >>> 1) ^ -(n & 1);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
