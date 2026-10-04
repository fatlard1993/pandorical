package justfatlard.pandorical.structure;

import justfatlard.pandorical.api.BlockEntry;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.RelPos;
import justfatlard.pandorical.api.StructureApi;
import justfatlard.pandorical.api.StructurePose;
import justfatlard.pandorical.protocol.DespawnStructureS2C;
import justfatlard.pandorical.protocol.SetStructureVisibleS2C;
import justfatlard.pandorical.protocol.SetStructureWalkableS2C;
import justfatlard.pandorical.protocol.SpawnStructureS2C;
import justfatlard.pandorical.protocol.StructureBlockEntry;
import justfatlard.pandorical.protocol.StructureRelPos;
import justfatlard.pandorical.protocol.UpdateStructureBlocksS2C;
import justfatlard.pandorical.protocol.UpdateStructurePoseS2C;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureRegistry implements StructureApi {
	public static final StructureRegistry INSTANCE = new StructureRegistry();

	private static final class StructureState {
		final Entity anchorEntity;
		final Map<RelPos, BlockState> blocks;
		StructurePose pose;
		/** The poses it has held lately, newest last: what a client may still be drawing. */
		final java.util.ArrayDeque<StructurePose> recent = new java.util.ArrayDeque<>();
		boolean visible;
		boolean walkable;

		StructureState(Entity anchorEntity, Map<RelPos, BlockState> blocks, StructurePose pose, boolean visible) {
			this.anchorEntity = anchorEntity;
			this.blocks = blocks;
			this.pose = pose;
			this.recent.add(pose);
			this.visible = visible;
		}

		void moveTo(StructurePose next) {
			pose = next;
			recent.addLast(next);
			while (recent.size() > RECENT_POSES) recent.removeFirst();
		}
	}

	/**
	 * How many of a structure's last poses a player's click or climb is held to: about half a second
	 * of ticks, enough to cover the client's smoothing and an ordinary round trip.
	 */
	private static final int RECENT_POSES = 10;

	private final Map<String, StructureState> structures = new ConcurrentHashMap<>();

	private StructureRegistry() {}

	private final java.util.List<justfatlard.pandorical.api.StructureUseHandler> useHandlers =
		new java.util.concurrent.CopyOnWriteArrayList<>();

	@Override
	public void onBlockUse(justfatlard.pandorical.api.StructureUseHandler handler) {
		useHandlers.add(handler);
	}

	/**
	 * A player pressed use on a block of a structure, as their client drew it: to the handlers, if
	 * the block is there and was within their reach where the structure stood at any moment lately.
	 * Held to reach the way vanilla holds a click on a block in the world, with the same margin.
	 */
	public void use(net.minecraft.server.level.ServerPlayer player, String structureId, RelPos pos) {
		if (player.isSpectator() || !player.isAlive()) return;
		StructureState state = structures.get(structureId);
		if (state == null || !state.walkable || !state.visible || state.anchorEntity.level() != player.level()) return;
		BlockState block = state.blocks.get(pos);
		if (block == null || block.isAir()) return;

		double reach = player.blockInteractionRange() + 1.0 + 0.87;
		net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
		boolean near = false;
		for (StructurePose pose : state.recent) {
			if (eye.distanceToSqr(worldPoint(pose, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5)) <= reach * reach) {
				near = true;
				break;
			}
		}
		if (!near) return;

		for (justfatlard.pandorical.api.StructureUseHandler handler : useHandlers) {
			net.minecraft.world.InteractionResult result = handler.use(player, state.anchorEntity, structureId, pos, block);
			if (result != net.minecraft.world.InteractionResult.PASS) return;
		}
	}

	/** A point in a structure's own frame, in the world, with the structure at this pose. */
	private static net.minecraft.world.phys.Vec3 worldPoint(StructurePose pose, double lx, double ly, double lz) {
		double yaw = Math.toRadians(pose.yaw());
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);
		return new net.minecraft.world.phys.Vec3(pose.x() + lx * cos - lz * sin, pose.y() + ly, pose.z() + lx * sin + lz * cos);
	}

	/**
	 * The climbable block a walkable structure has at this entity's feet, if any: a ladder or vine
	 * on a ship at sea is not a block in the world, so the game's own climbing check finds air there.
	 * Answered the way the client finds the deck under a player, turning the point into the
	 * structure's own frame - against each of its recent poses, since a player climbs the ladder
	 * their client draws, which on a moving ship is a little behind where the server has it.
	 */
	public boolean climbableAt(Entity entity) {
		for (StructureState state : structures.values()) {
			if (!state.walkable || !state.visible || state.anchorEntity.level() != entity.level()) continue;
			for (StructurePose pose : state.recent) {
				double yaw = Math.toRadians(pose.yaw());
				double dx = entity.getX() - pose.x();
				double dz = entity.getZ() - pose.z();
				int x = net.minecraft.util.Mth.floor(dx * Math.cos(yaw) + dz * Math.sin(yaw));
				int z = net.minecraft.util.Mth.floor(-dx * Math.sin(yaw) + dz * Math.cos(yaw));
				int y = net.minecraft.util.Mth.floor(entity.getY() - pose.y());
				BlockState block = state.blocks.get(new RelPos(x, y, z));
				if (block != null && block.is(net.minecraft.tags.BlockTags.CLIMBABLE)) return true;
			}
		}
		return false;
	}

	@Override
	public void spawn(Entity anchorEntity, String structureId, List<BlockEntry> blocks, StructurePose initialPose) {
		Map<RelPos, BlockState> blockMap = new LinkedHashMap<>();
		for (BlockEntry entry : blocks) blockMap.put(entry.pos(), entry.state());

		StructureState state = new StructureState(anchorEntity, blockMap, initialPose, true);
		structures.put(structureId, state);

		SpawnStructureS2C packet = buildSpawnPacket(structureId, state);
		broadcastToTrackers(state.anchorEntity, packet);
	}

	/** @hidden */
	public void clear() {
		structures.clear();
		pendingPoses.clear();
	}

	/** Poses held for the tracker pass; see {@link #flushPoses}. */
	private final Set<String> pendingPoses = ConcurrentHashMap.newKeySet();

	@Override
	public void updatePose(String structureId, StructurePose pose) {
		StructureState state = structures.get(structureId);
		if (state == null) return;
		state.moveTo(pose);
		pendingPoses.add(structureId);
	}

	/**
	 * Called at the head of {@code ChunkMap.tick()}, the pass that sends tracked entity positions,
	 * so a structure's pose and the entities riding it land in the same client tick. A pose sent
	 * mid-tick runs a tick ahead of its riders.
	 */
	public void flushPoses(ServerLevel level) {
		if (pendingPoses.isEmpty()) return;
		for (Iterator<String> it = pendingPoses.iterator(); it.hasNext();) {
			String structureId = it.next();
			StructureState state = structures.get(structureId);
			if (state == null) {
				it.remove();
				continue;
			}
			if (state.anchorEntity.level() != level) continue;
			it.remove();
			sendPose(structureId, state);
		}
	}

	/** An update that cannot wait for the tracker pass sends the held pose first. */
	private void sendPendingPose(String structureId, StructureState state) {
		if (pendingPoses.remove(structureId)) sendPose(structureId, state);
	}

	private void sendPose(String structureId, StructureState state) {
		StructurePose pose = state.pose;
		broadcastToTrackers(state.anchorEntity, new UpdateStructurePoseS2C(
			structureId, pose.x(), pose.y(), pose.z(), pose.yaw()));
	}

	@Override
	public void updateBlocks(String structureId, List<BlockEntry> added, List<RelPos> removed, Map<RelPos, BlockState> changed) {
		StructureState state = structures.get(structureId);
		if (state == null) return;
		sendPendingPose(structureId, state);

		for (BlockEntry entry : added) state.blocks.put(entry.pos(), entry.state());
		for (RelPos pos : removed) state.blocks.remove(pos);
		for (Map.Entry<RelPos, BlockState> entry : changed.entrySet()) state.blocks.put(entry.getKey(), entry.getValue());

		List<StructureBlockEntry> addedWire = toWireEntries(added);
		List<StructureRelPos> removedWire = removed.stream()
			.map(p -> new StructureRelPos(p.x(), p.y(), p.z()))
			.toList();
		List<StructureBlockEntry> changedWire = changed.entrySet().stream()
			.map(e -> new StructureBlockEntry(e.getKey().x(), e.getKey().y(), e.getKey().z(), e.getValue()))
			.toList();

		broadcastToTrackers(state.anchorEntity, new UpdateStructureBlocksS2C(
			structureId, addedWire, removedWire, changedWire));
	}

	@Override
	public void setVisible(String structureId, boolean visible) {
		StructureState state = structures.get(structureId);
		if (state == null) return;
		sendPendingPose(structureId, state);
		state.visible = visible;

		broadcastToTrackers(state.anchorEntity, new SetStructureVisibleS2C(structureId, visible));
	}

	@Override
	public void setWalkable(String structureId, boolean walkable) {
		StructureState state = structures.get(structureId);
		if (state == null || state.walkable == walkable) return;
		state.walkable = walkable;

		SetStructureWalkableS2C packet = new SetStructureWalkableS2C(structureId, walkable);
		for (ServerPlayer player : PlayerLookup.tracking(state.anchorEntity)) {
			if (PandoricalApi.hasCapability(player, Capabilities.WALKABLE_STRUCTURES)) {
				ServerPlayNetworking.send(player, packet);
			}
		}
	}

	@Override
	public void despawn(String structureId) {
		StructureState state = structures.remove(structureId);
		if (state == null) return;
		pendingPoses.remove(structureId);

		broadcastToTrackers(state.anchorEntity, new DespawnStructureS2C(structureId));
	}

	/** @hidden */
	public void handleStartTracking(Entity entity, ServerPlayer player) {
		if (!PandoricalApi.hasCapability(player, Capabilities.STRUCTURES)) return;
		for (Map.Entry<String, StructureState> entry : structures.entrySet()) {
			if (entry.getValue().anchorEntity == entity) {
				ServerPlayNetworking.send(player, buildSpawnPacket(entry.getKey(), entry.getValue()));
				if (entry.getValue().walkable && PandoricalApi.hasCapability(player, Capabilities.WALKABLE_STRUCTURES)) {
					ServerPlayNetworking.send(player, new SetStructureWalkableS2C(entry.getKey(), true));
				}
			}
		}
	}

	/** @hidden */
	public void handleStopTracking(Entity entity, ServerPlayer player) {
		if (!PandoricalApi.isAvailable(player)) return;
		for (Map.Entry<String, StructureState> entry : structures.entrySet()) {
			if (entry.getValue().anchorEntity == entity) {
				ServerPlayNetworking.send(player, new DespawnStructureS2C(entry.getKey()));
			}
		}
	}

	private SpawnStructureS2C buildSpawnPacket(String structureId, StructureState state) {
		return new SpawnStructureS2C(
			structureId,
			toWireEntries(state.blocks),
			state.pose.x(), state.pose.y(), state.pose.z(), state.pose.yaw(),
			state.visible
		);
	}

	private List<StructureBlockEntry> toWireEntries(List<BlockEntry> entries) {
		return entries.stream()
			.map(e -> new StructureBlockEntry(e.pos().x(), e.pos().y(), e.pos().z(), e.state()))
			.toList();
	}

	private List<StructureBlockEntry> toWireEntries(Map<RelPos, BlockState> blocks) {
		return blocks.entrySet().stream()
			.map(e -> new StructureBlockEntry(e.getKey().x(), e.getKey().y(), e.getKey().z(), e.getValue()))
			.toList();
	}

	private void broadcastToTrackers(Entity anchorEntity, CustomPacketPayload packet) {
		for (ServerPlayer player : PlayerLookup.tracking(anchorEntity)) {
			if (PandoricalApi.hasCapability(player, Capabilities.STRUCTURES)) {
				ServerPlayNetworking.send(player, packet);
			}
		}
	}
}
