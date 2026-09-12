package justfatlard.pandorical.client.component;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** A vanilla EditBox that sends {@code {"text": ...}} on every change. */
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
            // A hidden field must not keep the keyboard.
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
            boolean handled = editBox.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
            // A focused field takes every key but escape, or the inventory key closes the screen.
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

    @Override
    public boolean isNavigable() {
        return editBox != null;
    }

    public String getValue() {
        return editBox != null ? editBox.getValue() : "";
    }

    @Override
    public void carryOverFrom(PandoricalComponent previous) {
        TextInputComponent old = (TextInputComponent) previous;
        if (editBox == null || old.editBox == null) return;
        editBox.setValue(old.editBox.getValue());
        editBox.setFocused(old.editBox.isFocused());
    }
}
