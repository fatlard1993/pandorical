package justfatlard.pandorical.sleep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sleeping speeds the night up instead of skipping it: while players sleep, their dimension's clock runs
 * faster, the more of them asleep the faster, up to the speed an op sets, and it runs at its own speed
 * again once nobody is asleep. The clock's rate travels to clients with its time, so the sky turns
 * smoothly on any client, vanilla's included. Off until an op turns it on, and left alone when the
 * SleepWarp mod is installed.
 *
 * <p>A thunderstorm is still slept through the vanilla way, which skips to morning and clears it.
 */
public final class SleepSpeed {
	private SleepSpeed() {}

	public static final int DEFAULT_FASTEST = 40;
	public static final int MOST_FASTEST = 100;
	/** A rate this close to the wanted one is left, so a clock is not re-sent every tick. */
	private static final float CLOSE_ENOUGH = 0.05F;

	private static final boolean SLEEPWARP = FabricLoader.getInstance().isModLoaded("sleepwarp");

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(SleepSpeed::restoreLeftOver);
		ServerLifecycleEvents.SERVER_STOPPING.register(SleepSpeed::restoreLeftOver);
		ServerTickEvents.END_SERVER_TICK.register(SleepSpeed::tick);
	}

	public static boolean sleepWarpInstalled() {
		return SLEEPWARP;
	}

	public static boolean enabled(MinecraftServer server) {
		if (SLEEPWARP) return false;
		Boolean chosen = Choices.get(server).enabled;
		return chosen != null && chosen;
	}

	public static int fastest(MinecraftServer server) {
		Integer chosen = Choices.get(server).fastest;
		return chosen != null ? chosen : DEFAULT_FASTEST;
	}

	public static void choose(MinecraftServer server, boolean on) {
		Choices choices = Choices.get(server);
		choices.enabled = on;
		choices.setDirty();
	}

	public static void chooseFastest(MinecraftServer server, int fastest) {
		Choices choices = Choices.get(server);
		choices.fastest = Mth.clamp(fastest, 2, MOST_FASTEST);
		choices.setDirty();
	}

	/** For the level's tick: whether its night is sped up rather than skipped once enough players sleep. */
	public static boolean replacesSkip(ServerLevel level) {
		return enabled(level.getServer()) && !level.isThundering() && level.dimensionType().defaultClock().isPresent();
	}

	/** How fast a level's clock should run now: 1 when nobody there is asleep. */
	static float wantedRate(ServerLevel level, int fastest) {
		if (!replacesSkip(level)) return 1.0F;
		int players = 0, asleep = 0;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			players++;
			if (player.isSleepingLongEnough()) asleep++;
		}
		if (asleep == 0) return 1.0F;
		return 1.0F + (fastest - 1) * (asleep / (float) players);
	}

	private static void tick(MinecraftServer server) {
		if (SLEEPWARP) return;
		Choices choices = Choices.get(server);
		int fastest = fastest(server);
		// One clock may keep time for more than one level: it runs as fast as the fastest of them wants
		Map<Holder<WorldClock>, Float> wanted = new HashMap<>();
		for (ServerLevel level : server.getAllLevels()) {
			level.dimensionType().defaultClock().ifPresent(clock -> wanted.merge(clock, wantedRate(level, fastest), Math::max));
		}
		for (Map.Entry<Holder<WorldClock>, Float> entry : wanted.entrySet()) {
			Holder<WorldClock> clock = entry.getKey();
			String id = clock.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
			if (id == null) continue;
			boolean ours = choices.sped.contains(id);
			float rate = entry.getValue();
			// A clock nobody here sped up is left as it is, whatever an op or another mod set it to
			if (!ours && rate <= 1.0F) continue;
			float now = server.clockManager().getInstance(clock).rate();
			if (Math.abs(now - rate) > CLOSE_ENOUGH) server.clockManager().setRate(clock, rate);
			if (rate > 1.0F && !ours) {
				choices.sped.add(id);
				choices.setDirty();
			} else if (rate <= 1.0F && ours) {
				choices.sped.remove(id);
				choices.setDirty();
			}
		}
	}

	/**
	 * A clock's rate is kept with the world, so one sped up when the server stopped, or crashed, would
	 * stay fast through every day after. Put back at each start and each stop.
	 */
	public static void restoreLeftOver(MinecraftServer server) {
		// A server that failed to start still stops, with no world to read; throwing here would
		// skip every stop handler after this one
		if (server.overworld() == null) return;
		Choices choices = Choices.get(server);
		if (choices.sped.isEmpty()) return;
		var clocks = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK);
		for (String id : List.copyOf(choices.sped)) {
			Identifier location = Identifier.tryParse(id);
			if (location != null) {
				clocks.get(ResourceKey.create(Registries.WORLD_CLOCK, location))
					.ifPresent(clock -> server.clockManager().setRate(clock, 1.0F));
			}
			choices.sped.remove(id);
		}
		choices.setDirty();
	}

	/** An op's choices about sleeping, and which clocks are running fast, kept with the world. */
	static final class Choices extends SavedData {
		private record Stored(Optional<Boolean> enabled, Optional<Integer> fastest, List<String> sped) {
			static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("speeds_night").forGetter(Stored::enabled),
				Codec.INT.optionalFieldOf("fastest").forGetter(Stored::fastest),
				Codec.STRING.listOf().optionalFieldOf("sped_clocks", List.of()).forGetter(Stored::sped)
			).apply(instance, Stored::new));
		}

		static final Codec<Choices> CODEC = Stored.CODEC.xmap(Choices::fromStored, Choices::toStored);
		private static final SavedDataType<Choices> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("pandorical", "sleep"), Choices::new, CODEC, DataFixTypes.LEVEL);

		Boolean enabled;
		Integer fastest;
		final Set<String> sped = new LinkedHashSet<>();

		/** The running server's, held weakly: asked every tick. */
		private record Held(java.lang.ref.WeakReference<MinecraftServer> server, Choices choices) {}

		private static volatile Held held;

		static Choices get(MinecraftServer server) {
			Held now = held;
			if (now != null && now.server().get() == server) return now.choices();
			Choices choices = server.overworld().getDataStorage().computeIfAbsent(TYPE);
			held = new Held(new java.lang.ref.WeakReference<>(server), choices);
			return choices;
		}

		private static Choices fromStored(Stored stored) {
			Choices choices = new Choices();
			choices.enabled = stored.enabled().orElse(null);
			choices.fastest = stored.fastest().orElse(null);
			choices.sped.addAll(stored.sped());
			return choices;
		}

		private Stored toStored() {
			return new Stored(Optional.ofNullable(enabled), Optional.ofNullable(fastest), new ArrayList<>(sped));
		}
	}
}
