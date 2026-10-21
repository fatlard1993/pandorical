package justfatlard.pandorical.portal;

import justfatlard.pandorical.api.PortalApi;
import justfatlard.pandorical.protocol.PortalViewS2C;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sends each player with immersive portals what lies past the paired portal nearest them: the
 * blocks either side of its partner, a short look deep, with the light on them. Checked twice a
 * second, and sent again only when what they would see has changed.
 *
 * <p>The client draws it behind vanilla's swirl, which hides how little of the far world this is.
 */
public final class FarSides {
	private FarSides() {}

	/** How far the far side reaches past its sheet, beside it, and above and below it. */
	private static final int DEEP = 12;
	private static final int WIDE = 12;
	private static final int TALL = 8;
	private static final int SEARCH = 16;
	private static final int EVERY = 10;
	/** A cave on both sides is the worst case; past this the rest is left out rather than the packet grown. */
	private static final int MOST_BLOCKS = 16000;
	/**
	 * Sky light for a world with no sky. The near world's own light map draws the far blocks, and it
	 * knows nothing of the nether's glow; without this the far side of a nether portal is black.
	 */
	private static final int SKYLESS_GLOW = 13;

	private static final Map<UUID, Integer> sent = new HashMap<>();
	private static final List<PortalApi.LookThrough> answers = new CopyOnWriteArrayList<>();

	public static void lookThrough(PortalApi.LookThrough answer) {
		if (answer != null) answers.add(answer);
	}

