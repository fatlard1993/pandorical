package justfatlard.pandorical.api;

/**
 * What changed in your mod, for the player who was not here when it changed.
 *
 * <p>A player comes back to a server that has moved on without them, and the only thing that ever
 * says so is a version number they never saw in the first place. This is where a mod says what it
 * did, in the words of somebody who plays it rather than somebody who wrote it.
 *
 * <p>Write the note for the player, not the changelog: <em>"Geodes you share now ask everyone in
 * the cluster before growing"</em> is worth reading, and <em>"refactor GeodeCommands, bump deps"</em>
 * is not. One or two lines per version is the size that gets read.
 *
 * <pre>{@code
 * // At mod init. Declare every version you have notes for, oldest or newest first, it does not
 * // matter: a player away for three releases is shown all three, in order.
 * PandoricalApi.changelog()
 *     .note("amethyst-door", "1.3.0", "Growing a shared geode now asks everyone in the cluster.")
 *     .note("amethyst-door", "1.2.0", "Geodes can be shared with the people you build with.");
 * }</pre>
 *
 * <p>A mod that declares nothing is not left out. Its version moving is still reported, as the two
 * versions it moved between, because a player is better served by "this changed and I do not know
 * how" than by silence. Declaring a note replaces that line for the versions it covers.
 *
 * <p>Nothing here is shown to a player joining for the first time: there is no "since" for them,
 * and forty mods introducing themselves is not a welcome.
 */
public interface ChangelogApi {

	/**
	 * One version's worth of news, in a sentence or two.
	 *
	 * <p>Calling twice for the same mod and version replaces the note rather than adding a second,
	 * so a reload cannot make a mod say everything twice.
	 *
	 * @param modId   your mod's id, as its {@code fabric.mod.json} gives it
	 * @param version the version this note is about, exactly as that version called itself
	 * @param lines   what a player would want to know, one line per sentence
	 */
	ChangelogApi note(String modId, String version, String... lines);
}
