package justfatlard.pandorical.drops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import justfatlard.pandorical.api.DropsApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Optional;

/** Which of the drops fixes are on, for the mixins to read live. See {@link DropsApi}. */
public final class DropsPolicy implements DropsApi {
	public static final DropsPolicy INSTANCE = new DropsPolicy();

	private DropsPolicy() {}

	/** Vanilla's merge reach, in tenths of a block; at or below it the wider reach is off. */
	public static final int VANILLA_MERGE_TENTHS = 5;
	public static final int MOST_MERGE_TENTHS = 40;
	/** Vanilla's tracking range for items and orbs, 6 chunks. */
	public static final int MOST_TRACKING_BLOCKS = 96;

	private static final boolean CLUMPS = FabricLoader.getInstance().isModLoaded("clumps");
	private static final boolean GET_IT_TOGETHER = FabricLoader.getInstance().isModLoaded("getittogetherdrops");

	private static volatile boolean clumpByDefault = false;
	private static volatile int mergeTenthsByDefault = VANILLA_MERGE_TENTHS;
	private static volatile int trackingByDefault = 0;

	@Override
	public void clumpExperience(boolean byDefault) {
		clumpByDefault = byDefault;
	}

	@Override
	public void itemMergeRadius(double blocks) {
		mergeTenthsByDefault = mergeTenths((int) Math.round(blocks * 10));
	}

	@Override
	public void itemTrackingRange(int blocks) {
		trackingByDefault = trackingBlocks(blocks);
	}

	public static boolean clumpsInstalled() {
		return CLUMPS;
	}

	public static boolean getItTogetherInstalled() {
		return GET_IT_TOGETHER;
	}

	public static boolean clumping(MinecraftServer server) {
		if (CLUMPS) return false;
		Boolean chosen = Choices.get(server).clump;
		return chosen != null ? chosen : clumpByDefault;
	}

	/** In tenths of a block; {@link #VANILLA_MERGE_TENTHS} when the wider reach is off. */
	public static int mergeRadius(MinecraftServer server) {
		if (GET_IT_TOGETHER) return VANILLA_MERGE_TENTHS;
		Integer chosen = Choices.get(server).mergeTenths;
		return chosen != null ? chosen : mergeTenthsByDefault;
	}

	public static boolean widerMerge(MinecraftServer server) {
		return mergeRadius(server) > VANILLA_MERGE_TENTHS;
	}

	/** In blocks; 0 leaves vanilla's range. */
	public static int trackingRange(MinecraftServer server) {
		Integer chosen = Choices.get(server).trackingBlocks;
		return chosen != null ? chosen : trackingByDefault;
	}

	/** An op's choice, which from now on is the answer whatever any mod asked for. */
	public static void chooseClumping(MinecraftServer server, boolean on) {
		Choices choices = Choices.get(server);
		choices.clump = on;
		choices.setDirty();
	}

	public static void chooseMergeRadius(MinecraftServer server, int tenths) {
		Choices choices = Choices.get(server);
		choices.mergeTenths = mergeTenths(tenths);
		choices.setDirty();
	}

	public static void chooseTrackingRange(MinecraftServer server, int blocks) {
		Choices choices = Choices.get(server);
		choices.trackingBlocks = trackingBlocks(blocks);
		choices.setDirty();
	}

	private static int mergeTenths(int tenths) {
		return Mth.clamp(tenths, VANILLA_MERGE_TENTHS, MOST_MERGE_TENTHS);
	}

	private static int trackingBlocks(int blocks) {
		return Mth.clamp(blocks, 0, MOST_TRACKING_BLOCKS);
	}

	/** An op's choices about drops, kept with the world. Each is null until an op has chosen. */
	static final class Choices extends SavedData {
		private record Stored(Optional<Boolean> clump, Optional<Integer> mergeTenths, Optional<Integer> trackingBlocks) {
			static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("clump_experience").forGetter(Stored::clump),
				Codec.INT.optionalFieldOf("item_merge_tenths").forGetter(Stored::mergeTenths),
				Codec.INT.optionalFieldOf("item_tracking_blocks").forGetter(Stored::trackingBlocks)
			).apply(instance, Stored::new));
		}

		static final Codec<Choices> CODEC = Stored.CODEC.xmap(Choices::fromStored, Choices::toStored);

		private static final SavedDataType<Choices> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("pandorical", "drops"), Choices::new, CODEC, DataFixTypes.LEVEL);

		Boolean clump;
		Integer mergeTenths;
		Integer trackingBlocks;

		static Choices get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(TYPE);
		}

		private static Choices fromStored(Stored stored) {
			Choices choices = new Choices();
			choices.clump = stored.clump().orElse(null);
			choices.mergeTenths = stored.mergeTenths().orElse(null);
			choices.trackingBlocks = stored.trackingBlocks().orElse(null);
			return choices;
		}

		private Stored toStored() {
			return new Stored(Optional.ofNullable(clump), Optional.ofNullable(mergeTenths), Optional.ofNullable(trackingBlocks));
		}
	}
}
