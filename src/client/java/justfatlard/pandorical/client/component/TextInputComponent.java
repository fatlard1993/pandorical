package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Wraps vanilla EditBox for text input fields.
 * Sends "input" action with {"text": "..."} on every change.
 *
 * EditBox is a self-rendering widget: it handles its own rendering and input via the
 * widget event system. This component creates the EditBox, renders it via extractRenderState,
 * and forwards input events through the widget's own methods.
 */
public class TextInputComponent extends AbstractComponent {
    private EditBox editBox;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
        super.init(def, context);

        int maxLength = parseInt("max_length", 256);
        String placeholder = props.get("placeholder_key") != null
            ? Component.translatable(props.get("placeholder_key")).getString()
            : parseString("placeholder", "");

        editBox = new EditBox(context.font(), x, y, width, height,
            Component.literal(placeholder));
        editBox.setMaxLength(maxLength);
        editBox.setEditable(parseBool("editable", true));

        String initialValue = parseString("value", "");
        if (!initialValue.isEmpty()) {
            editBox.setValue(initialValue);
        }

        editBox.setResponder(text -> {
            if (context.sendAction() != null) {
                context.sendAction().accept(id, Map.of("text", text));
            }
        });
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        if (editBox != null) {
            if (changedProps.containsKey("value")) {
                editBox.setValue(changedProps.get("value"));
            }
            if (changedProps.containsKey("editable")) {
                editBox.setEditable(parseBool("editable", true));
            }
        }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (editBox != null) {
            // EditBox renders itself via extractRenderState
            editBox.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editBox != null && isMouseOver(mouseX, mouseY)) {
            editBox.setFocused(true);
            return true;
        }
        if (editBox != null) {
            editBox.setFocused(false);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editBox != null && editBox.isFocused()) {
            // keyPressed takes a KeyEvent; we construct one manually from the raw codes.
            return editBox.keyPressed(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers));
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editBox != null && editBox.isFocused()) {
            return editBox.charTyped(new net.minecraft.client.input.CharacterEvent(chr));
        }
        return false;
    }

    // Reachable only once the edit box exists, matching mouseClicked's own
    // guard. Note that landing here is as far as a gamepad gets on its own:
    // focusing the field is navigation, typing into it is not.
    @Override
    public boolean isNavigable() {
        return editBox != null;
    }

    public String getValue() {
        return editBox != null ? editBox.getValue() : "";
    }
}
