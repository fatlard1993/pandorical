package justfatlard.pandorical.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

/**
 * What is installed on this server, as a player would want to read it.
 *
 * <p>Every mod the loader knows, less the plumbing nobody installed on purpose: the game, the
 * loader, and Fabric API's own modules. Each comes with its name, version and description from
 * its manifest, and its readme if it shipped one in its jar - which every mod in this suite does,
 * as {@code README_<name>.md} beside its licence.
 */
public final class ModCatalog {
    private ModCatalog() {}

    /** One mod, ready to show. The readme is flattened markdown: a line per block, each knowing what it is. */
    public record ModInfo(String id, String name, String version, String authors, String description, List<Line> readme) {}

    /** What a line of readme is. */
    public enum Kind {
        /** {@code level} is the heading's depth, one for a title. */
        HEADING,
        /** A paragraph, joined from its source lines. */
        TEXT,
        /** A list item; {@code level} is how deep it is nested, from zero. */
        BULLET,
        /** A list item whose text starts with its number and a dot. */
        NUMBERED,
        /** One line of a fenced block, verbatim; a run of them is one block. */
        CODE,
        /** A quoted paragraph. */
        QUOTE,
        /** A horizontal rule; no text. */
        RULE
    }

    /** A block of readme, or a line of code. */
    public record Line(Kind kind, int level, String text) {
        public Line(Kind kind, String text) {
            this(kind, 0, text);
        }
    }

    private static List<ModInfo> cached;

    public static synchronized List<ModInfo> all() {
        if (cached == null) cached = load();
        return cached;
    }

    public static ModInfo find(String id) {
        for (ModInfo mod : all()) {
            if (mod.id().equals(id)) return mod;
        }
        return null;
    }

    private static List<ModInfo> load() {
        List<ModInfo> mods = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = container.getMetadata();
            if (plumbing(meta.getId())) continue;
            List<String> names = new ArrayList<>();
            for (Person person : meta.getAuthors()) names.add(person.getName());
            mods.add(new ModInfo(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(),
                String.join(", ", names), meta.getDescription(), readme(container)));
        }
        mods.sort(Comparator.comparing(mod -> mod.name().toLowerCase()));
        return mods;
    }

    private static boolean plumbing(String id) {
        return id.equals("minecraft") || id.equals("java") || id.equals("fabricloader") || id.equals("mixinextras")
            || id.startsWith("fabric-") || id.equals("fabric") || id.startsWith("fabric_");
    }

    /** The readme shipped in the jar root, flattened, or an empty list. */
    private static List<Line> readme(ModContainer container) {
        for (Path root : container.getRootPaths()) {
            try (Stream<Path> files = Files.list(root)) {
                Path found = files.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("README") && name.endsWith(".md");
                }).findFirst().orElse(null);
                if (found != null) return flatten(Files.readString(found, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // A jar without a listable root has no readme to show.
            }
        }
        return List.of();
    }

    /**
     * Markdown as blocks: headings with their depth, paragraphs joined, list items with their
     * nesting, fenced code kept verbatim, quotes and rules marked, tables turned into a list of
     * their rows. Images and raw HTML are left out; links keep their text.
     */
    static List<Line> flatten(String markdown) {
        List<Line> out = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        boolean fenced = false;
        boolean quoting = false;
        boolean tableRow = false;
        Runnable flush = () -> {
            if (paragraph.length() > 0) {
                out.add(new Line(Kind.TEXT, paragraph.toString()));
                paragraph.setLength(0);
            }
        };
        for (String raw : markdown.split("\r?\n")) {
            String line = raw.strip();
            if (line.startsWith("```")) {
                flush.run();
                fenced = !fenced;
                continue;
            }
            if (fenced) {
                out.add(new Line(Kind.CODE, raw.stripTrailing().replace("\t", "    ")));
                continue;
            }
            if (line.startsWith("![") || line.startsWith("<")) continue;
            if (!line.startsWith("|")) tableRow = false;
            if (line.isEmpty()) {
                flush.run();
                quoting = false;
                continue;
            }
            if (line.startsWith("#")) {
                flush.run();
                int depth = 0;
                while (depth < line.length() && line.charAt(depth) == '#') depth++;
                out.add(new Line(Kind.HEADING, depth, inline(line.substring(depth).strip())));
                continue;
            }
            if (line.matches("^([-*_])\\s*(\\1\\s*){2,}$")) {
                flush.run();
                out.add(new Line(Kind.RULE, ""));
                continue;
            }
            if (line.startsWith("|")) {
                flush.run();
                if (line.matches("^\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?$")) {
                    // The rule under a header row. Column names are for a grid, and these rows
                    // are read as a list, so the header goes with its rule.
                    if (tableRow && !out.isEmpty()) out.remove(out.size() - 1);
                    continue;
                }
                tableRow = true;
                List<String> cells = new ArrayList<>();
                for (String cell : line.substring(1, line.endsWith("|") ? line.length() - 1 : line.length()).split("\\|")) {
                    String text = inline(cell.strip());
                    if (!text.isEmpty()) cells.add(text);
                }
                if (!cells.isEmpty()) out.add(new Line(Kind.BULLET, 0, String.join("  \u00b7  ", cells)));
                continue;
            }
            if (line.startsWith(">")) {
                if (!quoting) flush.run();
                quoting = true;
                String text = inline(line.substring(1).strip());
                if (text.isEmpty()) {
                    flushQuote(out, paragraph);
                    continue;
                }
                if (paragraph.length() > 0) paragraph.append(' ');
                paragraph.append(text);
                continue;
            }
            if (quoting) {
                flushQuote(out, paragraph);
                quoting = false;
            }
            int indent = raw.length() - raw.stripLeading().length();
            if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) {
                flush.run();
                out.add(new Line(Kind.BULLET, indent / 2, inline(line.substring(2).strip())));
                continue;
            }
            if (line.matches("^\\d+[.)]\\s+.*")) {
                flush.run();
                int dot = line.indexOf(' ');
                out.add(new Line(Kind.NUMBERED, indent / 2, line.substring(0, dot).replace(')', '.') + " " + inline(line.substring(dot).strip())));
                continue;
            }
            if (paragraph.length() > 0) paragraph.append(' ');
            paragraph.append(inline(line));
        }
        if (quoting) flushQuote(out, paragraph);
        flush.run();
        return withoutEmptyHeadings(out);
    }

    /**
     * A heading with nothing under it before the next heading at its level or above, or the
     * end: a section that was only images or HTML, which are left out, and reads in-game as a
     * title over nothing.
     */
    private static List<Line> withoutEmptyHeadings(List<Line> lines) {
        List<Line> out = new ArrayList<>(lines);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < out.size(); i++) {
                Line line = out.get(i);
                if (line.kind() != Kind.HEADING) continue;
                Line next = i + 1 < out.size() ? out.get(i + 1) : null;
                if (next == null || next.kind() == Kind.HEADING && next.level() <= line.level()) {
                    out.remove(i);
                    changed = true;
                    break;
                }
            }
        }
        return out;
    }

    private static void flushQuote(List<Line> out, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            out.add(new Line(Kind.QUOTE, paragraph.toString()));
            paragraph.setLength(0);
        }
    }

    private static String inline(String text) {
        return text
            .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
            .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
            .replaceAll("`([^`]+)`", "$1")
            .replaceAll("(?<![\\w])_([^_]+)_(?![\\w])", "$1");
    }
}
