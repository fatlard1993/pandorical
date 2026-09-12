package justfatlard.pandorical.client.inventory;

import justfatlard.pandorical.protocol.InventoryButtonC2S;
import justfatlard.pandorical.protocol.InventoryButtonsS2C;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/** Inventory screen buttons the server declared; drawn by {@code InventoryScreenMixin}. */
@Environment(EnvType.CLIENT)
public final class ClientInventoryButtons {
    private ClientInventoryButtons() {}

    private static volatile List<InventoryButtonsS2C.Button> buttons = List.of();

    public static void set(List<InventoryButtonsS2C.Button> declared) {
        buttons = List.copyOf(declared);
    }

    public static void clear() {
        buttons = List.of();
    }

    public static List<InventoryButtonsS2C.Button> all() {
        return buttons;
    }

    public static void press(InventoryButtonsS2C.Button button) {
        ClientPlayNetworking.send(new InventoryButtonC2S(button.namespace(), button.id()));
    }
}
