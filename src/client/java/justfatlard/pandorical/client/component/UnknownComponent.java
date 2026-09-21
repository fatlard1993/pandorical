package justfatlard.pandorical.client.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Stands in for a type this client does not know: draws nothing, so only its children show. */
public final class UnknownComponent extends AbstractComponent {
    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}
}
