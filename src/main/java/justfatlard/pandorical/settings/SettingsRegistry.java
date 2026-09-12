package justfatlard.pandorical.settings;

import justfatlard.pandorical.api.Capabilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.pandorical.api.SettingsApi;
import justfatlard.pandorical.keybind.KeybindPool;
import justfatlard.pandorical.protocol.ComponentDef;
import justfatlard.pandorical.protocol.ComponentUpdate;
import justfatlard.pandorical.screen.Viewport;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Every mod's settings, and the one screen that shows them.
 *
 * <p>The screen is the mod menu: every mod installed down the left, and the chosen one on the
 * right with its description, its settings, and its readme. A toggle is a button that says On or
 * Off; a choice is a button that says the current option and cycles through them; a number is a
 * value between a minus and a plus. A press changes the value where it lives and re-labels the
 * control in place; only choosing another mod rebuilds the screen.
 *
 * <p>The settings sit in sections by whose they are: the player's own, kept here for them; the
 * player's own, kept by their client; and the server's, one value for everyone, which only an
 * op is shown.
 */
public final class SettingsRegistry implements SettingsApi {
    public static final String SCREEN_TYPE = "pandorical:settings";

    /** Whose a group's values are, in the order the sections are shown. */
    public enum Kind { PLAYER, CLIENT, SERVER }

    private static final int LIST_X = 8;
    private static final int LIST_Y = 20;
    private static final int LIST_ROW = 22;
    private static final int TABS_Y = 44;
    private static final int PANE_Y = 68;
    private static final int GUTTER = 8;
    private static final int LINE = 10;
    private static final int ROW = 24;
    private static final int CONTROL_W = 84;
    /** How far a list's entries sit in from its label. */
    private static final int INDENT = 8;
    /** Lines the pane will carry; past this the rest is left unsaid. */
    private static final int MOST_LINES = 800;
    private static final String HEADING_COLOR = "#303030";
    private static final String LABEL_COLOR = "#404040";
    private static final String HINT_COLOR = "#707070";
    /** The ops' colour: the server section's reminder, and the pill of a mod with server settings. */
    private static final String OPS_COLOR = "#B02828";
    /** The count bubble beside a mod's button: a quiet blue pill, white figure, against vanilla's grey. */
    private static final String BADGE_COLOR = "#FF4A76B8";
    private static final String OPS_BADGE_COLOR = "#FFB02828";
    /** The readme's furniture: rules under titles, the inset a code block sits in, a quote's bar. */
    private static final String RULE_COLOR = "#FF8C8C8C";
    private static final String CODE_BG_COLOR = "#FFB8B8B8";
    private static final String CODE_COLOR = "#1C2C3C";
    private static final String QUOTE_BAR_COLOR = "#FF9A9A9A";
    private static final String BADGE_TEXT_COLOR = "#FFFFFFFF";

    /**
     * The screen's measurements, cut to the window it will show in.
     *
     * <p>The least is what vanilla's smallest window holds, which every window can show. The most
     * is where a line of readme gets too long to read back across; a bigger window gets margins.
     */
    private record Layout(int width, int height, int listW, int paneX, int paneW, int listH, int paneH) {
        private static final int LEAST_W = 320;
        private static final int LEAST_H = 230;
        private static final int MOST_W = 560;
        private static final int MOST_H = 420;
        private static final int MARGIN = 8;

        static Layout fit(Viewport view) {
            int width = Math.clamp(view.width() - 2 * MARGIN, LEAST_W, MOST_W);
            int height = Math.clamp(view.height() - 2 * MARGIN, LEAST_H, MOST_H);
            int listW = Math.clamp(width * 3 / 10, 104, 150);
            int paneX = LIST_X + listW + GUTTER;
            return new Layout(width, height, listW, paneX, width - paneX - GUTTER, height - 52, height - PANE_Y - 32);
        }

        /** A line of prose: clear of the pane's scrollbar, with a little spare for another font. */
        int proseW() { return paneW - 12; }

        /** A setting's label and hint: what its control leaves. */
        int labelW(int controlW) { return paneW - controlW - 14; }

        /** The widest a control may grow for a long option; the label keeps enough to wrap in. */
        int mostControlW() { return paneW - 10 - 60; }
    }

    private final List<GroupImpl> groups = new ArrayList<>();
    private final Map<String, SettingImpl<?>> byId = new HashMap<>();
    /** The screen each player has open, so a press can re-label its control. */
    private final Map<UUID, String> openScreens = new ConcurrentHashMap<>();
    /** Which mod and tab each player is looking at, so a press on one keeps the other. */
    private record Shown(String mod, String tab) {}

    /** Which slot each player is waiting to press a key for, while the keys tab is open. */
    private final Map<UUID, Integer> rebinding = new ConcurrentHashMap<>();
    private final Map<UUID, Shown> shown = new ConcurrentHashMap<>();
    /**
     * How far down the mod list each player has scrolled, so the list a press rebuilds comes
     * back at the same place. The list tells us every time it moves; without this, choosing the
     * thirtieth mod put the list back at the top with the chosen one out of sight.
     */
    private final Map<UUID, Integer> listScroll = new ConcurrentHashMap<>();
    /** The same for the pane, which a change to a setting may rebuild mid-read. */
    private final Map<UUID, Integer> paneScroll = new ConcurrentHashMap<>();

    /** A player gone from the server: whatever their menu was showing goes with them. */
    public void forget(UUID player) {
        openScreens.remove(player);
        rebinding.remove(player);
        shown.remove(player);
        listScroll.remove(player);
        paneScroll.remove(player);
    }

