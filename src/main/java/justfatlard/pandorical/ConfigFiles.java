package justfatlard.pandorical;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writing the files a player would miss.
 *
 * <p>Opening the real file to write into it empties it first, so a game that stops between those
 * two moments leaves nothing behind: a crash while saving action menus takes every menu on the
 * machine. Everything here is written beside the real file and moved over it once it is whole,
 * which the filesystem does in one step or not at all.
 *
 * <p>The other half is {@link #setAside}: a file that would not parse is kept, not overwritten.
 * Reading it as "empty" and then saving over it is how a damaged save becomes a lost one.
 */
public final class ConfigFiles {
	private ConfigFiles() {}

	/** What a save is called while it is still half a save. */
	private static final String PART = ".part";

	/** What a save is called once it has been found unreadable. */
	private static final String BROKEN = ".broken";

	/** Write text to a file, or leave the file as it was. */
	public static void write(Path file, String text) throws IOException {
		write(file, writer -> writer.write(text));
	}

	/** Write to a file through a writer, or leave the file as it was. */
	public static void write(Path file, Content content) throws IOException {
		Path parent = file.getParent();
		if (parent != null) Files.createDirectories(parent);
		Path part = file.resolveSibling(file.getFileName() + PART);
		try (Writer writer = Files.newBufferedWriter(part)) {
			content.writeTo(writer);
		}
		try {
			Files.move(part, file,
				StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			// Not every filesystem will promise it. The window this leaves is a great deal
			// narrower than the one it replaces, and the alternative is not saving at all.
			Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Move a file that could not be read out of the way, and say where it went.
	 *
	 * <p>Called when a save will not parse. Without it the next save writes over the only copy of
	 * whatever went wrong, and a player who could have been handed their file back is told
	 * nothing at all.
	 *
	 * @return the file it was moved to, or null if it could not be moved
	 */
	public static Path setAside(Path file) {
		Path broken = file.resolveSibling(file.getFileName() + BROKEN);
		try {
			Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
			return broken;
		} catch (IOException e) {
			Pandorical.LOGGER.warn("[pandorical] could not set {} aside", file.getFileName(), e);
			return null;
		}
	}

	/** What to write, once there is somewhere safe to write it. */
	@FunctionalInterface
	public interface Content {
		void writeTo(Writer writer) throws IOException;
	}
}
