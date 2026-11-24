package justfatlard.pandorical.settings;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import justfatlard.pandorical.protocol.ClientModAboutC2S;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class ModCatalog {
    private ModCatalog() {}

    public record ModInfo(String id, String name, String version, String authors, String description, List<Line> readme) {}

    public enum Kind {
        /** {@code level} is the depth, 1 for a title. */
        HEADING,
        TEXT,
        /** {@code level} is the nesting, from 0. */
        BULLET,
        /** Text starts with the number and a dot. */
        NUMBERED,
        /** One verbatim line; a run of them is one block. */
        CODE,
        QUOTE,
        RULE
    }

    public record Line(Kind kind, int level, String text) {
        public Line(Kind kind, String text) {
            this(kind, 0, text);
        }
    }

    private static List<ModInfo> cached;
    private static Map<String, ClientModAboutC2S.About> abouts = Map.of();

    public static synchronized List<ModInfo> all() {
        if (cached == null) cached = load();
        return cached;
    }

    /** Licence, contributors and links of a mod on this side, or null for one not installed here. */
    public static synchronized ClientModAboutC2S.About about(String id) {
        all();
        return abouts.get(id);
    }

    /**
     * Read off a mod's own manifest: its contact links, then any links it declared for Mod Menu, the
     * one other place mods keep them. The icon is a texture id, and empty unless the jar keeps it
     * under {@code assets/}, where the game's resources can reach it. Clipped to what
     * {@link ClientModAboutC2S} carries.
     */
    public static ClientModAboutC2S.About aboutOf(ModContainer container) {
        ModMetadata meta = container.getMetadata();
        Map<String, String> links = new LinkedHashMap<>();
        link(links, "Homepage", meta.getContact().get("homepage").orElse(null));
        link(links, "Issues", meta.getContact().get("issues").orElse(null));
        link(links, "Source", meta.getContact().get("sources").orElse(null));
        for (var entry : meta.getContact().asMap().entrySet()) link(links, label(entry.getKey()), entry.getValue());
        CustomValue modmenu = meta.getCustomValue("modmenu");
        if (modmenu != null && modmenu.getType() == CustomValue.CvType.OBJECT) {
            CustomValue declared = modmenu.getAsObject().get("links");
            if (declared != null && declared.getType() == CustomValue.CvType.OBJECT) {
                for (var entry : declared.getAsObject()) {
                    if (entry.getValue().getType() == CustomValue.CvType.STRING) {
                        link(links, label(entry.getKey()), entry.getValue().getAsString());
                    }
                }
            }
        }
        List<ClientModAboutC2S.Link> list = new ArrayList<>();
        for (var entry : links.entrySet()) {
            if (list.size() >= 16) break;
            list.add(new ClientModAboutC2S.Link(clip(entry.getValue(), 64), entry.getKey()));
        }
        List<String> contributors = new ArrayList<>();
        for (Person person : meta.getContributors()) contributors.add(person.getName());
        List<String> bundled = new ArrayList<>();
        for (ModContainer inner : container.getContainedMods()) {
            bundled.add(inner.getMetadata().getName() + " " + inner.getMetadata().getVersion().getFriendlyString());
        }
        bundled.sort(String.CASE_INSENSITIVE_ORDER);
        return new ClientModAboutC2S.About(clip(meta.getId(), 64), clip(String.join(", ", meta.getLicense()), 256),
            clip(String.join(", ", contributors), 1024), iconTexture(meta.getIconPath(32).orElse("")), list,
            clip(String.join(", ", bundled), 2048));
    }

    /** By url, so a link given both as a contact and to Mod Menu is listed once, under its first name. */
    private static void link(Map<String, String> links, String label, String url) {
        if (url == null || url.length() > 512) return;
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return;
        links.putIfAbsent(url, label);
    }

    /** {@code modmenu.donate} is Donate; Mod Menu's own keys carry its prefix. */
    private static String label(String key) {
        String bare = key.startsWith("modmenu.") ? key.substring("modmenu.".length()) : key;
        return bare.isEmpty() ? key : Character.toUpperCase(bare.charAt(0)) + bare.substring(1);
    }

    static String iconTexture(String path) {
        if (!path.startsWith("assets/")) return "";
        int slash = path.indexOf('/', "assets/".length());
        if (slash < 0) return "";
        Identifier id = Identifier.tryParse(path.substring("assets/".length(), slash) + ":" + path.substring(slash + 1));
        return id == null || id.toString().length() > 256 ? "" : id.toString();
    }

    private static String clip(String text, int most) {
        return text.length() <= most ? text : text.substring(0, most);
    }

    /**
     * Reads every mod's readme now, on a thread of its own, rather than on the server thread when
     * the first player arrives and is offered what's new.
     */
    public static void warmUp() {
        Thread.ofPlatform().daemon().name("pandorical-mod-catalog").start(ModCatalog::all);
    }

    public static ModInfo find(String id) {
        for (ModInfo mod : all()) {
            if (mod.id().equals(id)) return mod;
        }
        return null;
    }

    private static List<ModInfo> load() {
        List<ModInfo> mods = new ArrayList<>();
        Map<String, ClientModAboutC2S.About> read = new HashMap<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = container.getMetadata();
            // A jar nested in another mod is that mod's library, not something anyone installed.
            if (plumbing(meta.getId()) || container.getContainingMod().isPresent()) continue;
            List<String> names = new ArrayList<>();
            for (Person person : meta.getAuthors()) names.add(person.getName());
            mods.add(new ModInfo(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(),
                String.join(", ", names), meta.getDescription(), readme(container)));
            read.put(meta.getId(), aboutOf(container));
        }
        abouts = Map.copyOf(read);
        mods.sort(Comparator.comparing(mod -> mod.name().toLowerCase()));
        return mods;
    }

    private static boolean plumbing(String id) {
        return id.equals("minecraft") || id.equals("java") || id.equals("fabricloader") || id.equals("mixinextras")
            || id.startsWith("fabric-") || id.equals("fabric") || id.startsWith("fabric_");
    }

    /** A mod's readme, flattened, for a client reporting its own. */
    public static List<Line> readmeOf(ModContainer container) {
        return readme(container);
    }

    /** Ids that are the loader's plumbing rather than somebody's mod. */
    public static boolean isPlumbing(String id) {
        return plumbing(id);
    }

    private static List<Line> readme(ModContainer container) {
        for (Path root : container.getRootPaths()) {
            try (Stream<Path> files = Files.list(root)) {
                Path found = files.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("README") && name.endsWith(".md");
                }).findFirst().orElse(null);
                if (found != null) return flatten(Files.readString(found, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }
        return List.of();
    }

    /** Images and raw HTML are dropped; a table becomes a list of its rows. */
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
                    // Rows are read as a list, so the header row goes with the rule under it.
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

    /** Drops a heading whose section held only images or HTML. */
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
