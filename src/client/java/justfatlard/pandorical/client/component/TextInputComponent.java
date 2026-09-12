package justfatlard.pandorical.client.component;

import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

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
        if (parseBool("focused", false)) {
            editBox.setFocused(true);
        }
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
            if (changedProps.containsKey("focused")) {
                editBox.setFocused(parseBool("focused", false));
            }
            // A hidden field must let go of the keyboard, or every key the screen gets from
            // here on lands in something nobody can see. Setting the value on the way out is
            // the server's job; only the focus is taken.
            if (!visible && editBox.isFocused()) {
                editBox.setFocused(false);
            }
        }
    }

    @Override
    protected void moved() {
        if (editBox != null) editBox.setPosition(x, y);
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
            boolean handled = editBox.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
            // A focused field owns the keyboard, escape aside. A letter the box has no use for
            // would otherwise fall through to the screen, and on a container screen the
            // inventory key is one of those letters: typing "e" into a search would close the
            // chest. Vanilla's anvil makes the same claim through canConsumeInput().
            return handled || keyCode != InputConstants.KEY_ESCAPE;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editBox != null && editBox.isFocused()) {
            return editBox.charTyped(new CharacterEvent(chr));
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

    /** What was typed, and whether it was being typed into: the server only ever hears the text. */
    @Override
    public void carryOverFrom(PandoricalComponent previous) {
        TextInputComponent old = (TextInputComponent) previous;
        if (editBox == null || old.editBox == null) return;
        editBox.setValue(old.editBox.getValue());
        editBox.setFocused(old.editBox.isFocused());
    }
}
