package justfatlard.pandorical.client.structure;

import justfatlard.pandorical.client.item.CellsItemRenderer;
import justfatlard.pandorical.protocol.SetStructureDrawingsS2C;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A structure block drawn as a build: the cells that show, already culled and turned, worked out
 * once as the drawing arrives. Drawn by {@link StructureRenderer} in the block's place.
 *
 * @param showing the cells to draw, in the build's own grid, turned; empty draws nothing
 */
public record StructureDrawing(int n, float lift, List<CellsItemRenderer.Cell> showing) {
    /** What an entry draws; nothing for one whose layout does not add up. */
    static StructureDrawing of(SetStructureDrawingsS2C.Entry entry) {
        CellsItemRenderer.Cells cells = CellsItemRenderer.cull(entry.n(), entry.height(), entry.palette(), entry.cells());
        if (cells == null) return new StructureDrawing(Math.max(1, entry.n()), entry.lift(), List.of());
        int n = cells.size();
        int turns = Math.floorMod(entry.turns(), 4);
        if (turns == 0) return new StructureDrawing(n, entry.lift(), cells.showing());
        List<CellsItemRenderer.Cell> turned = new ArrayList<>(cells.showing().size());
        for (CellsItemRenderer.Cell cell : cells.showing()) {
            int x = cell.x(), z = cell.z();
            BlockState state = cell.state();
            for (int i = 0; i < turns; i++) {
                int was = x;
                x = n - 1 - z;
                z = was;
                state = state.rotate(Rotation.CLOCKWISE_90);
            }
            turned.add(new CellsItemRenderer.Cell(x, cell.y(), z, state));
        }
        return new StructureDrawing(n, entry.lift(), List.copyOf(turned));
    }
}
