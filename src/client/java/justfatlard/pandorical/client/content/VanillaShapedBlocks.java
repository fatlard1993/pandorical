package justfatlard.pandorical.client.content;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import java.lang.reflect.Field;

/**
 * Subclasses only to reach the protected door and trapdoor constructors. Their shapes come from
 * the vanilla class, so the server's shape table is not applied.
 */
final class VanillaShapedBlocks {
    private VanillaShapedBlocks() {}

    static final class ClientDoorBlock extends DoorBlock {
        ClientDoorBlock(BlockSetType type, BlockBehaviour.Properties props) {
            super(type, props);
        }
    }

    static final class ClientTrapDoorBlock extends TrapDoorBlock {
        ClientTrapDoorBlock(BlockSetType type, BlockBehaviour.Properties props) {
            super(type, props);
        }
    }

    static Block forBase(Block baseBlock, BlockBehaviour.Properties props) {
        if (baseBlock instanceof DoorBlock door) return new ClientDoorBlock(door.type(), props);
        if (baseBlock instanceof TrapDoorBlock trapdoor) return new ClientTrapDoorBlock(setTypeOf(trapdoor), props);
        return null;
    }

    /** TrapDoorBlock has no accessor for its set type. */
    private static BlockSetType setTypeOf(TrapDoorBlock trapdoor) {
        for (Field field : TrapDoorBlock.class.getDeclaredFields()) {
            if (field.getType() != BlockSetType.class) continue;
            try {
                field.setAccessible(true);
                return (BlockSetType) field.get(trapdoor);
            } catch (ReflectiveOperationException e) {
                break;
            }
        }
        return BlockSetType.OAK;
    }

    static boolean isOne(Block block) {
        return block instanceof ClientDoorBlock || block instanceof ClientTrapDoorBlock;
    }
}
