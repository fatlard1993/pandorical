package justfatlard.pandorical.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import justfatlard.pandorical.api.WorldRulesApi;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Optional;

/** Which small rules of the world are on, for the mixins to read live. See {@link WorldRulesApi}. */
public final class WorldRules implements WorldRulesApi {
	public static final WorldRules INSTANCE = new WorldRules();

	private WorldRules() {}

	public static final int MOST_RAIN_TICKS = 8;
	public static final int DEFAULT_RAIN_TICKS = 1;
	public static final int DEFAULT_UNATTENDED_PERCENT = 75;
	public static final int DEFAULT_REQUEST_SECONDS = 60;
	public static final int FEWEST_REQUEST_SECONDS = 10;
	public static final int MOST_REQUEST_SECONDS = 600;

	private static volatile boolean rainByDefault = false;
	private static volatile boolean unattendedByDefault = false;

	@Override
	public void rainGrowth(boolean byDefault) {
		rainByDefault = byDefault;
	}

	@Override
	public void unattendedKillXp(boolean byDefault) {
		unattendedByDefault = byDefault;
	}

	public static boolean rainGrowth(MinecraftServer server) {
		Boolean chosen = Choices.get(server).rain;
		return chosen != null ? chosen : rainByDefault;
	}

	public static int rainTicks(MinecraftServer server) {
		Integer chosen = Choices.get(server).rainTicks;
		return chosen != null ? chosen : DEFAULT_RAIN_TICKS;
	}

	public static boolean unattendedKillXp(MinecraftServer server) {
		Boolean chosen = Choices.get(server).unattended;
		return chosen != null ? chosen : unattendedByDefault;
	}

	public static int unattendedPercent(MinecraftServer server) {
		Integer chosen = Choices.get(server).unattendedPercent;
		return chosen != null ? chosen : DEFAULT_UNATTENDED_PERCENT;
	}

	public static int requestSeconds(MinecraftServer server) {
		Integer chosen = Choices.get(server).requestSeconds;
		return chosen != null ? chosen : DEFAULT_REQUEST_SECONDS;
	}

	public static void chooseRainGrowth(MinecraftServer server, boolean on) {
		Choices choices = Choices.get(server);
		choices.rain = on;
		choices.setDirty();
	}

	public static void chooseRainTicks(MinecraftServer server, int ticks) {
		Choices choices = Choices.get(server);
		choices.rainTicks = Mth.clamp(ticks, 0, MOST_RAIN_TICKS);
		choices.setDirty();
	}

	public static void chooseUnattendedKillXp(MinecraftServer server, boolean on) {
		Choices choices = Choices.get(server);
		choices.unattended = on;
		choices.setDirty();
	}

	public static void chooseUnattendedPercent(MinecraftServer server, int percent) {
		Choices choices = Choices.get(server);
		choices.unattendedPercent = Mth.clamp(percent, 0, 100);
		choices.setDirty();
	}

	public static void chooseRequestSeconds(MinecraftServer server, int seconds) {
		Choices choices = Choices.get(server);
		choices.requestSeconds = Mth.clamp(seconds, FEWEST_REQUEST_SECONDS, MOST_REQUEST_SECONDS);
		choices.setDirty();
	}

	/** An op's choices about these rules, kept with the world. Each is null until an op has chosen. */
	static final class Choices extends SavedData {
		private record Stored(Optional<Boolean> rain, Optional<Integer> rainTicks, Optional<Boolean> unattended,
				Optional<Integer> unattendedPercent, Optional<Integer> requestSeconds) {
			static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("rain_growth").forGetter(Stored::rain),
				Codec.INT.optionalFieldOf("rain_growth_ticks").forGetter(Stored::rainTicks),
				Codec.BOOL.optionalFieldOf("unattended_kill_xp").forGetter(Stored::unattended),
				Codec.INT.optionalFieldOf("unattended_kill_percent").forGetter(Stored::unattendedPercent),
				Codec.INT.optionalFieldOf("request_seconds").forGetter(Stored::requestSeconds)
			).apply(instance, Stored::new));
		}

		static final Codec<Choices> CODEC = Stored.CODEC.xmap(Choices::fromStored, Choices::toStored);
		private static final SavedDataType<Choices> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("pandorical", "world_rules"), Choices::new, CODEC, DataFixTypes.LEVEL);

		Boolean rain;
		Integer rainTicks;
		Boolean unattended;
		Integer unattendedPercent;
		Integer requestSeconds;

		/** The running server's, held weakly: asked on every random tick that lands in the rain. */
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
			choices.rain = stored.rain().orElse(null);
			choices.rainTicks = stored.rainTicks().orElse(null);
			choices.unattended = stored.unattended().orElse(null);
			choices.unattendedPercent = stored.unattendedPercent().orElse(null);
			choices.requestSeconds = stored.requestSeconds().orElse(null);
			return choices;
		}

		private Stored toStored() {
			return new Stored(Optional.ofNullable(rain), Optional.ofNullable(rainTicks), Optional.ofNullable(unattended),
				Optional.ofNullable(unattendedPercent), Optional.ofNullable(requestSeconds));
		}
	}
}
