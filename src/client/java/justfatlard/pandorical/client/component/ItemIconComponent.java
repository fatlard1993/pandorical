package justfatlard.pandorical.client.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.Optional;

/**
 * Renders a single item icon at the component's position.
 * Props: item_id (e.g. "minecraft:red_shrub"), item_count (default 1).
 * Count > 1 is shown as the vanilla stack count overlay.
 */
public class ItemIconComponent extends AbstractComponent {
    private ItemStack stack = ItemStack.EMPTY;

    @Override
    public void init(justfatlard.pandorical.protocol.ComponentDef def, ComponentContext context) {
        super.init(def, context);
        if (width == 0) width = 16;
        if (height == 0) height = 16;
        parseStack();
    }

    @Override
    public void updateProps(Map<String, String> changedProps) {
        super.updateProps(changedProps);
        parseStack();
    }

    private void parseStack() {
        String itemId = parseString("item_id", "");
        int count = parseInt("item_count", 1);
        if (itemId.isEmpty()) {
            stack = ItemStack.EMPTY;
            return;
        }
        Identifier loc = Identifier.tryParse(itemId);
        if (loc == null) {
            stack = ItemStack.EMPTY;
            return;
        }
        Optional<Item> found = BuiltInRegistries.ITEM.getOptional(loc);
        Item item = found.orElse(Items.AIR);
        stack = new ItemStack(item, Math.max(1, count));
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (stack.isEmpty()) return;
        // Items need their own stratum so they render above the fill layers
        graphics.nextStratum();
        graphics.fakeItem(stack, x, y);
        if (stack.getCount() > 1) {
            graphics.itemDecorations(context.font(), stack, x, y);
        }
        graphics.nextStratum();
    }
}
