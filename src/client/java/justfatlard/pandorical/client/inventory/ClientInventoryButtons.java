package justfatlard.pandorical.client.inventory;

import java.util.List;
import justfatlard.pandorical.protocol.InventoryButtonC2S;
import justfatlard.pandorical.protocol.InventoryButtonsS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * The buttons this server asked for on the inventory screen.
 *
 * <p>Declared once during configuration and drawn by {@code InventoryScreenMixin}, the same way
 * the extra slots are. A server that never sends any leaves the screen exactly as vanilla drew
 * it.
 */
@Environment(EnvType.CLIENT)
public final class ClientInventoryButtons {
    private ClientInventoryButtons() {}

    private static volatile List<InventoryButtonsS2C.Button> buttons = List.of();

    public static void set(List<InventoryButtonsS2C.Button> declared) {
        buttons = List.copyOf(declared);
    }

    /** Dropped on disconnect: the next server's buttons are its own business. */
    public static void clear() {
        buttons = List.of();
    }

    public static List<InventoryButtonsS2C.Button> all() {
        return buttons;
    }

    /** Tell the server a button was pressed. */
    public static void press(InventoryButtonsS2C.Button button) {
        ClientPlayNetworking.send(new InventoryButtonC2S(button.namespace(), button.id()));
    }
}
