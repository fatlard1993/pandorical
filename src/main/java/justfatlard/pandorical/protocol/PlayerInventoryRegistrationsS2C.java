package justfatlard.pandorical.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent during the Pandorical handshake (after HelloC2S) to inform the client of all registered
 * extra inventory slot groups. The client uses this to add matching dummy slots to InventoryMenu
 * so vanilla's slot-sync protocol can populate them correctly.
 *
 * Item validators are server-only and are not included in this packet.
 */
public record PlayerInventoryRegistrationsS2C(
    List<SlotGroup> groups
) implements CustomPacketPayload {

    public static final Type<PlayerInventoryRegistrationsS2C> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("pandorical", "player_inv_registrations"));

    /** One group of extra slots registered by a single server-side namespace. */
    public record SlotGroup(
        String namespace,
        List<SlotPosition> slots
    ) {}

    /**
     * Screen position for a single slot index.
     *
     * @param backgroundSprite optional sprite identifier string (e.g. {@code "map-plus-plus:empty_map_slot"}),
     *                         or {@code null} / empty string if none
     */
    public record SlotPosition(int slotIndex, int screenX, int screenY, @Nullable String backgroundSprite) {}

    public static final StreamCodec<ByteBuf, PlayerInventoryRegistrationsS2C> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public PlayerInventoryRegistrationsS2C decode(ByteBuf buf) {
                int groupCount = ByteBufCodecs.VAR_INT.decode(buf);
                List<SlotGroup> groups = new ArrayList<>(groupCount);
                for (int g = 0; g < groupCount; g++) {
                    String namespace = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int slotCount = ByteBufCodecs.VAR_INT.decode(buf);
                    List<SlotPosition> slots = new ArrayList<>(slotCount);
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
