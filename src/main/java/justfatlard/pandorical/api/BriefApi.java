package justfatlard.pandorical.api;

/**
 * What your mod is, for somebody who has just arrived and has never heard of it.
 *
 * <p>A new player joins a server running forty mods and is told none of them. They find out what is
 * here by walking into it, which works for a block that looks interesting and not at all for a
 * command, a key or a rule about how the world behaves. The brief is one pass over what is running,
 * put to them once, on their first visit.
 *
 * <p>One or two lines. This is the paragraph before the readme, not the readme: a player who wants
 * more is a click from it at the end of the brief, and a mod that says everything here says it to
 * somebody with no idea yet which parts they will care about.
 *
 * <pre>{@code
 * // At mod init.
 * PandoricalApi.brief().overview("amethyst-door",
 *     "Amethyst doors you can lock to everyone but the people you build with.");
 * }</pre>
 *
 * <p>Saying nothing is fine. A mod with no overview is described by the summary in its own
 * {@code fabric.mod.json}, which it already had to write, so the brief is never a list of names
 * with gaps in it. Declaring one replaces that summary for somebody who would rather say it in
 * their own words.
 *
 * <p>Shown once, on a player's first visit. What changed on later visits is
 * {@link ChangelogApi}'s business.
 */
public interface BriefApi {

	/**
	 * What your mod is, in a line or two a new player would understand.
	 *
	 * <p>Calling twice for the same mod replaces the overview rather than adding a second.
	 *
	 * @param modId your mod's id, as its {@code fabric.mod.json} gives it
	 * @param lines what it is, one line per sentence
	 */
	BriefApi overview(String modId, String... lines);
}
