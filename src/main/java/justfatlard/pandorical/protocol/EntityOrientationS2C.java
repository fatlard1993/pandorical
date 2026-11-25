package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * An entity's orientation, a unit quaternion in its own frame, or that it has none any more. Sent
 * only to the players tracking it, again whenever one starts to; the client forgets it when the
 * entity leaves its world.
 */
public record EntityOrientationS2C(int entityId, boolean oriented, float x, float y, float z, float w)
		implements CustomPacketPayload {

	public static final Type<EntityOrientationS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "entity_orientation"));

	public static final StreamCodec<ByteBuf, EntityOrientationS2C> STREAM_CODEC = StreamCodec.of(
		(buf, turn) -> {
			FriendlyByteBuf out = new FriendlyByteBuf(buf);
			out.writeVarInt(turn.entityId);
			out.writeBoolean(turn.oriented);
			out.writeFloat(turn.x);
			out.writeFloat(turn.y);
			out.writeFloat(turn.z);
			out.writeFloat(turn.w);
		},
		buf -> {
			FriendlyByteBuf in = new FriendlyByteBuf(buf);
			return new EntityOrientationS2C(in.readVarInt(), in.readBoolean(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
		});

	public static EntityOrientationS2C cleared(int entityId) {
		return new EntityOrientationS2C(entityId, false, 0, 0, 0, 1);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
