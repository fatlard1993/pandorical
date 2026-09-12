package justfatlard.pandorical.client.inventory;

import justfatlard.pandorical.protocol.PlayerInventoryRegistrationsS2C;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Extra inventory slot groups, read by {@code InventoryMenuClientMixin} as the menu is built. */
public final class ClientInventorySlotRegistry {
    private ClientInventorySlotRegistry() {}

    private static final List<PlayerInventoryRegistrationsS2C.SlotGroup> groups =
        new CopyOnWriteArrayList<>();

    public static void receive(PlayerInventoryRegistrationsS2C packet) {
        groups.clear();
        groups.addAll(packet.groups());
    }

    public static List<PlayerInventoryRegistrationsS2C.SlotGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public static void reset() {
        groups.clear();
    }
}
