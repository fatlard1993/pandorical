package justfatlard.pandorical.client.inventory;

import justfatlard.pandorical.protocol.PlayerInventoryRegistrationsS2C;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side registry that stores the extra inventory slot groups received from the server
 * via {@link PlayerInventoryRegistrationsS2C}.
 *
 * <p>Populated when the packet arrives (on the render thread), and cleared on disconnect.
 * The {@link justfatlard.pandorical.client.mixin.InventoryMenuClientMixin} reads from this
 * class when constructing {@code InventoryMenu} for the local player.
 */
public final class ClientInventorySlotRegistry {
    private ClientInventorySlotRegistry() {}

    private static final List<PlayerInventoryRegistrationsS2C.SlotGroup> groups =
        new CopyOnWriteArrayList<>();

    /** Called on the render thread when {@link PlayerInventoryRegistrationsS2C} is received. */
    public static void receive(PlayerInventoryRegistrationsS2C packet) {
        groups.clear();
        groups.addAll(packet.groups());
    }

    /** Returns all slot groups registered by the server. Empty list if not connected or none registered. */
    public static List<PlayerInventoryRegistrationsS2C.SlotGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    /** Called on disconnect — clears all stored registrations. */
    public static void reset() {
        groups.clear();
    }
}
