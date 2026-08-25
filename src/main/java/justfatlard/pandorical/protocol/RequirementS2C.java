package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * What this server needs of the Pandorical on the other end, said during configuration.
 *
 * <p>Sent before any content is, because content is what breaks. The formats in that sync have
 * changed before and will change again - an equippable item's slot gained the id of its armour
 * asset once, and a client one version behind threw on every piece of armour, died inside the
 * config phase, and dropped itself with no message. What the player saw was a mod name and a
 * disconnect loop; what it actually was is four versions of drift on their own machine.
 *
 * <p>This payload exists so that never happens quietly again, and it works two ways at once.
 * Being <em>able to receive it</em> is itself the coarse test - a client too old to have declared
 * this type is too old for the current content format, and the server can say so plainly instead
 * of letting the sync fail. Its contents are the fine test, for every version after this one: the
 * client compares them against itself and says which way it is wrong.
 *
 * @param protocolVersion   the wire format this server speaks
 * @param minimumProtocol   the oldest the client may speak and still be understood
 * @param serverModVersion  what to tell the player to install, in the words on the jar
 */
public record RequirementS2C(int protocolVersion, int minimumProtocol, String serverModVersion)
    implements CustomPacketPayload {

    public static final Type<RequirementS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "requirement"));

    public static final StreamCodec<ByteBuf, RequirementS2C> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public RequirementS2C decode(ByteBuf buf) {
                return new RequirementS2C(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, RequirementS2C value) {
                ByteBufCodecs.VAR_INT.encode(buf, value.protocolVersion());
                ByteBufCodecs.VAR_INT.encode(buf, value.minimumProtocol());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.serverModVersion());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
