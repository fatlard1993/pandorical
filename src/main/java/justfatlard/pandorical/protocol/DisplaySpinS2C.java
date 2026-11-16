package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A display entity's drawing turning about a point round an axis, in the display's own frame after
 * its transformation, at a declared rate, for the client to turn by itself. Sent only to the
 * players tracking the display, and again whenever one starts to; the client forgets it when the
 * display leaves its world.
 *
 * @param degreesPerTick 0 to stop, which draws the display as its transformation alone
 * @param ticks          how long it turns before holding the angle reached; 0 for until stopped
 * @param elapsedMillis  how long ago, by the server's clock, it started
 */
public record DisplaySpinS2C(int entityId, float pivotX, float pivotY, float pivotZ,
		float axisX, float axisY, float axisZ, float degreesPerTick, int ticks, long elapsedMillis)
		implements CustomPacketPayload {

	public static final Type<DisplaySpinS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("pandorical", "display_spin"));

	public static final StreamCodec<ByteBuf, DisplaySpinS2C> STREAM_CODEC = StreamCodec.of(
		(buf, spin) -> {
			FriendlyByteBuf out = new FriendlyByteBuf(buf);
			out.writeVarInt(spin.entityId);
			out.writeFloat(spin.pivotX);
			out.writeFloat(spin.pivotY);
			out.writeFloat(spin.pivotZ);
			out.writeFloat(spin.axisX);
			out.writeFloat(spin.axisY);
			out.writeFloat(spin.axisZ);
			out.writeFloat(spin.degreesPerTick);
			out.writeVarInt(spin.ticks);
			out.writeVarLong(spin.elapsedMillis);
		},
		buf -> {
			FriendlyByteBuf in = new FriendlyByteBuf(buf);
			return new DisplaySpinS2C(in.readVarInt(), in.readFloat(), in.readFloat(), in.readFloat(),
				in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readVarInt(), in.readVarLong());
		});

	public static DisplaySpinS2C stop(int entityId) {
		return new DisplaySpinS2C(entityId, 0, 0, 0, 0, 1, 0, 0, 0, 0);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
