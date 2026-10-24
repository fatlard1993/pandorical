package justfatlard.pandorical.client.content;

import justfatlard.pandorical.Pandorical;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * A server's synced assets kept on disk by the SHA-256 of what they unpack to, so joining again
 * skips the download. Kept gzipped, as they arrived; a file is trusted only after it unpacks to
 * its own name, so a torn write or a hand edit costs one download and nothing worse.
 */
final class AssetCache {
    private AssetCache() {}

    /** One per server played on lately, and a spare for the version a server just moved off. */
    private static final int KEPT = 4;

    private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");

    private static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("pandorical").resolve("asset-cache");
    }

    /** The gzipped assets named by {@code sha}, or null when they are missing or not what the name says. */
    static byte[] load(String sha, long maxBytes) {
        if (!SHA.matcher(sha).matches()) return null;
        Path file = dir().resolve(sha + ".gz");
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > maxBytes) return null;
            byte[] gz = Files.readAllBytes(file);
            if (sha.equals(unpackedSha(gz, maxBytes))) {
                Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
                return gz;
            }
            Pandorical.LOGGER.warn("Cached assets {} do not unpack to their name; downloading them again", file.getFileName());
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Pandorical.LOGGER.warn("Could not read cached assets {}", file, e);
        }
        return null;
    }

    static void store(String sha, byte[] gz, long maxBytes) {
        if (!SHA.matcher(sha).matches()) return;
        Path dir = dir();
        try {
            if (!sha.equals(unpackedSha(gz, maxBytes))) {
                Pandorical.LOGGER.warn("Downloaded assets do not match the hash the server offered; not caching them");
                return;
            }
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, sha, ".part");
            Files.write(temp, gz);
            Files.move(temp, dir.resolve(sha + ".gz"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            prune(dir);
        } catch (IOException e) {
            Pandorical.LOGGER.warn("Could not cache assets in {}", dir, e);
        }
    }

    /** Null when the data unpacks past {@code maxBytes} or is not gzip at all. */
    private static String unpackedSha(byte[] gz, long maxBytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java has SHA-256", e);
        }
        try (InputStream in = new DigestInputStream(new GZIPInputStream(new ByteArrayInputStream(gz)), digest)) {
            byte[] buf = new byte[8192];
            long total = 0;
            for (int read; (read = in.read(buf)) != -1; ) {
                total += read;
                if (total > maxBytes) return null;
            }
        } catch (IOException e) {
            return null;
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void prune(Path dir) throws IOException {
        List<Path> kept;
        try (Stream<Path> files = Files.list(dir)) {
            kept = files.filter(path -> path.getFileName().toString().endsWith(".gz"))
                .sorted(Comparator.comparing(AssetCache::modified).reversed())
                .toList();
        }
        for (Path old : kept.subList(Math.min(KEPT, kept.size()), kept.size())) Files.deleteIfExists(old);
    }

    private static FileTime modified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }
}
