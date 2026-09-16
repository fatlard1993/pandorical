package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import justfatlard.pandorical.api.MapTerrain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Play-phase packet about one item frame's relief: its terrain, or null to draw the map flat
 * again, and whether the change is to be watched happening. The frame is named by its network id.
 *
 * <p>The channel is named for its layout: a build sending one layout never reaches a client
 * reading another, since each only sends on channels the other has. A change to the layout is a
 * new name.
 *
 * <p>The terrain is deflated: a map's worth of columns is a few hundred kilobytes of mostly
 * repeated runs, and would otherwise crowd the one-megabyte payload limit on a rugged map.
 */
public record MapReliefS2C(int entityId, MapTerrain terrain, Motion motion) implements CustomPacketPayload {
    /** How a change is seen: at once, the ground rising out of the map, or sinking back into it. */
    public enum Motion { NONE, RISE, SINK }

    public static final Type<MapReliefS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "map_relief_v2"));

    private static final int COLUMNS = MapTerrain.SIDE * MapTerrain.SIDE;
    /** Largest a terrain may unpack to, so a bad packet cannot ask for a gigabyte. */
    private static final int MOST_UNPACKED = 16 << 20;

    public static MapReliefS2C show(int entityId, MapTerrain terrain, Motion motion) {
        return new MapReliefS2C(entityId, terrain, motion);
    }

    public static MapReliefS2C clear(int entityId, Motion motion) {
        return new MapReliefS2C(entityId, null, motion);
    }

    public boolean isShow() { return terrain != null; }

    public static final StreamCodec<ByteBuf, MapReliefS2C> STREAM_CODEC = StreamCodec.of(
        (raw, value) -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(raw);
            buf.writeVarInt(value.entityId());
            buf.writeEnum(value.motion());
            buf.writeBoolean(value.isShow());
            if (!value.isShow()) return;
            byte[] packed = pack(value.terrain());
            buf.writeVarInt(packed.length);
            buf.writeByteArray(deflate(packed));
        },
        raw -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(raw);
            int entityId = buf.readVarInt();
            Motion motion = buf.readEnum(Motion.class);
            if (!buf.readBoolean()) return clear(entityId, motion);
            int size = buf.readVarInt();
            if (size < 0 || size > MOST_UNPACKED) throw new IllegalArgumentException("a terrain of " + size + " bytes");
            return show(entityId, unpack(inflate(buf.readByteArray(), size)), motion);
        });

    private static byte[] pack(MapTerrain terrain) {
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        List<BlockState> blocks = terrain.blocks();
        out.writeVarInt(blocks.size());
        for (int i = 1; i < blocks.size(); i++) out.writeVarInt(Block.getId(blocks.get(i)));
        out.writeVarInt(terrain.biomes().size());
        for (Identifier biome : terrain.biomes()) out.writeIdentifier(biome);
        for (int column = 0; column < COLUMNS; column++) {
            int first = terrain.firstRun(column), end = terrain.firstRun(column + 1);
            out.writeVarInt(terrain.biomeOf(column));
            out.writeVarInt(end - first);
            for (int run = first; run < end; run++) {
                out.writeVarInt(terrain.runBlock(run));
                out.writeVarInt(terrain.runLength(run));
            }
        }
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        out.release();
        return bytes;
    }

    private static MapTerrain unpack(byte[] bytes) {
        FriendlyByteBuf in = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        int blockCount = in.readVarInt();
        List<BlockState> blocks = new ArrayList<>(blockCount);
        blocks.add(null);
        for (int i = 1; i < blockCount; i++) {
            int id = in.readVarInt();
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
            if (state == null) throw new IllegalArgumentException("no block state " + id);
            blocks.add(state);
        }
        int biomeCount = in.readVarInt();
        List<Identifier> biomes = new ArrayList<>(biomeCount);
        for (int i = 0; i < biomeCount; i++) biomes.add(in.readIdentifier());
        int[] columnBiome = new int[COLUMNS];
        int[] runStart = new int[COLUMNS + 1];
        IntArrayList runs = new IntArrayList();
        for (int column = 0; column < COLUMNS; column++) {
            columnBiome[column] = in.readVarInt();
            runStart[column] = runs.size() / 2;
            int count = in.readVarInt();
            for (int run = 0; run < count; run++) {
                runs.add(in.readVarInt());
                runs.add(in.readVarInt());
            }
        }
        runStart[COLUMNS] = runs.size() / 2;
        return MapTerrain.of(blocks, biomes, columnBiome, runStart, runs.toIntArray());
    }

    private static byte[] deflate(byte[] bytes) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(bytes);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length / 4 + 64);
            byte[] chunk = new byte[8192];
            while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk));
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] bytes, int size) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(bytes);
            byte[] out = new byte[size];
            int filled = 0;
            while (filled < size && !inflater.finished()) {
                int n = inflater.inflate(out, filled, size - filled);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                filled += n;
            }
            if (filled != size) throw new IllegalArgumentException("a terrain " + filled + " bytes long, not " + size);
            return out;
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("a terrain that does not inflate", e);
        } finally {
            inflater.end();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
