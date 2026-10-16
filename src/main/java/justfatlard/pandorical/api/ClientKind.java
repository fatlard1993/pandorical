package justfatlard.pandorical.api;

/**
 * What kind of game a player joined with, decided in the configuration phase.
 *
 * <p>For logging and for telling an op what is connected. A mod does not branch on it: it calls the
 * same APIs for everyone, and Pandorical decides what each kind of game is shown.
 */
public enum ClientKind {
	/** Registered Pandorical's channels. */
	PANDORICAL,
	/** A Java game without Pandorical, or a server-side player with no game at all. */
	VANILLA_JAVA,
	/** A Bedrock game joined through Geyser, as a detector registered with {@link PandoricalApi#detectBedrock} says. */
	BEDROCK
}
