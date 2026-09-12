package justfatlard.pandorical.client.screen;

import justfatlard.pandorical.api.NavigableScreen;
import justfatlard.pandorical.client.component.*;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.protocol.OpenScreenS2C;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

public class PandoricalScreen extends Screen implements NavigableScreen {
    private final OpenScreenS2C screenDef;
    private final ScreenComponents components = new ScreenComponents();

    public PandoricalScreen(OpenScreenS2C screenDef) {
        super(Component.literal(screenDef.title()));
        this.screenDef = screenDef;
    }

    @Override
    protected void init() {
        super.init();

        int screenX = (this.width - screenDef.width()) / 2;
        int screenY = (this.height - screenDef.height()) / 2;

        ComponentContext context = new ComponentContext(
            screenDef.screenId(),
            screenDef.screenType(),
            screenX, screenY,
            this.font,
            this::sendAction,
            null
        );

        components.rebuild(screenDef.components(), context, screenX, screenY);
    }

    @Override
    public void tick() {
        super.tick();
        components.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Blur may run only once per frame, so the background is left to super.
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        components.render(graphics, mouseX, mouseY, delta);
        components.renderChat(this, graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (components.mouseClicked(click)) {
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (components.mouseReleased(click)) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (components.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (components.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (components.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return screenDef.pauseGame();
    }

    @Override
    public void onClose() {
        sendAction("_screen", Map.of());
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        components.removed();
    }

    public void applyUpdates(List<ComponentUpdate> updates) {
        components.applyUpdates(updates);
    }

    public String getScreenId() {
        return screenDef.screenId();
    }

    public String getScreenType() {
        return screenDef.screenType();
    }

    @Override
    public List<NavRegion> navRegions() {
        return components.navRegions();
    }

    private void sendAction(String componentId, Map<String, String> data) {
        ScreenHelper.sendAction(screenDef.screenId(), componentId, data);
    }
}