	/** Where a portal leads for this player: a mod's own answer first, then the portal it is paired with. */
	private static PortalApi.FarEnd farEndOf(ServerPlayer viewer, ServerLevel level, BlockPos near) {
		for (PortalApi.LookThrough answer : answers) {
			PortalApi.FarEnd far = answer.farEnd(viewer, level, near);
			if (far != null) return far;
		}
		GlobalPos partner = PortalPairing.partnerOf(level, near);
		return partner == null ? null : PortalApi.FarEnd.of(partner);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY != 0) return;
			boolean on = ImmersivePortals.enabled(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) update(server, player, on);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> sent.remove(handler.player.getUUID())));
	}

	private static void update(MinecraftServer server, ServerPlayer player, boolean on) {
		if (!ServerPlayNetworking.canSend(player, PortalViewS2C.TYPE)) return;
		Integer before = sent.get(player.getUUID());
		PortalViewS2C view = on ? viewFor(server, player) : PortalViewS2C.NONE;
		// The far chunk is still loading: keep showing whatever was shown
		if (view == null) return;
		if (!view.shown()) {
			if (before != null) ServerPlayNetworking.send(player, PortalViewS2C.NONE);
			sent.remove(player.getUUID());
			return;
		}
		int hash = view.hashCode();
		if (before != null && before == hash) return;
		ServerPlayNetworking.send(player, view);
		sent.put(player.getUUID(), hash);
	}

	/** {@link PortalViewS2C#NONE} for no portal in reach, null to wait for the far side to load. */
	private static PortalViewS2C viewFor(MinecraftServer server, ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos near = level.getPoiManager()
			.findClosest(type -> type.is(PoiTypes.NETHER_PORTAL), player.blockPosition(), SEARCH, PoiManager.Occupancy.ANY)
			.orElse(null);
		if (near == null) return PortalViewS2C.NONE;
		PortalApi.FarEnd farEnd = farEndOf(player, level, near);
		if (farEnd == null) return PortalViewS2C.NONE;
		GlobalPos partner = farEnd.portal();
		ServerLevel farLevel = server.getLevel(partner.dimension());
		if (farLevel == null) return PortalViewS2C.NONE;

		// Held loaded while somebody looks, the way vanilla holds a portal's far end for a traveller
		farLevel.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(partner.pos()), 2);
		if (!farLevel.hasChunkAt(partner.pos())) return null;

		PortalViewS2C.Sheet nearSheet = sheet(level, near);
		PortalViewS2C.Sheet farSheet = sheet(farLevel, partner.pos());
		if (nearSheet == null || farSheet == null) return PortalViewS2C.NONE;
		Direction face = farEnd.face();
		// A face the sheet does not have is no face: both sides, as vanilla would
		if (face != null && (face.getAxis() == Direction.Axis.Y || (face.getAxis() == Direction.Axis.Z) != farSheet.alongX())) face = null;

		return new PortalViewS2C(nearSheet, farSheet, face, sky(farLevel), biome(farLevel, partner.pos()), blocks(farLevel, farSheet, face));
	}

	private static PortalViewS2C.Sheet sheet(ServerLevel level, BlockPos inside) {
		Set<BlockPos> blocks = PortalPairing.sheetOf(level, inside);
		if (blocks.isEmpty()) return null;
		boolean alongX = level.getBlockState(inside).getValue(NetherPortalBlock.AXIS) == Direction.Axis.X;
		int lowX = Integer.MAX_VALUE, lowY = Integer.MAX_VALUE, lowZ = Integer.MAX_VALUE;
		int highX = Integer.MIN_VALUE, highY = Integer.MIN_VALUE, highZ = Integer.MIN_VALUE;
		for (BlockPos pos : blocks) {
			lowX = Math.min(lowX, pos.getX()); highX = Math.max(highX, pos.getX());
			lowY = Math.min(lowY, pos.getY()); highY = Math.max(highY, pos.getY());
			lowZ = Math.min(lowZ, pos.getZ()); highZ = Math.max(highZ, pos.getZ());
		}
		int width = alongX ? highX - lowX + 1 : highZ - lowZ + 1;
		return new PortalViewS2C.Sheet(new BlockPos(lowX, lowY, lowZ), alongX, width, highY - lowY + 1);
	}

	/** Every block in front of the sheet with a face open to the air, nearest rows first: both sides, or only {@code face}'s. */
	private static List<PortalViewS2C.Block> blocks(ServerLevel level, PortalViewS2C.Sheet sheet, Direction face) {
		List<PortalViewS2C.Block> out = new ArrayList<>();
		boolean skyless = !level.dimensionType().hasSkyLight();
		BlockPos corner = sheet.corner();
		for (int depth = 1; depth <= DEEP; depth++) {
			int[] sides = face == null ? new int[] {-depth, depth} : new int[] {depth * face.getAxisDirection().getStep()};
			for (int side : sides) {
				for (int across = -WIDE; across < sheet.width() + WIDE; across++) {
					for (int up = -TALL; up < sheet.height() + TALL; up++) {
						BlockPos at = sheet.alongX() ? corner.offset(across, up, side) : corner.offset(side, up, across);
						BlockState state = level.getBlockState(at);
						if (state.isAir()) continue;
						int light = openLight(level, at, skyless);
						if (light < 0) continue;
						out.add(new PortalViewS2C.Block(at.getX() - corner.getX(), at.getY() - corner.getY(),
							at.getZ() - corner.getZ(), Block.getId(state), light));
						if (out.size() >= MOST_BLOCKS) return out;
					}
				}
			}
		}
		return out;
	}

	/** The brightest light on any open face, packed block-then-sky; -1 when no face is open. */
	private static int openLight(ServerLevel level, BlockPos pos, boolean skyless) {
		int block = -1, sky = -1;
		for (Direction side : Direction.values()) {
			BlockPos beside = pos.relative(side);
			if (level.getBlockState(beside).canOcclude()) continue;
			block = Math.max(block, level.getBrightness(LightLayer.BLOCK, beside));
			sky = Math.max(sky, level.getBrightness(LightLayer.SKY, beside));
		}
		if (block < 0) return -1;
		if (skyless) sky = Math.max(sky, SKYLESS_GLOW);
		return block << 4 | sky;
	}

	private static int sky(ServerLevel level) {
		if (level.dimensionType().hasSkyLight()) return 0x78A7FF;
		return level.dimension() == Level.NETHER ? 0x330808 : 0x0B0B14;
	}

	private static String biome(ServerLevel level, BlockPos pos) {
		return level.getBiome(pos).unwrapKey().map(key -> key.identifier().toString()).orElse("");
	}
}
