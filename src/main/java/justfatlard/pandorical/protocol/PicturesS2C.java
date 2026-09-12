package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import justfatlard.pandorical.api.Picture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Play-phase packet about one entity's picture: the whole of it, some of its cells, or its
 * removal. The entity is named by its network id.
 */
public record PicturesS2C(int entityId, Picture picture, int[] indices, byte[] values) implements CustomPacketPayload {
    public static final Type<PicturesS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "pictures"));

    private static final byte SHOW = 0;
    private static final byte PAINT = 1;
    private static final byte CLEAR = 2;

    public static PicturesS2C show(int entityId, Picture picture) {
        return new PicturesS2C(entityId, picture, null, null);
    }

    public static PicturesS2C paint(int entityId, int[] indices, byte[] values) {
        return new PicturesS2C(entityId, null, indices, values);
    }

    public static PicturesS2C clear(int entityId) {
        return new PicturesS2C(entityId, null, null, null);
    }

    public boolean isShow() { return picture != null; }

    public boolean isPaint() { return indices != null; }

    public static final StreamCodec<ByteBuf, PicturesS2C> STREAM_CODEC = StreamCodec.of(
        (raw, value) -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(raw);
            buf.writeVarInt(value.entityId());
            if (value.isShow()) {
                Picture p = value.picture();
                buf.writeByte(SHOW);
                buf.writeVarInt(p.columns());
                buf.writeVarInt(p.rows());
                buf.writeVarIntArray(p.palette());
                buf.writeByteArray(p.cells());
                Picture.Pose pose = p.pose();
                buf.writeFloat(pose.x());
                buf.writeFloat(pose.y());
                buf.writeFloat(pose.z());
                buf.writeFloat(pose.yaw());
                buf.writeFloat(pose.tilt());
                buf.writeFloat(pose.width());
                buf.writeFloat(pose.height());
                buf.writeInt(p.backColor());
                buf.writeFloat(p.thickness());
            } else if (value.isPaint()) {
                buf.writeByte(PAINT);
                buf.writeVarIntArray(value.indices());
                buf.writeByteArray(value.values());
            } else {
                buf.writeByte(CLEAR);
            }
        },
        raw -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(raw);
            int entityId = buf.readVarInt();
            byte kind = buf.readByte();
            if (kind == SHOW) {
                int columns = buf.readVarInt();
                int rows = buf.readVarInt();
                int[] palette = buf.readVarIntArray();
                byte[] cells = buf.readByteArray();
                Picture.Pose pose = new Picture.Pose(buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
                return show(entityId, new Picture(columns, rows, palette, cells, pose, buf.readInt(), buf.readFloat()));
            }
            if (kind == PAINT) return paint(entityId, buf.readVarIntArray(), buf.readByteArray());
            return clear(entityId);
        });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
