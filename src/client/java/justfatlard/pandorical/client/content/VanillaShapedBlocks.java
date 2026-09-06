package justfatlard.pandorical.client.content;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * Stand-ins that are the real thing.
 *
 * <p>A synced door is a vanilla door on the server, with vanilla's own states, shapes and
 * placement. A generic stand-in carrying a table of shapes per state stood in for it and
 * did not stand in well: a player walked into an open pair as if it were shut. Vanilla's
 * classes already know everything about a door and a trapdoor; the constructors are merely
 * protected, and these subclasses exist to reach them. Their shapes come from the class, so
 * the server's table is not needed and not applied.
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

    /** A door or trapdoor for a base block that is one, else null. */
    static Block forBase(Block baseBlock, BlockBehaviour.Properties props) {
        if (baseBlock instanceof DoorBlock door) return new ClientDoorBlock(door.type(), props);
        if (baseBlock instanceof TrapDoorBlock trapdoor) return new ClientTrapDoorBlock(setTypeOf(trapdoor), props);
        return null;
    }

    /** A trapdoor keeps its set type to itself; read it off the field, or fall back to oak. */
    private static BlockSetType setTypeOf(TrapDoorBlock trapdoor) {
        for (java.lang.reflect.Field field : TrapDoorBlock.class.getDeclaredFields()) {
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
