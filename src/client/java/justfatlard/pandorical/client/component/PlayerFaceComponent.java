package justfatlard.pandorical.client.component;

import java.util.Map;
import java.util.UUID;
import justfatlard.pandorical.api.ComponentType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * A player's face with its hat layer, square at the component's size. See
 * {@link ComponentType#PLAYER_FACE}.
 *
 * <p>The skin is looked up every frame rather than once, because a skin arrives late: a player
 * just joined has the default one for a moment while theirs downloads, and a face fixed at open
 * would keep that. The lookup is two map reads.
 */
public class PlayerFaceComponent extends AbstractComponent {
    private UUID player;

    @Override
    public void init(justfatlard.pandorical.protocol.ComponentDef def, ComponentContext context) {
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

    /**
     * The skin they are wearing, whether or not they are in sight: a skin the server dressed
     * them in first, then their body's, then the tab list's.
     *
     * <p>The worn skin is asked for by player, not read off the body, because only the body
     * carries it: a face read from the tab list whenever the player walked out of sight turned
     * into the default skin on an offline server, whose tab list has no skins at all.
     */
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
        PlayerSkin worn = justfatlard.pandorical.client.skin.SkinOverrides.forPlayer(player, theirs);
        return worn != null ? worn : theirs;
    }
}
