package justfatlard.pandorical.api;

import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.ShowHudS2C;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public interface HudApi {
    void show(ServerPlayer player, ShowHudS2C overlay);
    void update(ServerPlayer player, String overlayId, List<ComponentUpdate> updates);
    void hide(ServerPlayer player, String overlayId);
}
