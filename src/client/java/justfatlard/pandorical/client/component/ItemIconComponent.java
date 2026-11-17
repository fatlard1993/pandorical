package justfatlard.pandorical.client.component;

import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.ItemIcons;
import justfatlard.pandorical.protocol.ComponentDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.Optional;

public class ItemIconComponent extends AbstractComponent {
    private ItemStack stack = ItemStack.EMPTY;

    @Override
    public void init(ComponentDef def, ComponentContext context) {
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

    /** What the stack was made from, so an update to some other prop does not unpack it again. */
    private String parsedFrom;

    private void parseStack() {
        String itemId = parseString("item_id", "");
        int count = parseInt("item_count", 1);
        String from = itemId + "\n" + count + "\n" + parseString(ComponentType.PROP_ITEM_COMPONENTS, "");
        if (from.equals(parsedFrom)) return;
        parsedFrom = from;
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
        String components = parseString(ComponentType.PROP_ITEM_COMPONENTS, "");
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (!components.isEmpty() && level != null && !stack.isEmpty()) {
            ItemIcons.read(components, level.registryAccess()).ifPresent(stack::applyComponents);
        } else if (!components.isEmpty() && level == null) {
            parsedFrom = null;
        }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (stack.isEmpty()) return;
        // Without its own stratum the item draws under the fill layers.
        graphics.nextStratum();
        graphics.fakeItem(stack, x, y);
        if (stack.getCount() > 1) {
            graphics.itemDecorations(context.font(), stack, x, y);
        }
        graphics.nextStratum();
    }
}