    /** Wire the screen's actions and the keybind reports; once, after both APIs exist. */
    public void init() {
        PandoricalApi.screens().onActionFallback(SCREEN_TYPE, (player, data) -> press(player, data.get("_componentId"), data));
        PandoricalApi.screens().onAction(SCREEN_TYPE, "close", (player, data) -> {
            String id = openScreens.remove(player.getUUID());
            if (id != null) PandoricalApi.screens().close(player, id);
        });
        PandoricalApi.screens().onClose(SCREEN_TYPE, player -> {
            if (rebinding.remove(player.getUUID()) != null) {
                PandoricalApi.keybindsImpl().requestRebind(player, -1);
            }
            openScreens.remove(player.getUUID());
            shown.remove(player.getUUID());
            listScroll.remove(player.getUUID());
            paneScroll.remove(player.getUUID());
        });
        // Each report lays the keys tab out again, so only one that changed, or that a rebind is
        // waiting on, is worth the rebuild.
        PandoricalApi.keybindsImpl().onBindingsReported((player, changed) -> {
            if (changed || isRebinding(player)) refreshKeybinds(player);
        });
    }

    @Override
    public Group group(String modId, String modName) {
        return group(modId, modName, Kind.PLAYER);
    }

    @Override
    public Group serverGroup(String modId, String modName) {
        return group(modId, modName, Kind.SERVER);
    }

    /** A group whose values a player's own client keeps; see {@link ClientMods}. */
    public Group clientGroup(String modId, String modName) {
        return group(modId, modName, Kind.CLIENT);
    }

    private Group group(String modId, String modName, Kind kind) {
        for (GroupImpl group : groups) {
            if (group.modId.equals(modId) && group.kind == kind) return group;
        }
        GroupImpl group = new GroupImpl(modId, modName, kind);
        groups.add(group);
        return group;
    }

