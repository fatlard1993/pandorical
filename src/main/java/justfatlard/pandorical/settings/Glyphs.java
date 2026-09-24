package justfatlard.pandorical.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Default-font text widths, as near as a server can tell. A resource pack's font can differ, so
 * callers keep a few pixels spare.
 *
 * <p>Kept in {@code settings} because that is where it was first needed; nothing about it is
 * settings-specific, so anything in the mod may use it rather than measure a string its own way.
 */
public final class Glyphs {
    private Glyphs() {}

    private static final String ELLIPSIS = "...";
    private static final int[] ASCII = new int[128];
    private static final int OTHER = 7;

    static {
        Arrays.fill(ASCII, 6);
        for (char c : " It[]\"()*{}".toCharArray()) ASCII[c] = 4;
        for (char c : "<>fk".toCharArray()) ASCII[c] = 5;
        for (char c : "`l".toCharArray()) ASCII[c] = 3;
        for (char c : "!',.:;|i".toCharArray()) ASCII[c] = 2;
        for (char c : "@~".toCharArray()) ASCII[c] = 7;
    }

    public static int width(char c) {
        return c < 128 ? ASCII[c] : OTHER;
    }

    public static int width(CharSequence text) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) total += width(text.charAt(i));
        return total;
    }

    /** A word wider than {@code px} is broken. */
    public static List<String> wrap(String text, int px) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (line.length() > 0 && width(line) + width(' ') + width(word) > px) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (line.length() > 0 && width(line) + width(c) > px) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(c);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    /** Spacing kept, for code. */
    static List<String> cut(String text, int px) {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (piece.length() > 0 && width(piece) + width(c) > px) {
                pieces.add(piece.toString());
                piece.setLength(0);
            }
            piece.append(c);
        }
        pieces.add(piece.toString());
        return pieces;
    }

    public static String clip(String text, int px) {
        if (width(text) <= px) return text;
        int room = px - width(ELLIPSIS);
        int end = 0;
        int used = 0;
        while (end < text.length() && used + width(text.charAt(end)) <= room) {
            used += width(text.charAt(end++));
        }
        return text.substring(0, end).stripTrailing() + ELLIPSIS;
    }
}
