package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The client adds matching slots to its InventoryMenu so vanilla's slot sync can fill them. */
public record PlayerInventoryRegistrationsS2C(
    List<SlotGroup> groups
) implements CustomPacketPayload {

    public static final Type<PlayerInventoryRegistrationsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "player_inv_registrations"));

    public record SlotGroup(
        String namespace,
        List<SlotPosition> slots
    ) {}

    /** @param backgroundSprite a sprite id, or null for none (empty on the wire) */
    public record SlotPosition(int slotIndex, int screenX, int screenY, @Nullable String backgroundSprite) {}

    // Counts off the wire are capped, and lists sized by what decodes, not by what is claimed.
    private static final int MAX_GROUPS = 64;
    private static final int MAX_SLOTS_PER_GROUP = 256;

    public static final StreamCodec<ByteBuf, PlayerInventoryRegistrationsS2C> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public PlayerInventoryRegistrationsS2C decode(ByteBuf buf) {
                int groupCount = ByteBufCodecs.VAR_INT.decode(buf);
                if (groupCount < 0 || groupCount > MAX_GROUPS) {
                    throw new DecoderException(
                        "slot group count " + groupCount + " exceeds " + MAX_GROUPS);
                }
                List<SlotGroup> groups = new ArrayList<>();
                for (int g = 0; g < groupCount; g++) {
                    String namespace = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int slotCount = ByteBufCodecs.VAR_INT.decode(buf);
                    if (slotCount < 0 || slotCount > MAX_SLOTS_PER_GROUP) {
                        throw new DecoderException(
                            "slot count " + slotCount + " exceeds " + MAX_SLOTS_PER_GROUP);
                    }
                    List<SlotPosition> slots = new ArrayList<>();
                    for (int s = 0; s < slotCount; s++) {
                        int idx    = ByteBufCodecs.VAR_INT.decode(buf);
                        int x      = ByteBufCodecs.VAR_INT.decode(buf);
                        int y      = ByteBufCodecs.VAR_INT.decode(buf);
                        String spr = ByteBufCodecs.STRING_UTF8.decode(buf);
                        slots.add(new SlotPosition(idx, x, y, spr.isEmpty() ? null : spr));
                    }
                    groups.add(new SlotGroup(namespace, slots));
                }
                return new PlayerInventoryRegistrationsS2C(groups);
            }

            @Override
            public void encode(ByteBuf buf, PlayerInventoryRegistrationsS2C value) {
                ByteBufCodecs.VAR_INT.encode(buf, value.groups().size());
                for (SlotGroup group : value.groups()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, group.namespace());
                    ByteBufCodecs.VAR_INT.encode(buf, group.slots().size());
                    for (SlotPosition slot : group.slots()) {
                        ByteBufCodecs.VAR_INT.encode(buf, slot.slotIndex());
                        ByteBufCodecs.VAR_INT.encode(buf, slot.screenX());
                        ByteBufCodecs.VAR_INT.encode(buf, slot.screenY());
                        String spr = slot.backgroundSprite() != null ? slot.backgroundSprite() : "";
                        ByteBufCodecs.STRING_UTF8.encode(buf, spr);
                    }
                }
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