    /** The one bar for touching the server's own settings: the same one that runs its commands. */
    public static boolean isOp(ServerPlayer player) {
        return player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    public boolean groupIsServer(SettingImpl<?> setting) {
        return setting.group.server;
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public List<GroupImpl> groups() {
        return groups;
    }

    /** The setting called {@code mod:key}, or null. */
    public SettingImpl<?> find(String modId, String key) {
        return byId.get(modId + ":" + key);
    }

    /** The menu as the player left it if it is open now, since a resize asks for it again; else fresh. */
    @Override
    public void open(ServerPlayer player) {
        Shown was = openScreens.containsKey(player.getUUID()) ? shown.get(player.getUUID()) : null;
        open(player, was == null ? null : was.mod(), was == null ? null : was.tab());
    }

    public void open(ServerPlayer player, String selectedId) {
        open(player, selectedId, null);
    }

    /**
     * A mod's groups this player may see, in section order: those with a setting shown to
     * them right now, and server groups only for ops.
     */
    private List<GroupImpl> groupsFor(String modId, ServerPlayer player) {
        List<GroupImpl> out = new ArrayList<>();
        for (GroupImpl group : groups) {
            if (!group.modId.equals(modId) || group.shown(player).isEmpty()) continue;
            if (group.server && !isOp(player)) continue;
            out.add(group);
        }
        out.sort((a, b) -> a.kind.compareTo(b.kind));
        return out;
    }

    /**
     * The mod menu: every mod down the left, and the chosen one on the right with its
     * description, its settings if it registered any, and its readme.
     */
    public void open(ServerPlayer player, String selectedId, String tab) {
        Layout at = Layout.fit(Viewport.of(player));
        // The server's mods and this player's own client mods, one list, the client's marked.
        List<ModCatalog.ModInfo> mods = new ArrayList<>(ModCatalog.all());
        for (ModCatalog.ModInfo mine : ClientMods.of(player)) {
            if (mods.stream().noneMatch(mod -> mod.id().equals(mine.id()))) mods.add(mine);
        }
        mods.sort(java.util.Comparator.comparing(mod -> mod.name().toLowerCase()));
        ModCatalog.ModInfo selected = null;
        if (selectedId != null) {
            for (ModCatalog.ModInfo mod : mods) {
                if (mod.id().equals(selectedId)) selected = mod;
            }
        }
        if (selected == null) {
            // Start on something with a setting to change, else the first name.
            for (ModCatalog.ModInfo mod : mods) {
                if (!groupsFor(mod.id(), player).isEmpty()) { selected = mod; break; }
            }
            if (selected == null && !mods.isEmpty()) selected = mods.get(0);
        }
        // Every tab this mod has anything on, in reading order. A mod with no commands has no
        // commands tab: four buttons across a narrow pane leave no room for their own names, and
        // an empty tab is a press that tells the player nothing.
        List<String> tabs = new ArrayList<>();
        if (selected != null) {
            tabs.add("readme");
            if (!groupsFor(selected.id(), player).isEmpty()) tabs.add("settings");
            if (!ModCommands.of(selected.id(), player).isEmpty()) tabs.add("commands");
            if (!PandoricalApi.keybindsImpl().claimsOf(selected.id()).isEmpty()) tabs.add("keybinds");
        }
        // Settings first when there are some to change; the readme otherwise.
        String at_tab = tab != null && tabs.contains(tab) ? tab
            : tabs.contains("settings") ? "settings" : "readme";

        ScreenBuilder screen = new ScreenBuilder(SCREEN_TYPE).size(at.width, at.height).title("Mods");
        screen.panel("frame", 0, 0, at.width, at.height, Map.of());
        screen.component(new ComponentBuilder("title", ComponentType.TEXT)
            .bounds(LIST_X, 6, at.listW, 12)
            .prop(ComponentType.PROP_TEXT_KEY, "pandorical.mods.title")
            .prop(ComponentType.PROP_COLOR, HEADING_COLOR));

        // The list. A mod with settings this player may change wears a count of them beside
        // its button, so the ones worth opening can be told from the ones that are only a
        // readme without opening each in turn; the pill is red when any of them are the
        // server's. The button carries the whole name; the client fits it with the font it has.
        List<ComponentDef> names = new ArrayList<>();
        int buttonW = at.listW - 8;
        int y = 0;
        for (ModCatalog.ModInfo mod : mods) {
            boolean lit = selected != null && mod.id().equals(selected.id());
            int count = 0;
            boolean ops = false;
            for (GroupImpl group : groupsFor(mod.id(), player)) {
                count += group.shown(player).size();
                ops |= group.server;
            }
            String digits = String.valueOf(count);
            int badgeW = count > 0 ? 8 + digits.length() * 6 : 0;
            names.add(new ComponentBuilder("mod:" + mod.id(), ComponentType.BUTTON)
                .bounds(0, y, count > 0 ? buttonW - badgeW - 4 : buttonW, 20)
                .prop(ComponentType.PROP_LABEL, mod.name())
                .prop(ComponentType.PROP_STYLE, lit ? "pressed" : "default").build());
            if (count > 0) {
                int badgeX = buttonW - badgeW;
                names.add(new ComponentBuilder("badge:" + mod.id(), ComponentType.SPRITE)
                    .bounds(badgeX, y + 5, badgeW, 10)
                    .prop(ComponentType.PROP_COLOR, ops ? OPS_BADGE_COLOR : BADGE_COLOR).build());
                names.add(new ComponentBuilder("count:" + mod.id(), ComponentType.TEXT)
                    .bounds(badgeX, y + 6, badgeW, 10)
                    .prop(ComponentType.PROP_TEXT, digits)
                    .prop(ComponentType.PROP_ALIGN, "center")
                    .prop(ComponentType.PROP_SHADOW, "false")
                    .prop(ComponentType.PROP_COLOR, BADGE_TEXT_COLOR).build());
            }
            y += LIST_ROW;
        }
        int visible = at.listH / LIST_ROW;
        int offset = Math.max(0, Math.min(listScroll.getOrDefault(player.getUUID(), 0), mods.size() - visible));
        screen.scrollPanel("mods", LIST_X, LIST_Y, at.listW, at.listH, Map.of(
            "item_height", String.valueOf(LIST_ROW),
            "visible_items", String.valueOf(visible),
            "total_items", String.valueOf(mods.size()),
            "scroll_offset", String.valueOf(offset),
            "show_scrollbar", String.valueOf(mods.size() * LIST_ROW > at.listH)), names);

        // The chosen mod.
        if (selected != null) {
            screen.component(new ComponentBuilder("name", ComponentType.TEXT)
                .bounds(at.paneX, 20, at.paneW, 12)
                .prop(ComponentType.PROP_TEXT, Glyphs.clip(selected.name() + "  " + selected.version(), at.paneW))
                .prop(ComponentType.PROP_COLOR, HEADING_COLOR));
            screen.component(new ComponentBuilder("authors", ComponentType.TEXT)
                .bounds(at.paneX, 32, at.paneW, 12)
                .prop(ComponentType.PROP_TEXT, Glyphs.clip(selected.authors().isEmpty() ? selected.id() : selected.authors(), at.paneW))
                .prop(ComponentType.PROP_COLOR, HINT_COLOR));
            int gap = 4;
            int tabW = Math.min(70, (at.paneW - gap * (tabs.size() - 1)) / Math.max(1, tabs.size()));
            for (int i = 0; i < tabs.size(); i++) {
                String name = tabs.get(i);
                screen.button("tab:" + name, at.paneX + i * (tabW + gap), TABS_Y, tabW, 20, Map.of(
                    ComponentType.PROP_LABEL_KEY, "pandorical.mods." + name,
                    ComponentType.PROP_STYLE, name.equals(at_tab) ? "pressed" : "default"));
            }

            List<ComponentDef> about = new ArrayList<>();
            int height = switch (at_tab) {
                case "settings" -> settingsPane(player, selected, about, at);
                case "commands" -> commandsPane(player, selected, about, at);
                case "keybinds" -> keybindsPane(player, selected, about, at);
                default -> readmePane(selected, about, at);
            };
            // Laid out in lines, scrolled two at a time; the scrollbar's arithmetic is in lines too.
            int lines = (height + LINE - 1) / LINE;
            int paneOffset = Math.max(0, Math.min(paneScroll.getOrDefault(player.getUUID(), 0), lines - at.paneH / LINE));
            screen.scrollPanel("about", at.paneX, PANE_Y, at.paneW, at.paneH, Map.of(
                "item_height", String.valueOf(LINE),
                "visible_items", String.valueOf(at.paneH / LINE),
                "total_items", String.valueOf(lines),
                "scroll_offset", String.valueOf(paneOffset),
                "scroll_step", "2",
                "show_scrollbar", String.valueOf(lines > at.paneH / LINE)), about);
        }

        screen.button("close", at.width / 2 - 40, at.height - 26, 80, 20,
            Map.of(ComponentType.PROP_LABEL_KEY, "pandorical.settings.close"));

        openScreens.put(player.getUUID(), screen.screenId());
        shown.put(player.getUUID(), new Shown(selected == null ? null : selected.id(), at_tab));
        PandoricalApi.screens().open(player, screen.build());
    }

    /** Whether this player's keys tab is waiting on a key for one of its slots. */
    private boolean isRebinding(ServerPlayer player) {
        return rebinding.containsKey(player.getUUID());
    }

    /**
     * The client has just said what its keys are bound to. Anyone sitting on the keys tab is
     * shown the answer, and whatever they were waiting for has arrived.
     */
    private void refreshKeybinds(ServerPlayer player) {
        Shown was = shown.get(player.getUUID());
        if (was == null || !"keybinds".equals(was.tab())) {
            rebinding.remove(player.getUUID());
            return;
        }
        rebinding.remove(player.getUUID());
        open(player, was.mod(), was.tab());
    }

    /** What a section is called, and the line under it saying whose the values are. */
    private static String sectionTitle(Kind kind) {
        return switch (kind) {
            case PLAYER -> "Your settings";
            case CLIENT -> "Your client's settings";
            case SERVER -> "Server settings";
        };
    }

    private static String sectionHint(Kind kind) {
        return switch (kind) {
            case PLAYER -> "Yours alone, kept on this server.";
            case CLIENT -> "Yours alone, kept by your game wherever you play.";
            case SERVER -> "One value for everyone. Ops only.";
        };
    }

    /**
     * The commands tab: every form of every command the mod registered, one to a line, with the
     * ones an operator alone may run marked and gathered under their own heading.
     *
     * <p>A command is worth nothing unheard of, and until now the only way to learn a server
     * mod's commands was to type a slash and read the suggestions, which says the names and not
     * what they take.
     */
    private int commandsPane(ServerPlayer player, ModCatalog.ModInfo mod, List<ComponentDef> out, Layout at) {
        List<ModCommands.Entry> entries = ModCommands.of(mod.id(), player);
        if (entries.isEmpty()) {
            out.add(prose("none", 2, "This mod has no commands.", HINT_COLOR, at));
            return LINE + 2;
        }
        boolean op = isOp(player);
        int y = 2;
        int n = 0;
        for (boolean ops : new boolean[] {false, true}) {
            List<ModCommands.Entry> section = new ArrayList<>();
            for (ModCommands.Entry entry : entries) {
                if (entry.ops() == ops) section.add(entry);
            }
            if (section.isEmpty()) continue;
            out.add(prose("cmdhead:" + ops, y, ops ? "Operators only" : "Anyone", ops ? OPS_COLOR : HEADING_COLOR, at));
            y += LINE + 2;
            if (ops && !op) {
                for (String line : Glyphs.wrap("Listed so you know they exist; the server will refuse them.", at.proseW())) {
                    out.add(prose("cmdhint:" + n++, y, line, HINT_COLOR, at));
                    y += LINE;
                }
            }
            y += 2;
            for (ModCommands.Entry entry : section) {
                // Wrapped rather than clipped: a command with three arguments is longer than the
                // pane and the arguments are the half worth reading.
                List<String> lines = Glyphs.wrap(entry.usage(), at.proseW() - INDENT);
                for (int i = 0; i < lines.size(); i++) {
                    out.add(new ComponentBuilder("cmd:" + n++, ComponentType.TEXT)
                        .bounds(i == 0 ? 0 : INDENT, y, at.proseW(), LINE)
                        .prop(ComponentType.PROP_TEXT, lines.get(i))
                        .prop(ComponentType.PROP_COLOR, CODE_COLOR).build());
                    y += LINE;
                }
            }
            y += 6;
        }
        return y;
    }

    /**
     * The keys tab: the keybinds this mod claimed, what each is bound to now, and a button that
     * binds it to the next key pressed.
     *
     * <p>The pool is Pandorical's and the names are the server's, so the controls screen shows
     * them under one heading with no hint of which mod asked for what. Here they sit with the
     * mod they belong to, and can be changed without leaving the page.
     */
    private int keybindsPane(ServerPlayer player, ModCatalog.ModInfo mod, List<ComponentDef> out, Layout at) {
        var keybinds = PandoricalApi.keybindsImpl();
        List<KeybindPool.Claim> claims = keybinds.claimsOf(mod.id());
        if (claims.isEmpty()) {
            out.add(prose("none", 2, "This mod claims no keys.", HINT_COLOR, at));
            return LINE + 2;
        }
        int y = 2;
        int n = 0;
        boolean listening = PandoricalApi.hasCapability(player, Capabilities.KEYBINDS);
        if (!listening) {
            for (String line : Glyphs.wrap("Your client does not carry keybinds; these do nothing here.", at.proseW())) {
                out.add(prose("keyhint:" + n++, y, line, HINT_COLOR, at));
                y += LINE;
            }
            y += 4;
        }
        Integer waiting = rebinding.get(player.getUUID());
        for (KeybindPool.Claim claim : claims) {
            boolean asking = waiting != null && waiting == claim.slot();
            String bound = keybinds.bindingOf(player, claim.slot());
            out.add(new ComponentBuilder("keyname:" + claim.slot(), ComponentType.TEXT)
                .bounds(0, y + 5, at.proseW() - CONTROL_W - GUTTER, LINE)
                .prop(ComponentType.PROP_TEXT, Glyphs.clip(claim.displayName(), at.proseW() - CONTROL_W - GUTTER))
                .prop(ComponentType.PROP_COLOR, LABEL_COLOR).build());
            out.add(new ComponentBuilder("key:" + claim.slot(), ComponentType.BUTTON)
                .bounds(at.proseW() - CONTROL_W, y, CONTROL_W, 20)
                .prop(ComponentType.PROP_LABEL, asking ? "> Press a key <"
                    : bound == null ? (listening ? "..." : "Unknown") : bound.isEmpty() ? "Not bound" : bound)
                .prop(ComponentType.PROP_ENABLED, String.valueOf(listening))
                .prop(ComponentType.PROP_STYLE, asking ? "pressed" : "default").build());
            y += ROW;
        }
        y += 4;
        for (String line : Glyphs.wrap(waiting != null
                ? "Press the key you want, or Escape to leave it as it is."
                : "Press a key's button, then the key you want it on.", at.proseW())) {
            out.add(prose("keyfoot:" + n++, y, line, HINT_COLOR, at));
            y += LINE;
        }
        return y;
    }

    /**
     * The settings tab: a section per group the player may see, each headed with whose it is,
     * and a row per setting under it; or a line saying there are none.
     */
    private int settingsPane(ServerPlayer player, ModCatalog.ModInfo mod, List<ComponentDef> out, Layout at) {
        List<GroupImpl> sections = groupsFor(mod.id(), player);
        if (sections.isEmpty()) {
            out.add(prose("none", 2, "This mod has no settings.", HINT_COLOR, at));
            return LINE + 2;
        }
        int y = 2;
        int n = 0;
        for (GroupImpl group : sections) {
            String kind = group.kind.name().toLowerCase();
            out.add(prose("section:" + kind, y, sectionTitle(group.kind), HEADING_COLOR, at));
            y += LINE + 2;
            for (String line : Glyphs.wrap(sectionHint(group.kind), at.proseW())) {
                out.add(prose("hint:" + kind + ":" + n++, y, line, group.server ? OPS_COLOR : HINT_COLOR, at));
                y += LINE;
            }
            y += 4;
            for (SettingImpl<?> setting : group.shown(player)) {
                y += setting.rows(player, out, at, y);
            }
            y += 6;
        }
        return y;
    }

    /** The readme tab: the description, then the readme, block by block. */
    private int readmePane(ModCatalog.ModInfo mod, List<ComponentDef> out, Layout at) {
        Readme readme = new Readme(out, at);
        if (!mod.description().isEmpty()) readme.paragraph(mod.description(), LABEL_COLOR, 0);
        List<ModCatalog.Line> lines = mod.readme();
        // The title is the mod's name, which the pane already says above, with its version.
        int first = !lines.isEmpty() && lines.get(0).kind() == ModCatalog.Kind.HEADING && lines.get(0).level() == 1 ? 1 : 0;
        for (int i = first; i < lines.size(); i++) {
            if (readme.n >= MOST_LINES) {
                readme.text(0, readme.y, "\u2026", HINT_COLOR, at.proseW());
                readme.y += LINE;
                break;
            }
            ModCatalog.Line line = lines.get(i);
            switch (line.kind()) {
                case HEADING -> readme.heading(line.level(), line.text());
                case TEXT -> readme.paragraph(line.text(), LABEL_COLOR, 0);
                case BULLET -> readme.item("\u2022", line.level(), line.text());
                case NUMBERED -> {
                    int space = line.text().indexOf(' ');
                    readme.item(space < 0 ? line.text() : line.text().substring(0, space), line.level(),
                        space < 0 ? "" : line.text().substring(space + 1));
                }
                case QUOTE -> readme.quote(line.text());
                case RULE -> readme.rule();
                case CODE -> {
                    int end = i;
                    while (end + 1 < lines.size() && lines.get(end + 1).kind() == ModCatalog.Kind.CODE) end++;
                    readme.code(lines.subList(i, end + 1));
                    i = end;
                }
            }
        }
        if (out.isEmpty()) {
            readme.text(0, 2, "This mod shipped no readme.", HINT_COLOR, at.proseW());
            readme.y = LINE + 2;
        }
        return Math.max(readme.y, 1);
    }

    /**
     * Lays readme blocks down the pane. Each kind has its own shape: a title carries a rule
     * beneath it, a list item hangs its text off its marker, code sits in an inset with its
     * spacing kept, a quote has a bar down its side. The gaps between blocks are what make a
     * page of it readable; a wall is what it was without them.
     */
    private final class Readme {
        private static final int GAP = 5;
        private static final int INDENT = 10;
        private final List<ComponentDef> out;
        private final Layout at;
        int y;
        int n;

        Readme(List<ComponentDef> out, Layout at) {
            this.out = out;
            this.at = at;
        }

        void text(int x, int y, String text, String color, int width) {
            out.add(new ComponentBuilder("r" + n++, ComponentType.TEXT)
                .bounds(x, y, width, LINE)
                .prop(ComponentType.PROP_TEXT, text)
                .prop(ComponentType.PROP_COLOR, color).build());
        }

        void fill(int x, int y, int width, int height, String color) {
            out.add(new ComponentBuilder("r" + n++, ComponentType.SPRITE)
                .bounds(x, y, width, height)
                .prop(ComponentType.PROP_COLOR, color).build());
        }

        /** Wrapped lines at an indent; returns how many. */
        private int lines(String text, String color, int indent) {
            List<String> wrapped = Glyphs.wrap(text, at.proseW() - indent);
            for (String line : wrapped) {
                text(indent, y, line, color, at.proseW() - indent);
                y += LINE;
            }
            return wrapped.size();
        }

        void paragraph(String text, String color, int indent) {
            lines(text, color, indent);
            y += GAP;
        }

        void heading(int level, String text) {
            boolean title = level <= 2;
            if (y > 0) y += title ? 8 : 6;
            lines(text, HEADING_COLOR, 0);
            if (title) {
                fill(0, y + 1, at.proseW(), 1, RULE_COLOR);
                y += 3;
            }
            y += 4;
        }

        void item(String marker, int level, String text) {
            int indent = Math.min(level, 4) * INDENT;
            int hang = Glyphs.width(marker) + 4;
            text(indent, y, marker, LABEL_COLOR, hang);
            if (text.isEmpty()) {
                y += LINE;
            } else {
                lines(text, LABEL_COLOR, indent + hang);
            }
            y += 2;
        }

        void quote(String text) {
            int top = y;
            lines(text, HINT_COLOR, 8);
            fill(1, top, 2, y - top - 1, QUOTE_BAR_COLOR);
            y += GAP;
        }

        void rule() {
            y += 4;
            fill(0, y, at.proseW(), 1, RULE_COLOR);
            y += 6;
        }

        void code(List<ModCatalog.Line> block) {
            List<String> cut = new ArrayList<>();
            for (ModCatalog.Line line : block) cut.addAll(Glyphs.cut(line.text(), at.proseW() - 8));
            int height = 4 + cut.size() * LINE + 2;
            fill(0, y, at.proseW(), height, CODE_BG_COLOR);
            int lineY = y + 4;
            for (String line : cut) {
                if (!line.isEmpty()) text(4, lineY, line, CODE_COLOR, at.proseW() - 8);
                lineY += LINE;
            }
            y += height + GAP;
        }
    }

    private static ComponentDef prose(String id, int y, String text, String color, Layout at) {
        return new ComponentBuilder(id, ComponentType.TEXT)
            .bounds(0, y, at.proseW(), LINE)
            .prop(ComponentType.PROP_TEXT, text)
            .prop(ComponentType.PROP_COLOR, color).build();
    }

    /**
     * A press on a control: "set:", "dec:" or "inc:" followed by the setting's id, or "rem:"
     * followed by the setting's id and an entry's; "mod:" or "tab:" to rebuild the screen
     * around another mod or tab; or a panel saying it scrolled.
     */
    private void press(ServerPlayer player, String componentId, Map<String, String> data) {
        if (componentId == null) return;
        if (componentId.equals("mods") || componentId.equals("about")) {
            String scrolled = data.get("scroll_offset");
            if (scrolled != null) {
                try {
                    (componentId.equals("mods") ? listScroll : paneScroll).put(player.getUUID(), Integer.parseInt(scrolled));
                } catch (NumberFormatException ignored) {
                }
            }
            return;
        }
        int colon = componentId.indexOf(':');
        if (colon < 0) return;
        String verb = componentId.substring(0, colon);
        if (verb.equals("mod") || verb.equals("tab")) {
            // Opened over the old screen rather than after closing it. A close puts the cursor
            // back in the middle of the window - vanilla grabs the mouse when the screen goes
            // and lets it go again, centred, when the next one arrives - so every press on a
            // mod's name yanked the pointer away from the list. Opening straight over the top
            // swaps the screen with the cursor where it was.
            Shown was = shown.get(player.getUUID());
            String mod = verb.equals("mod") ? componentId.substring(colon + 1) : was == null ? null : was.mod();
            String tab = verb.equals("tab") ? componentId.substring(colon + 1) : was == null ? null : was.tab();
            paneScroll.remove(player.getUUID());
            open(player, mod, tab);
            return;
        }
        if (verb.equals("key")) {
            int slot;
            try {
                slot = Integer.parseInt(componentId.substring(colon + 1));
            } catch (NumberFormatException ignored) {
                return;
            }
            Integer waiting = rebinding.get(player.getUUID());
            boolean cancel = waiting != null && waiting == slot;
            if (cancel) rebinding.remove(player.getUUID());
            else rebinding.put(player.getUUID(), slot);
            PandoricalApi.keybindsImpl().requestRebind(player, cancel ? -1 : slot);
            Shown was = shown.get(player.getUUID());
            open(player, was == null ? null : was.mod(), was == null ? null : was.tab());
            return;
        }
        String rest = componentId.substring(colon + 1);
        String entry = null;
        if (verb.equals("rem")) {
            // rem:<mod>:<key>:<entry>, and the entry may have colons of its own.
            int key = rest.indexOf(':');
            int end = key < 0 ? -1 : rest.indexOf(':', key + 1);
            if (end < 0) return;
            entry = rest.substring(end + 1);
            rest = rest.substring(0, end);
        }
        SettingImpl<?> setting = byId.get(rest);
        if (setting == null) return;
        if (setting.group.server && !isOp(player)) return;
        if (!setting.visible(player)) return;

        switch (verb) {
            case "set" -> setting.cycle(player, 1);
            case "inc" -> setting.cycle(player, 1);
            case "dec" -> setting.cycle(player, -1);
            case "rem" -> {
                if (!(setting instanceof ListImpl list)) return;
                list.set(player, entry);
            }
            default -> { return; }
        }
        String screenId = openScreens.get(player.getUUID());
        if (screenId == null) return;
        if (setting.group.conditional() || setting instanceof ListImpl) {
            // Another setting may have just appeared or gone: the page is laid out again, at
            // the same place, rather than the one control re-labelled.
            Shown was = shown.get(player.getUUID());
            open(player, was == null ? null : was.mod(), was == null ? null : was.tab());
        } else {
            PandoricalApi.screens().update(player, screenId, setting.relabel(player));
        }
    }

    public final class GroupImpl implements Group {
        public final String modId;
        public final String modName;
        public final Kind kind;
        public final boolean server;
        public final List<SettingImpl<?>> settings = new ArrayList<>();

        GroupImpl(String modId, String modName, Kind kind) {
            this.modId = modId;
            this.modName = modName;
            this.kind = kind;
            this.server = kind == Kind.SERVER;
        }

        private <T> SettingImpl<T> add(SettingImpl<T> setting) {
            settings.add(setting);
            byId.put(setting.id(), setting);
            return setting;
        }

        /** The settings shown to this player right now, in order. */
        List<SettingImpl<?>> shown(ServerPlayer player) {
            List<SettingImpl<?>> out = new ArrayList<>();
            for (SettingImpl<?> setting : settings) {
                if (setting.visible(player)) out.add(setting);
            }
            return out;
        }

        /** Whether any setting here can come and go. */
        boolean conditional() {
            for (SettingImpl<?> setting : settings) {
                if (!setting.conditions.isEmpty()) return true;
            }
            return false;
        }

        @Override
        public Setting<Boolean> toggle(String key, String label, boolean fallback) {
            return add(new SettingImpl<>(this, key, label, fallback) {
                @Override String encode(Boolean v) { return String.valueOf(v); }
                @Override Boolean decode(String s) { return Boolean.parseBoolean(s); }
                @Override String display(Boolean v) { return v ? "pandorical.settings.on" : "pandorical.settings.off"; }
                @Override boolean displayIsKey() { return true; }
                @Override Boolean next(Boolean v, int direction) { return !v; }
                @Override public Boolean parse(String s) {
                    return switch (s.toLowerCase(java.util.Locale.ROOT)) {
                        case "true", "on" -> true;
                        case "false", "off" -> false;
                        default -> null;
                    };
                }
                @Override String accepts() { return "on or off"; }
            });
        }

        @Override
        public Setting<String> choice(String key, String label, Map<String, String> options, String fallback) {
            Map<String, String> ordered = new LinkedHashMap<>(options);
            List<String> ids = new ArrayList<>(ordered.keySet());
            return add(new SettingImpl<>(this, key, label, fallback) {
                @Override String encode(String v) { return v; }
                @Override String decode(String s) { return ordered.containsKey(s) ? s : fallback; }
                @Override String display(String v) { return ordered.getOrDefault(v, v); }
                @Override int controlWidth(int most) {
                    int widest = 0;
                    for (String label : ordered.values()) widest = Math.max(widest, Glyphs.width(label));
                    return Math.clamp(widest + 12, CONTROL_W, most);
                }
                @Override String next(String v, int direction) {
                    int at = ids.indexOf(v);
                    return ids.get(Math.floorMod(at + direction, ids.size()));
                }
                @Override public String parse(String s) { return ordered.containsKey(s) ? s : null; }
                @Override String accepts() { return String.join(", ", ids); }
            });
        }

        @Override
        public Setting<String> list(String key, String label, Function<ServerPlayer, Map<String, String>> entries,
                BiConsumer<ServerPlayer, String> remove) {
            return add(new ListImpl(this, key, label, entries, remove));
        }

        @Override
        public Setting<Integer> number(String key, String label, int min, int max, int step, int fallback) {
            return add(new SettingImpl<>(this, key, label, fallback) {
                @Override String encode(Integer v) { return String.valueOf(v); }
                @Override Integer decode(String s) {
                    try { return Math.clamp(Integer.parseInt(s), min, max); } catch (NumberFormatException e) { return fallback; }
                }
                @Override String display(Integer v) { return String.valueOf(v); }
                @Override Integer next(Integer v, int direction) { return Math.clamp(v + direction * step, min, max); }
                @Override boolean stepped() { return true; }
                @Override public Integer parse(String s) {
                    try { return Math.clamp(Integer.parseInt(s), min, max); } catch (NumberFormatException e) { return null; }
                }
                @Override String accepts() { return "a number from " + min + " to " + max; }
            });
        }
    }

    public abstract class SettingImpl<T> implements Setting<T> {
        final GroupImpl group;
        final String key;
        final String label;
        final T fallback;
        String description;
        private Function<ServerPlayer, T> getter;
        private BiConsumer<ServerPlayer, T> setter;
        private final List<BiConsumer<ServerPlayer, T>> listeners = new ArrayList<>();
        final List<Predicate<ServerPlayer>> conditions = new ArrayList<>();

        SettingImpl(GroupImpl group, String key, String label, T fallback) {
            this.group = group;
            this.key = key;
            this.label = label;
            this.fallback = fallback;
        }

        public String id() {
            return group.modId + ":" + key;
        }

        public String label() {
            return label;
        }

        abstract String encode(T value);
        abstract T decode(String stored);
        /** What the control says for this value: text, or a lang key when {@link #displayIsKey()}. */
        abstract String display(T value);
        abstract T next(T value, int direction);

        boolean displayIsKey() { return false; }
        boolean stepped() { return false; }

        /**
         * How wide the control is drawn: the usual, unless what it may have to say is wider,
         * up to {@code most}. The client shrinks a label that still does not fit.
         */
        int controlWidth(int most) { return CONTROL_W; }

        /** A value typed at a command, or null if it is not one this setting takes. */
        public T parse(String typed) {
            return decode(typed);
        }

        /** What {@link #parse} takes, said for a player who typed something else; empty when there is nothing to list. */
        String accepts() {
            return "";
        }

        /** The value as it would be shown, for a command listing. */
        public String shown(ServerPlayer player) {
            T value = get(player);
            return displayIsKey() ? encode(value) : display(value);
        }

        @Override
        public T get(ServerPlayer player) {
            if (getter != null) return getter.apply(player);
            String stored = group.server
                ? ServerSettings.get(player.level().getServer()).get(id())
                : PlayerSettings.get(player.level().getServer()).get(player.getUUID(), id());
            return stored == null ? fallback : decode(stored);
        }

        @Override
        public void set(ServerPlayer player, T value) {
            if (setter != null) {
                setter.accept(player, value);
            } else if (group.server) {
                ServerSettings.get(player.level().getServer()).put(id(), encode(value));
            } else {
                PlayerSettings.get(player.level().getServer()).put(player.getUUID(), id(), encode(value));
            }
            changed(player, value);
        }

        /** Tell the listeners. */
        void changed(ServerPlayer player, T value) {
            for (BiConsumer<ServerPlayer, T> listener : listeners) listener.accept(player, value);
        }

        void cycle(ServerPlayer player, int direction) {
            set(player, next(get(player), direction));
        }

        @Override
        public Setting<T> onChange(BiConsumer<ServerPlayer, T> listener) {
            listeners.add(listener);
            return this;
        }

        @Override
        public Setting<T> describe(String description) {
            this.description = description;
            return this;
        }

        @Override
        public Setting<T> backedBy(Function<ServerPlayer, T> getter, BiConsumer<ServerPlayer, T> setter) {
            this.getter = getter;
            this.setter = setter;
            return this;
        }

        @Override
        public Setting<T> shownWhen(Predicate<ServerPlayer> condition) {
            conditions.add(condition);
            return this;
        }

        @Override
        public Setting<T> shownWith(String modId) {
            boolean loaded = FabricLoader.getInstance().isModLoaded(modId);
            return shownWhen(player -> loaded);
        }

        @Override
        public <V> Setting<T> shownWhen(Setting<V> other, V value) {
            return shownWhen(player -> Objects.equals(other.get(player), value));
        }

        /** Whether this player is shown the setting right now. */
        public boolean visible(ServerPlayer player) {
            for (Predicate<ServerPlayer> condition : conditions) {
                if (!condition.test(player)) return false;
            }
            return true;
        }

        private Map<String, String> labelProps(ServerPlayer player) {
            String shown = display(get(player));
            return Map.of(displayIsKey() ? ComponentType.PROP_LABEL_KEY : ComponentType.PROP_LABEL, shown);
        }

        /**
         * The setting's rows on the page, panel-relative from {@code y}; returns their height.
         *
         * <p>The label and its hint stack down the left of the control, as many lines as they
         * take; a lone one-line label sits level with the control instead.
         */
        int rows(ServerPlayer player, List<ComponentDef> out, Layout at, int y) {
            int controlW = controlWidth(at.mostControlW());
            int labelW = at.labelW(controlW);
            List<String> labelLines = Glyphs.wrap(label, labelW);
            List<String> hint = description == null ? List.of() : Glyphs.wrap(description, labelW);
            int lines = labelLines.size() + hint.size();
            int textY = lines == 1 ? y + 6 : y + 1;
            for (int i = 0; i < lines; i++) {
                boolean isLabel = i < labelLines.size();
                out.add(new ComponentBuilder((isLabel ? "label:" : "hint:") + id() + ":" + i, ComponentType.TEXT)
                    .bounds(0, textY + i * LINE, labelW, LINE)
                    .prop(ComponentType.PROP_TEXT, isLabel ? labelLines.get(i) : hint.get(i - labelLines.size()))
                    .prop(ComponentType.PROP_COLOR, isLabel ? LABEL_COLOR : HINT_COLOR).build());
            }
            out.addAll(controls(player, at.paneW - 10, y, controlW));
            return Math.max(ROW, 1 + lines * LINE + 3);
        }

        /** The control(s) for this setting, panel-relative, with the control's right edge at {@code right}. */
        List<ComponentDef> controls(ServerPlayer player, int right, int y, int width) {
            List<ComponentDef> out = new ArrayList<>();
            if (stepped()) {
                out.add(new ComponentBuilder("dec:" + id(), ComponentType.BUTTON)
                    .bounds(right - CONTROL_W, y, 20, 20).prop(ComponentType.PROP_LABEL, "-").build());
                out.add(new ComponentBuilder("val:" + id(), ComponentType.TEXT)
                    .bounds(right - CONTROL_W + 22, y + 6, CONTROL_W - 44, 12)
                    .prop(ComponentType.PROP_TEXT, display(get(player)))
                    .prop(ComponentType.PROP_COLOR, LABEL_COLOR)
                    .prop(ComponentType.PROP_ALIGN, "center").build());
                out.add(new ComponentBuilder("inc:" + id(), ComponentType.BUTTON)
                    .bounds(right - 20, y, 20, 20).prop(ComponentType.PROP_LABEL, "+").build());
            } else {
                out.add(new ComponentBuilder("set:" + id(), ComponentType.BUTTON)
                    .bounds(right - width, y, width, 20).props(labelProps(player)).build());
            }
            return out;
        }

        /** The update that shows the value it has now. */
        List<ComponentUpdate> relabel(ServerPlayer player) {
            if (stepped()) {
                return List.of(new ComponentUpdate("val:" + id(), Map.of(ComponentType.PROP_TEXT, display(get(player)))));
            }
            return List.of(new ComponentUpdate("set:" + id(), labelProps(player)));
        }
    }

    /**
     * A list the player prunes: its label and hint as a heading, then a row per entry with a
     * button that takes it off. There is no value to cycle; a press rebuilds the page without
     * the row, and {@code set} with an entry's id is the same removal for the command.
     */
    public final class ListImpl extends SettingImpl<String> {
        private static final int REMOVE_W = 56;
        private static final int ENTRY_ROW = 22;
        private final Function<ServerPlayer, Map<String, String>> entries;
        private final BiConsumer<ServerPlayer, String> remove;

        ListImpl(GroupImpl group, String key, String label, Function<ServerPlayer, Map<String, String>> entries,
                BiConsumer<ServerPlayer, String> remove) {
            super(group, key, label, "");
            this.entries = entries;
            this.remove = remove;
        }

        @Override String encode(String v) { return v; }
        @Override String decode(String s) { return s; }
        @Override String display(String v) { return v; }
        @Override String next(String v, int direction) { return v; }

        @Override
        public String get(ServerPlayer player) {
            return String.join(", ", entries.apply(player).keySet());
        }

        @Override
        public void set(ServerPlayer player, String entry) {
            if (entry == null || !entries.apply(player).containsKey(entry)) return;
            remove.accept(player, entry);
            changed(player, entry);
        }

        @Override
        public String parse(String typed) {
            return typed.isEmpty() ? null : typed;
        }

        @Override
        void cycle(ServerPlayer player, int direction) {}

        @Override
        int rows(ServerPlayer player, List<ComponentDef> out, Layout at, int y) {
            int top = y;
            int wide = at.proseW();
            for (String line : Glyphs.wrap(label, wide)) {
                out.add(new ComponentBuilder("label:" + id() + ":" + (y - top), ComponentType.TEXT)
                    .bounds(0, y + 1, wide, LINE)
                    .prop(ComponentType.PROP_TEXT, line)
                    .prop(ComponentType.PROP_COLOR, LABEL_COLOR).build());
                y += LINE;
            }
            if (description != null) {
                for (String line : Glyphs.wrap(description, wide)) {
                    out.add(new ComponentBuilder("hint:" + id() + ":" + (y - top), ComponentType.TEXT)
                        .bounds(0, y + 1, wide, LINE)
                        .prop(ComponentType.PROP_TEXT, line)
                        .prop(ComponentType.PROP_COLOR, HINT_COLOR).build());
                    y += LINE;
                }
            }
            y += 3;
            Map<String, String> shown = entries.apply(player);
            if (shown.isEmpty()) {
                out.add(new ComponentBuilder("empty:" + id(), ComponentType.TEXT)
                    .bounds(INDENT, y + 1, wide - INDENT, LINE)
                    .prop(ComponentType.PROP_TEXT, "Nothing here.")
                    .prop(ComponentType.PROP_COLOR, HINT_COLOR).build());
                y += LINE + 3;
                return y - top;
            }
            int right = at.paneW - 10;
            int nameW = right - REMOVE_W - 6 - INDENT;
            for (Map.Entry<String, String> entry : shown.entrySet()) {
                out.add(new ComponentBuilder("entry:" + id() + ":" + entry.getKey(), ComponentType.TEXT)
                    .bounds(INDENT, y + 6, nameW, LINE)
                    .prop(ComponentType.PROP_TEXT, Glyphs.clip(entry.getValue(), nameW))
                    .prop(ComponentType.PROP_COLOR, LABEL_COLOR).build());
                out.add(new ComponentBuilder("rem:" + id() + ":" + entry.getKey(), ComponentType.BUTTON)
                    .bounds(right - REMOVE_W, y, REMOVE_W, 20)
                    .prop(ComponentType.PROP_LABEL_KEY, "pandorical.settings.remove").build());
                y += ENTRY_ROW;
            }
            return y - top + 2;
        }
    }
}
