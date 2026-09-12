package justfatlard.pandorical.client.component;

import java.util.Map;
import java.util.UUID;
import justfatlard.pandorical.api.ComponentType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import justfatlard.pandorical.client.skin.SkinOverrides;
import justfatlard.pandorical.protocol.ComponentDef;

/** See {@link ComponentType#PLAYER_FACE}. The skin is looked up every frame: it downloads late. */
public class PlayerFaceComponent extends AbstractComponent {
    private UUID player;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);
        if (width == 0) width = 8;
        if (height == 0) height = width;
        readPlayer();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        readPlayer();
    }

    private void readPlayer() {
        try {
            player = UUID.fromString(parseString(ComponentType.PROP_PLAYER, ""));
        } catch (IllegalArgumentException e) {
            player = null;
        }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (player == null) return;
        PlayerFaceExtractor.extractRenderState(graphics, skin(), x, y, Math.min(width, height));
    }

    /** An offline server's tab list has no skins, so the override is looked up by player. */
    private PlayerSkin skin() {
        var minecraft = context.minecraft();
        PlayerSkin theirs;
        if (minecraft.level != null && minecraft.level.getPlayerByUUID(player) instanceof AbstractClientPlayer seen) {
            theirs = seen.getSkin();
        } else if (minecraft.getConnection() != null && minecraft.getConnection().getPlayerInfo(player) != null) {
            theirs = minecraft.getConnection().getPlayerInfo(player).getSkin();
        } else {
            theirs = DefaultPlayerSkin.get(player);
        }
        PlayerSkin worn = SkinOverrides.forPlayer(player, theirs);
        return worn != null ? worn : theirs;
    }
}
