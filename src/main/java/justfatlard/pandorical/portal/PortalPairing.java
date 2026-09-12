package justfatlard.pandorical.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import justfatlard.pandorical.api.PortalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** A portal is known by the anchor of its sheet, so every block of one portal names one pair. */
public final class PortalPairing implements PortalApi {
	public static final PortalPairing INSTANCE = new PortalPairing();

	private PortalPairing() {}

	/** A sheet is at most 23 by 23; past that it is not a portal and not worth walking. */
	private static final int MOST_BLOCKS = 23 * 23;

	private static volatile boolean byDefault = false;
	private static final List<BiPredicate<ServerLevel, BlockPos>> kept = new CopyOnWriteArrayList<>();

	public static void setDefault(boolean on) {
		byDefault = on;
	}

	public static void keepOut(BiPredicate<ServerLevel, BlockPos> ours) {
		if (ours != null) kept.add(ours);
	}

	@Override
	public void pairNetherPortals(boolean byDefault) {
		setDefault(byDefault);
	}

	@Override
	public void keepOutOfPairing(BiPredicate<ServerLevel, BlockPos> ours) {
		keepOut(ours);
	}

	public static boolean enabled(MinecraftServer server) {
		Boolean chosen = Pairs.get(server).chosen;
		return chosen != null ? chosen : byDefault;
	}

	public static void choose(MinecraftServer server, boolean on) {
		Pairs pairs = Pairs.get(server);
		pairs.chosen = on;
		pairs.setDirty();
	}

	/** Null leaves the trip to vanilla. */
	public static BlockPos partnerFor(ServerLevel from, BlockPos entry, ServerLevel to) {
		MinecraftServer server = from.getServer();
		if (!enabled(server)) return null;
		GlobalPos source = anchorOf(from, entry);
		if (source == null || isKept(from, source.pos())) return null;

		Pairs pairs = Pairs.get(server);
		GlobalPos partner = pairs.links.get(source);
		if (partner == null || !partner.dimension().equals(to.dimension())) return null;
		if (!to.getBlockState(partner.pos()).is(Blocks.NETHER_PORTAL) || isKept(to, partner.pos())) {
			pairs.unlink(source, partner);
			return null;
		}
		return partner.pos();
	}

	public static void remember(ServerLevel from, BlockPos entry, TeleportTransition trip) {
		if (trip == null || trip.newLevel() == null || trip.newLevel() == from) return;
		MinecraftServer server = from.getServer();
		if (!enabled(server)) return;
		GlobalPos source = anchorOf(from, entry);
		if (source == null || isKept(from, source.pos())) return;

		ServerLevel to = trip.newLevel();
		BlockPos arrival = portalNear(to, BlockPos.containing(trip.position()));
		if (arrival == null) return;
		GlobalPos target = anchorOf(to, arrival);
		if (target == null || isKept(to, target.pos())) return;

		Pairs.get(server).link(source, target);
	}

	private static boolean isKept(ServerLevel level, BlockPos pos) {
		for (BiPredicate<ServerLevel, BlockPos> ours : kept) {
			if (ours.test(level, pos)) return true;
		}
		return false;
	}

	/** Vanilla lands a traveller inside the portal, nudged clear of walls: within two blocks. */
	private static BlockPos portalNear(ServerLevel level, BlockPos landing) {
		for (int r = 0; r <= 2; r++) {
			for (BlockPos pos : BlockPos.betweenClosed(landing.offset(-r, -1, -r), landing.offset(r, 2, r))) {
				if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) return pos.immutable();
			}
		}
		return null;
	}

	static GlobalPos anchorOf(ServerLevel level, BlockPos inside) {
		if (!level.getBlockState(inside).is(Blocks.NETHER_PORTAL)) return null;
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> next = new ArrayDeque<>();
		BlockPos start = inside.immutable();
		seen.add(start);
		next.add(start);
		BlockPos corner = start;
		while (!next.isEmpty() && seen.size() <= MOST_BLOCKS) {
			BlockPos pos = next.poll();
			if (before(pos, corner)) corner = pos;
			for (Direction direction : Direction.values()) {
				BlockPos beside = pos.relative(direction);
				if (seen.contains(beside) || !level.hasChunkAt(beside)) continue;
				if (!level.getBlockState(beside).is(Blocks.NETHER_PORTAL)) continue;
				seen.add(beside);
				next.add(beside);
			}
		}
		return GlobalPos.of(level.dimension(), corner);
	}

	private static boolean before(BlockPos a, BlockPos b) {
		if (a.getY() != b.getY()) return a.getY() < b.getY();
		if (a.getX() != b.getX()) return a.getX() < b.getX();
		return a.getZ() < b.getZ();
	}

	static final class Pairs extends SavedData {
		private record Link(GlobalPos from, GlobalPos to) {
			static final Codec<Link> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				GlobalPos.CODEC.fieldOf("from").forGetter(Link::from),
				GlobalPos.CODEC.fieldOf("to").forGetter(Link::to)
			).apply(instance, Link::new));
		}

		private record Stored(List<Link> links, Optional<Boolean> chosen) {
			static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Link.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(Stored::links),
				Codec.BOOL.optionalFieldOf("chosen").forGetter(Stored::chosen)
			).apply(instance, Stored::new));
		}

		static final Codec<Pairs> CODEC = Stored.CODEC.xmap(Pairs::fromStored, Pairs::toStored);

		private static final SavedDataType<Pairs> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("pandorical", "portal_pairs"), Pairs::new, CODEC, DataFixTypes.LEVEL);

		final Map<GlobalPos, GlobalPos> links = new HashMap<>();
		/** Null until an op chooses. */
		Boolean chosen;

		static Pairs get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(TYPE);
		}

		/**
		 * The first pairing stands until an end breaks. A trip into an already-paired portal only
		 * records the way there, so nobody else's trip can move where yours returns.
		 */
		void link(GlobalPos a, GlobalPos b) {
			if (links.containsKey(a)) return;
			links.put(a, b);
			links.putIfAbsent(b, a);
			setDirty();
		}

		void unlink(GlobalPos a, GlobalPos b) {
			links.remove(a, b);
			links.remove(b, a);
			setDirty();
		}

		private static Pairs fromStored(Stored stored) {
			Pairs pairs = new Pairs();
			for (Link link : stored.links()) pairs.links.put(link.from(), link.to());
			pairs.chosen = stored.chosen().orElse(null);
			return pairs;
		}

		private Stored toStored() {
			List<Link> out = new ArrayList<>();
			for (Map.Entry<GlobalPos, GlobalPos> entry : links.entrySet()) out.add(new Link(entry.getKey(), entry.getValue()));
			return new Stored(out, Optional.ofNullable(chosen));
		}
	}
}
