package justfatlard.pandorical.push;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import justfatlard.pandorical.api.BlockTintApi;
import justfatlard.pandorical.protocol.BlockTintPositionsS2C;
import justfatlard.pandorical.protocol.BlockTintsConfigS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class BlockTints implements BlockTintApi {
	public static final BlockTints INSTANCE = new BlockTints();

	private final List<BlockTintsConfigS2C.Entry> entries = new ArrayList<>();

	private BlockTints() {}

	@Override public void grass(String... blockIds)     { add("grass",     0, blockIds); }
	@Override public void stem(String... blockIds)      { add("stem",      0, blockIds); }
	@Override public void sugarCane(String... blockIds) { add("sugar_cane",0, blockIds); }
	@Override public void foliage(String... blockIds)   { add("foliage",   0, blockIds); }
	@Override public void constant(int argb, String... blockIds) { add("constant", argb, blockIds); }
	@Override public void positional(String... blockIds)          { add("positional", 0, blockIds); }
	@Override public void positional(int fallbackArgb, String... blockIds) { add("positional", fallbackArgb, blockIds); }

	@Override
	public void paint(ServerPlayer player, Map<BlockPos, Integer> argbByPosition) {
		if (argbByPosition.isEmpty()) return;

		send(player, argbByPosition.entrySet().stream()
			.map(entry -> new BlockTintPositionsS2C.Entry(entry.getKey().asLong(), entry.getValue()))
			.toList());
	}

	@Override
	public void unpaint(ServerPlayer player, Collection<BlockPos> positions) {
		if (positions.isEmpty()) return;

		send(player, positions.stream()
			.map(pos -> new BlockTintPositionsS2C.Entry(pos.asLong(), 0))
			.toList());
	}

	private void send(ServerPlayer player, List<BlockTintPositionsS2C.Entry> entries) {
		if (!ServerPlayNetworking.canSend(player, BlockTintPositionsS2C.TYPE)) return;

		ServerPlayNetworking.send(player, new BlockTintPositionsS2C(entries));
	}

	private void add(String tintType, int constantColor, String[] blockIds) {
		entries.add(new BlockTintsConfigS2C.Entry(tintType, constantColor, List.of(blockIds)));
	}

	public BlockTintsConfigS2C buildPacket() {
		return new BlockTintsConfigS2C(List.copyOf(entries));
	}

	public boolean hasEntries() { return !entries.isEmpty(); }
}
