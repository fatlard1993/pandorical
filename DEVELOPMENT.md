# Pandorical Development

Pandorical is a bridge, not a content mod. Your mod runs entirely server-side and
*describes* what it wants: a screen, an overlay, a block, a keybind. Pandorical carries
that description to the client and renders it. Your mod ships no client code, no client
entrypoint, and no mixins.

The bet is the inverse of the usual one. You require players to have Pandorical
installed, and in exchange you get real UI instead of whatever can be disguised as a
vanilla container.

Everything below is also demonstrated running, in one place:
[pandorical-demo](https://github.com/fatlard1993/pandorical-demo) puts every component
type in a single screen and every world capability in a single frame. Clone it beside
this repo and `./gradlew runClient` if you would rather read a working screen than a
description of one.

## Adding it as a dependency

Pandorical is not published to a Maven repository. Either way, a mod compiles against a
checkout of Pandorical beside its own, and which way depends on whether the mod runs without it.

**A mod that needs Pandorical** includes it as a Gradle subproject and compiles against its
working tree, so an API change surfaces as a compile error at once:

```groovy
// settings.gradle
include ':pandorical'
project(':pandorical').projectDir = new File(rootProject.projectDir, '../pandorical')
```

```groovy
// build.gradle
dependencies {
    implementation(project(':pandorical'))
}
```

```json
// fabric.mod.json
"depends": {
    "pandorical": ">=15.0.0"
}
```

Set the floor to the first version with every API the mod calls. `"*"` loads against any
Pandorical and fails at the first call to something it does not have.

**The major version is the protocol, and the protocol is the gate.** Pandorical 15 serves every
client speaking protocol 15 or above and turns the rest away with a message naming the version to
install. A new word is a new channel, which an older client never registers and so never receives,
and a changed payload is a new channel beside the old one; neither refuses anybody, so neither
moves the major. It moves when a channel already in players' hands is dropped, which is the one
change that makes an existing client useless. So a floor of `>=15.0.0` says what it means: this mod
needs a Pandorical that still speaks 15.

The minor version carries new words, and the patch fixes. A mod calling an API added in 15.4 sets
`>=15.4.0`; one that only needs the basics can sit at `>=15.0.0` and keep working across the whole
15 line.

**A mod that only uses Pandorical when it is there** compiles against the built jar and applies
`gradle/dependent.gradle`, which finds the jar by the version Pandorical declares and refuses one
older than Pandorical's source:

```groovy
// build.gradle
apply from: file("../pandorical/gradle/dependent.gradle")
```

```json
// fabric.mod.json
"suggests": {
    "pandorical": "*"
}
```

Build Pandorical first (`./gradlew build -p ../pandorical`), and keep everything that touches its
types behind `FabricLoader.isModLoaded("pandorical")`.

Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and
`fabric.mod.json` (Java). Match them.

## Strings that fail silently

Three string shapes in this API look right and are not. Get any of them wrong and
nothing throws, nothing logs, and the feature is absent. That is why they come
before anything else.

**Capability strings are a fixed list**, the constants in `Capabilities`. A client declares
`Capabilities.CLIENT`, and nothing else is ever sent in the handshake:

`screens` · `content` · `camera` · `hud` · `structures` · `entity_overlays` ·
`chest_overlays` · `keybinds` · `hud_elements` · `skins` · `render_policy` ·
`animations` · `mount_policy` · `walkable_structures`

Pass the constant, `hasCapability(player, Capabilities.SCREENS)`, and a typo is a compile
error. `hasCapability(player, "blockTints")` returns false forever, because `blockTints` is an
API surface, not a capability. Player inventory slots and buttons, block tints, block marks,
banner decals, pictures, map reliefs, action menus, keepsakes, and built-in entity renderers have
no capability string and are not guarded that way; each is sent only to a client that registered
its channel, which Fabric answers with `ServerPlayNetworking.canSend`.

`Capabilities.Server.SETTINGS` and `Capabilities.Server.BLOCK_MARKS` are a server's announcements
about itself, for its clients to read. They are not client capabilities, so
`hasCapability(player, ...)` is false for them for every player, and says so in the log.

**Chest overlay ids keep their atlas prefix.** `minecraft:christmas` is not a sprite;
`minecraft:entity/chest/christmas` is. The wrong one draws magenta and logs nothing. See
[Chest overlays](#chest-overlays).

**Screen types and screen IDs are different keys.** Handlers register against the
*screen type* you passed to the `ScreenBuilder` constructor. Updates and closes address
a *screen ID*, which is generated per screen instance:

```java
screens.onAction("mymod:hello", "ok", handler);        // screen TYPE
screens.update(player, screenId, updates);             // screen ID
screens.close(player, screenId);                       // screen ID
```

A handler does not receive the ID. Get it from the player:

```java
String screenId = PandoricalApi.getOpenScreenId(player.getUUID());
if (screenId == null) return;
```

## Older clients

The whole point of this mod is that your mod ships without anybody reinstalling anything, so the
client in a player's hands is always older than the server it joins. What that client does with a
word it has never heard is therefore part of the contract, not an accident.

**A client below the gate is turned away, by name.** It leaves during configuration with "This
server needs Pandorical *version*", rather than dying on the first payload it cannot read.

**A client above the gate takes what it knows and skips the rest.** An unknown component type
draws nothing and its children draw as usual; set `ComponentType.PROP_FALLBACK` to name a type an
older client does have, and it draws that instead:

```java
new ComponentBuilder("dial", ComponentType.DIAL)
    .prop(ComponentType.PROP_FALLBACK, ComponentType.PANEL)
```

The same holds elsewhere: an unknown camera hint, perspective, animation target, chest overlay op,
tint type, tool kind or HUD element is skipped and logged once, never guessed at. A spec with more
fields than the client expects keeps the fields it knows.

**Ask the channel, not the version.** `ServerPlayNetworking.canSend(player, SomePayload.TYPE)` is
the only answer that cannot be wrong: a client registers a channel exactly when it has the code
that reads it. A capability string can be older than the feature it appears to cover, which is how
keybind rebinding once offered a button that did nothing.

**The server trims rather than kicks.** An encode failure disconnects the player it was aimed at,
so an overlong label or mark is shortened and logged once, naming the value. Check your own lengths
if the exact string matters.

**The client tells you what it could not do.** Your mod runs where you can see it and draws where
you cannot, so a word an older client lacks would otherwise be a feature that is absent,
with the only explanation sitting in a log on someone else's machine. Instead the client reports it
back, Pandorical writes it to the server log naming the player and the word, and you can listen:

```java
PandoricalApi.onNotUnderstood((player, kind, value) -> {
    if (NotUnderstood.COMPONENT_TYPE.equals(kind)) showThePlainScreenTo(player);
});
```

Each client reports each distinct word once per session, so it is a signal, not a stream. A client
older than reporting itself says nothing, so silence means "no news", never "understood".

**Growing a word without stranding anybody.** A new word is a new channel: a client that lacks it
never registers it, so `canSend` is false and nothing is sent. A changed word is a new channel
beside the old one, named `..._v2`, with the old one kept and the server picking the newest the
client can take. Nothing about either refuses a player, which is why neither moves the protocol.

## Guards and timing

```java
// Guards players on vanilla clients
if (!PandoricalApi.isAvailable(player)) return;

// Per-capability guard, for anything a client might lack
if (!PandoricalApi.hasCapability(player, Capabilities.HUD_ELEMENTS)) return;
```

`isAvailable(player)` and `hasCapability(...)` return false until the client's capability
handshake completes, which lands shortly *after* the player's JOIN event, not before.
Do not push a screen or HUD straight from a JOIN handler. Defer it a tick or two, or
trigger off the player's own first action, or the push silently no-ops.

Content sync is tracked separately. Wait for it before opening anything that draws your
own blocks, items, or textures:

```java
if (PandoricalApi.isContentReady(player)) {
    screens.open(player, ...);
}
```

## Quick start

The whole path, from mod init to a screen a player can click:

```java
public class MyMod implements ModInitializer {
    private static final String SCREEN_TYPE = "mymod:hello";

    @Override
    public void onInitialize() {
        // Register handlers once, at init. They key on the screen TYPE.
        PandoricalApi.screens().onAction(SCREEN_TYPE, "ok", (player, data) -> {
            player.sendSystemMessage(Component.literal("Clicked."));

            String screenId = PandoricalApi.getOpenScreenId(player.getUUID());
            if (screenId != null) PandoricalApi.screens().close(player, screenId);
        });
    }

    public static void openHello(ServerPlayer player) {
        if (!PandoricalApi.isAvailable(player)) return;

        ScreenBuilder screen = new ScreenBuilder(SCREEN_TYPE)
            .size(176, 100)
            .title("Hello");

        screen.panel("bg", 0, 0, 176, 100, Map.of("border", "beveled"));
        screen.text("greeting", 8, 24, "Hello from the server.");
        screen.button("ok", 8, 60, 60, 20, Map.of("label", "OK"));

        PandoricalApi.screens().open(player, screen.build());
    }
}
```

Everything else in this document is a variation on that shape: build a description,
hand it to an API, register handlers for what comes back.

For the same thing at full size - every component type at once, with the code that
produced it - see [ShowcaseScreen.java](https://github.com/fatlard1993/pandorical-demo/blob/main/src/main/java/justfatlard/pandorical_demo/ShowcaseScreen.java)
in the demo.

## Component types

![Every component type on one screen](screenshot-components.png)

Screens and HUD overlays are composed from these component types:

`panel` · `scroll_panel` · `text` · `button` · `text_input` · `sprite` · `item_slot` ·
`item_icon` · `inventory_grid` · `map` · `radar` · `particle_burst` · `dial` · `pixel_canvas` ·
`player_face`

`ScreenBuilder` and `HudBuilder` carry shorthand for the ones used most. The rest,
`text_input` and `item_slot` among them, are built with `ComponentBuilder` and passed to
`component(...)`. There is no `.textInput(...)`; do not go looking for one.

## Screens

`ScreenBuilder` produces the `OpenScreenS2C` payload. It carries shorthand for the
common components, and `component(ComponentBuilder)` for anything needing nesting,
scale, or rotation.

```java
new ScreenBuilder(SCREEN_TYPE)
    .id("explicit-id")              // defaults to a fresh UUID per builder
    .size(280, 180)
    .title("Fletching Table")
    .pauseGame(false)
    .container(10, true);           // 10 server-backed slots + player inventory
```

Shorthand: `panel` · `scrollPanel` · `button` · `text` · `inventoryGrid` · `itemIcon` ·
`sprite`. Nested or animated components go through `ComponentBuilder`:

```java
screen.component(new ComponentBuilder("badge", "sprite")
    .bounds(10, 10, 16, 16)
    .scale(1.5f)
    .rotation(45f)
    .prop("texture", "mymod:textures/gui/badge.png")
    .child(new ComponentBuilder("label", "text").pos(2, 2).prop("text", "!")));
```

### Opening

```java
ScreenApi screens = PandoricalApi.screens();

screens.open(player, screen.build());

// Backed by a real server-side Container; slot 0 is read-only here
screens.openContainer(player, screen.build(), container, Set.of(0));
```

### Updating

Updates address components by ID inside an already-open screen. `ComponentUpdateBuilder`
builds them; the client interpolates geometry changes over the following ticks. A new position
is measured the way the component's own was when it was built, from its parent's corner or the
screen's, and a component that moves takes its children with it.

```java
String screenId = PandoricalApi.getOpenScreenId(player.getUUID());
if (screenId == null) return;

screens.update(player, screenId, List.of(
    new ComponentUpdateBuilder("greeting").prop("text", "Updated.").build(),
    new ComponentUpdateBuilder("badge").pos(20, 10).scale(2.0f).build()
));
```

Two props ride the same update path and are worth knowing by name. `visible`
(`ComponentType.PROP_VISIBLE`, any component) takes a component and its children out of
the screen entirely: not drawn, not clickable, not a navigator target. It is how a screen
swaps one set of controls for another in place - build both at open time, hide one, flip
them on a press - without reopening and losing whatever the player was carrying. A
`text_input` being revealed usually wants `focused` (`PROP_FOCUSED`) sent with it, so the
first keystroke lands without a click. `dim_slots` (`PROP_DIM_SLOTS`, `inventory_grid`)
names container slots to veil over their items, which is what a search result looks like:
the server names the slots that did not match and the rest of the grid is what is left lit.

```java
screens.update(player, screenId, List.of(
    new ComponentUpdateBuilder("sort").prop(ComponentType.PROP_VISIBLE, "false").build(),
    new ComponentUpdateBuilder("search_box")
        .prop(ComponentType.PROP_VISIBLE, "true")
        .prop(ComponentType.PROP_FOCUSED, "true").build(),
    new ComponentUpdateBuilder("chest").prop(ComponentType.PROP_DIM_SLOTS, "0,1,5").build()
));
```

A window resize rebuilds the screen from its definitions with every update since the open
replayed over them, so a swapped-in control stays swapped in. What a component keeps that
no prop holds - the text typed into a field, strokes painted ahead of the server - is carried
over to the rebuilt component.

### Painting by hand

`pixel_canvas` is a grid of palette-coloured cells the player paints with the mouse: drag
to lay the current ink under a square brush, right-click to report the colour under the
pointer. The client paints the moment the hand moves and reports each stroke; the server
applies the same stroke to its own copy and acknowledges it. Both ends run one rule,
`PixelCanvas#apply`, over the same cells and the same supply of ink, so they agree without
the cells being sent back. Send `pixels` again only when your copy moved some way the client
could not have predicted: a stroke you refused, or a change from outside the canvas.

```java
screen.component(new ComponentBuilder("canvas", ComponentType.PIXEL_CANVAS)
    .bounds(8, 8, 192, 192)
    .prop(ComponentType.PROP_CANVAS_COLUMNS, "32")
    .prop(ComponentType.PROP_CANVAS_ROWS, "32")
    .prop(ComponentType.PROP_CANVAS_PALETTE, "#FFF7E9A3,#FF993333,#FF334CB2") // up to 256
    .prop(ComponentType.PROP_CANVAS_PIXELS, PixelCanvas.encode(cells))
    .prop(ComponentType.PROP_CANVAS_SUPPLY, PixelCanvas.encodeSupply(supply)) // omit for unlimited
    .prop(ComponentType.PROP_CANVAS_INK, "1")
    .prop(ComponentType.PROP_CANVAS_ACK, "0"));

screens.onAction(SCREEN_TYPE, "canvas", (player, data) -> {
    PixelCanvas.Stroke stroke = PixelCanvas.Stroke.fromAction(data);
    if (stroke == null) return; // a pick: data.get(PixelCanvas.ACTION_PICK)
    PixelCanvas.apply(cells, 32, 32, supply, stroke, changedIndex -> { /* redraw it */ });
    screens.update(player, screenId, List.of(new ComponentUpdateBuilder("canvas")
        .prop(ComponentType.PROP_CANVAS_ACK, String.valueOf(stroke.seq()))
        .prop(ComponentType.PROP_CANVAS_SUPPLY, PixelCanvas.encodeSupply(supply)).build()));
});
```

Cells are bytes, read unsigned, so a palette can have up to 256 colours; `PixelCanvas.encode` is their base64. The supply lists only the inks there is any of, as `index:amount` pairs.

A stroke carries the ink and brush the client saw while making it, so a colour chosen a
moment before the server heard about it still paints in that colour. They came from the
client: `apply` holds the brush to `PixelCanvas.MAX_BRUSH` and gives an ink past the end of the
supply none, and whether the ink is one your palette offers is yours to check.

### Handlers

All key on screen type, all registered once at init.

```java
screens.onAction(SCREEN_TYPE, "confirm-button", (player, data) -> { ... });

// Catch-all for generated IDs, e.g. "recipe_0", "recipe_1", ...
screens.onActionFallback(SCREEN_TYPE, (player, data) -> {
    String componentId = data.get(ScreenApi.FALLBACK_COMPONENT_ID_KEY);
});

screens.onClose(SCREEN_TYPE, player -> { ... });
screens.onSlotChange(SCREEN_TYPE, (player, slotIndex, stack) -> { ... });
screens.onContainerRemoved(SCREEN_TYPE, player -> { /* return items */ });
```

`onContainerRemoved` is where items get handed back. A container screen closed without
returning its contents strands them on the server.

## HUD

Shown working in [ShowcaseHud.java](https://github.com/fatlard1993/pandorical-demo/blob/main/src/main/java/justfatlard/pandorical_demo/ShowcaseHud.java), including suppressing a vanilla element and animating one by update.

```java
HudApi hud = PandoricalApi.hud();

HudBuilder overlay = new HudBuilder("mymod:unread")
    .anchor("bottom_center")
    .offset(-40, 60);

overlay.text("count", 0, 0, "3 unread");

hud.show(player, overlay.build());
hud.update(player, "mymod:unread", updates);
hud.hide(player, "mymod:unread");
```

Anchors: `top_left`, `top_right`, `bottom_left`, `bottom_right` (offset is a margin from
that corner), `center` (offset is a nudge from true screen center, for prompts that need
to sit near the crosshair), `bottom_center` (offsetX positions the overlay's left edge
relative to horizontal center, for sitting with the hotbar rows), `top_center` (the overlay's
own width is centred; hidden while the player list is open, since that is where it drops). See
the javadoc on `HudBuilder#anchor` for the full contract.

`particleBurst` draws lightweight local particle effects; `map` renders a map component.

### Standing in for vanilla elements

```java
hud.hideVanillaElements(player, "mymod", List.of(VanillaHudElement.FOOD_BAR));
hud.restoreVanillaElements(player, "mymod");
```

Suppression is keyed by the requesting mod, so two mods hiding different elements do not
clobber each other, and an element stays hidden while any of them still wants it hidden.
Chat, the player list, the sleep overlay and the demo timer are deliberately not
suppressible, nor are the spectator menu and tooltip while the player is spectating.

Clients lacking the `hud_elements` capability keep drawing vanilla's version. An overlay
meant to *replace* a vanilla element needs a layout that still works alongside it, or
should not be pushed to those clients at all.

## Content

A synced block's light is the server's: each state's emission travels with the block and
the client stand-in is built with it, so a torch on a slab glows on every client that has
Pandorical. Nothing to declare; the block's own `lightLevel` is what gets sent.

Register during `onInitialize`. Content syncs to clients in the configuration phase,
before Fabric's registry sync.

`content.solidRails()` makes every rail hold a player up: two pixels of deck on a flat one,
a ramp of steps on a slope, for players only, so carts and mobs are untouched. It rides the
content sync as a flag, and the same shape (`RailCollision`) is applied on both sides through
one mixin on `BaseRailBlock` plus the client's stand-in blocks, because a floor the client
cannot predict is one it keeps dropping the player through.

```java
ContentApi content = PandoricalApi.content();

content.registerBlock("mymod:my-block", new BlockRegistration(...));
content.registerItem("mymod:my-item", new ItemRegistration(...));
content.registerAsset("mymod/models/block/my-block.json", jsonBytes);
content.registerModAssets("mymod");   // auto-scans classpath assets/

// Repaint a vanilla item for Pandorical clients only
content.overrideVanillaItem("minecraft:rabbit_hide", new VanillaItemOverride(...));
```

`registerModAssets` is the usual call. Anything your mod draws (textures for entity
overlays, chest overlays, custom blocks) must be registered here or the client has
nothing to load.

## Camera

```java
CameraApi camera = PandoricalApi.camera();

camera.setDistance(player, 6.0f);
camera.setPerspective(player, "third_person_back");
camera.reset(player);
```

## Entity overlays

An extra texture layer on one specific living entity, for per-entity cosmetics. The
texture must follow that entity model's own texture layout; transparent pixels are not
drawn.

```java
PandoricalApi.entityOverlays().set(entity,
    Identifier.fromNamespaceAndPath("mymod", "textures/entity/my_overlay.png"));

PandoricalApi.entityOverlays().clear(entity);
```

Broadcast to every current and future tracker of the entity, so there is no player
argument. State is in-memory only and dropped when the entity unloads: re-call `set`
when your entity loads, from a tick hook reading your own persisted flag.

## Pictures

A grid of palette-coloured cells standing in the world as a thin panel, at any yaw, tilt and
size, anchored to an entity: a canvas on an easel, a board anything can be painted onto and seen
changing. Like entity overlays, a picture is broadcast off the anchor's tracking, so there is no
player argument and nobody out of range is sent anything.

```java
PandoricalApi.pictures().show(anchor, new Picture(columns, rows, paletteArgb, cells,
    new Picture.Pose(dx, dy, dz, yaw, tilt, widthBlocks, heightBlocks),
    backArgb, thicknessBlocks));
PandoricalApi.pictures().paint(anchor, changedIndices, newValues); // just what changed
PandoricalApi.pictures().clear(anchor);
```

The pose places the middle of the picture's bottom edge, offset from the anchor; yaw turns it the
way an entity's yaw does. The front is drawn unshaded, as vanilla draws a map in a frame, so a
colour looks the same whichever way the picture faces; the back and edges take `backArgb`, or are
left out when its alpha is nought. An invisible item display makes a good anchor: persistent,
tracked, and nothing to hit. State is in memory and dropped when the anchor unloads, so show it
again when your anchor loads. mc-paint's easel is the working example.

## Chest overlays

Draw particular chests with a different texture, so a player can tell (say) the ones a
village generated from their own.

```java
ChestOverlayApi chests = PandoricalApi.chestOverlays();

chests.replace(player, texture, positions);  // state the whole truth; the call for a join
chests.add(player, texture, positions);      // mark more, leave existing marks alone
chests.remove(player, positions);            // unmark, whatever texture they carried
```

Unlike entity overlays these are addressed to one player, because whether a chest
deserves marking can depend on who is looking.

The texture is a sprite **id** in the vanilla chests atlas, and it keeps the atlas's
directory prefix: `<yourmod>:entity/chest/<name>`, not the bare `<yourmod>:<name>`. A
bare name resolves to no sprite and the chest draws as missing-texture magenta, which is
the only symptom you get. No extension, and no `_left` / `_right` suffix: the client
appends those itself for each half of a double chest. Ship all three files
(`<name>.png`, `<name>_left.png`, `<name>_right.png`) under
`assets/<yourmod>/textures/entity/chest/`.

Nothing is persisted across a reconnect. Send the marks again on join.

## Context models

A block can be drawn differently for what stands beside it. The client swaps the model where
the chunk compiler looks it up, with the neighbours in hand; each case is a provider that names
the extra models it wants (found by scanning, so nothing is loaded that nobody shipped) and
picks one at render time. Six ship today.

**Rail diagonals.** A run of alternating curved rails is drawn as the straight diagonal it stands for. The
client swaps a curve's model for a chord when a connected neighbour is the same bend turned half
round; two curves making a U-turn keep their bends. The chord models come from whichever mod
ships the rail, named `<block>_diagonal_<se|sw|nw|ne>` beside its block models
(`<block>_on_diagonal_...` for a powered state). Where a run meets a straight the two share one
bend: the run's last chord takes `<block>_diagonal_<se|sw|nw|ne>_<n|e|s|w>`, eased toward the
straight beyond that face, and the straight takes `<block>_diagonal_end_<ne|nw>`, modelled for a
diagonal arriving through its south face and turned for the others. A rail without them keeps its bend. Any block with a `shape` property of
`RailShape` is a rail here, so a server-defined stand-in qualifies as readily as vanilla's.
Minecart Mania ships chords for vanilla's four rails and its own.

**Joined fence gates.** A gate with the same gate beside it on its line, or below it, takes a
joined model, named `<gate>[_wall][_open]_join_<left|right|both|none>[_stacked]_<facing>`
(`none` for a gate joined only below), the facing baked in because a model picked here has no
blockstate rotation. Moredoor ships them for every vanilla gate.
A lone gate marked `BlockMarkApi.GATE_HINGE_LEFT` or `GATE_HINGE_RIGHT` opens as one leaf,
`<gate>[_wall]_open_swing_<left|right>[_stacked]_<facing>`.

**Door banks.** Doors of one kind hung on the same side and filling a rectangle are drawn as
one door: frame round the outside, sheet across the inside, one handle. Each leaf takes
`<door>_mega_<bottom|top>_<hinge>[_open]_<flags>`, the flags naming which frame pieces it
keeps. A leaf marked `BlockMarkApi.DOOR_DETACHED` is left out of any bank, and leaves marked
`DOOR_SLIDING` bank only with each other. Moredoor ships them.

**Trapdoor banks.** Trapdoors of one kind lying in one plane and filling a rectangle are one
hatch or shutter, each tile taking `<trapdoor>_mega_<bottom|top|open>_<flags>`. Moredoor
ships them.

**Door jambs.** A door with a fence connecting on its left or right takes a model with a
post where the fence arm arrives and rails across to the panel, named
`<door>_<lower|upper>_<hinge>[_open]_jamb_<left|right|both>_<facing>`. Moredoor ships them
for the wooden doors and iron.

**Hung from a slab.** A ceiling-mounted block under a top slab would float half a block below
it; a block that ships `<block>_hung_<facing>[_on]` takes that model there. Lever Torch ships
them for its torch and vanilla's lever.

## Keybinds

Server-declared rebindable keys. Your mod claims a slot from a fixed client-side pool and
ships zero client code. A keybind reports its press through `onPress`; a handler that needs
the other edge, a handbrake or anything held, overrides `onRelease` too.

```java
// At mod init, before players connect. Key codes are the game's own table, which is
// neither GLFW's nor ASCII's: KeybindApi.letter gives the code for a letter.
PandoricalApi.keybinds().register("mymod:action", KeybindApi.letter('G'), "Do The Thing",
    player -> doTheThing(player));
```

Two slots come pre-bound: slot 1 to G, slot 2 to B. Naming one of those keys claims that slot
if it is free; any other key gets an unbound slot, and `bindByDefault` puts the key on it once
per client. The display name appears in the client's controls
screen under category "Pandorical", and a rebind persists in `options.txt` like any other
key. Presses arrive on the server thread, validated and rate-limited.

The pool is fixed at 16 slots (`KeybindPool.MAX_SLOTS`) because the options system only accepts
keybind registration during client startup. Unclaimed slots are inert and send nothing. A client
older than the current pool reports how many slots it carries, and the server says in its log
which claim falls off the end rather than binding a key nobody can press.

## Action menus

A grid of buttons the player opens with one key. Your mod suggests a menu, or suggests single
buttons for menus the player builds themselves; the client owns both, and everything a server
offers is rebuilt from scratch on every join.

```java
// At mod init. A button either runs a command or presses one of your keybinds.
PandoricalApi.actionMenus().suggestMenu("mymod:arena", "Arena", List.of(
    ActionMenuApi.Button.runs("minecraft:iron_sword", "Queue", "arena queue"),
    ActionMenuApi.Button.presses("minecraft:feather", "Leap", "mymod:action")));

// Or offer one button for the player to put wherever they like.
PandoricalApi.actionMenus().suggestButton(
    ActionMenuApi.Button.runs("minecraft:paper", "Home", "home"));
```

A suggested menu is the server's: the player can give it a key but cannot edit it, and it is gone
the moment they leave. A suggested button is an offer - it shows up in the editor's **Add what
this server offers...** list and only becomes real if the player picks it.

Do not suggest a key. The field exists, but a key the game already uses is refused on the client,
and the player reaching the menu through the one key that opens all of them is the design. Anything
a player does constantly already has a key; action menus are for the useful and less common.

The mods screen puts an **Add** button beside every command your mod lists that takes no arguments,
so anything reachable by `CommandHelpApi` is one press from being a button. A command whose usage
has a slot in it - `<name>`, `[page]`, `(here|there)` - gets no button, because a button sends one
fixed string and leaves nowhere to type the rest.

An older client never registers the channel, so it is sent nothing and shows no Add button. There
is no version to check and nothing to update.

## Notices

A question put to one player, waiting in a tray rather than scrolling past in chat. **This is not
a toast**: it is for something that needs an answer, and it stays until it gets one or runs out.

```java
// id, kind, icon, what it says, the choices, and how many seconds it waits.
PandoricalApi.notices().offer(player, new NoticeApi.Notice(
    tradeId, "mymod:trade", "minecraft:emerald",
    who + " wants to trade with you",
    List.of(new NoticeApi.Choice("accept", "minecraft:lime_dye", "Accept"),
        new NoticeApi.Choice("decline", "minecraft:barrier", "Decline")),
    60));

// Answered gives you the choice id; expired means it left without one.
PandoricalApi.notices().onChoice("mymod:trade", (who, id, choice) -> settle(who, id, choice));
PandoricalApi.notices().onExpiry("mymod:trade", (who, id) -> cancel(who, id));
```

Register `onExpiry` if a notice going unanswered means anything to your mod. A notice leaves
without an answer three ways - it runs out of time, it is pushed out by newer ones once a player
has more than eight waiting, or the player logs off - and all three call that handler. Without it
your mod waits for a reply that is not coming.

Offering the same kind and id twice replaces the first rather than adding a second, so an impatient
caller cannot fill somebody's tray.

## What changed since last time

A player who was away comes back to a server that moved on without them, and nothing ever says so.
Declare what a version of your mod changed, and they are told on the way in.

Ship a `pandorical.changelog.json` in your resources. **No code, and no dependency on Pandorical**
- a mod gets this by having the file:

```json
{
  "overview": ["Amethyst doors you can lock to everyone but the people you build with."],
  "versions": {
    "1.3.0": ["Growing a shared geode now asks everyone in the cluster."],
    "1.2.0": ["Geodes can be shared with the people you build with."]
  }
}
```

`overview` is the brief, `versions` is the changelog, and either may be one string or a list of
them. The file is read once, after every mod has initialised.

Declaring in code does the same and wins where both exist, for anything worked out at runtime:

```java
// At mod init. Order does not matter.
PandoricalApi.changelog()
    .note("amethyst-door", "1.3.0", "Growing a shared geode now asks everyone in the cluster.");
```

Write it for the player, not for the commit log. *"Geodes you share now ask everyone before
growing"* is worth reading; *"refactor GeodeCommands, bump deps"* is not.

Pandorical works out which mods a player has not seen by comparing what is running now against what
was running the last time they joined, kept per player in the world save. A player away for three
releases is shown all three notes, oldest first. Versions are compared as versions, so a note is
never shown to somebody who was already here for it; where a version will not parse as one, only
the note for the version actually running is shown.

**A mod that declares nothing is still reported**, as the two versions it moved between. That is the
fallback rather than the feature: a version number tells a player something changed and nothing
about what, which is better than silence and worse than a sentence.

Nothing is shown to a player joining for the first time - there is no "since" for them, and forty
mods introducing themselves is not a welcome. Nothing is shown when nothing has changed, either.

It arrives as a notice, so it waits in the tray rather than scrolling past in chat, and answering it
opens a screen grouping the changes into what is new, what changed and what is gone.

The same notes are also a **Changes** tab on the mod's own page in `/pandorical mods`, newest first,
with the running version marked. The notice is a moment and is gone once answered; the tab is the
copy that stays, for anyone who dismissed it or joined after it.

## The brief

What your mod is, for somebody meeting this server for the first time. One pass over everything
running, offered once, ending with a way into the mods screen for whoever wants more.

The same `pandorical.changelog.json` carries it, under `overview`, so a mod declares both in one
file and writes no code:

```json
{ "overview": ["Amethyst doors you can lock to everyone but the people you build with."] }
```

Or in code, which wins where both exist:

```java
PandoricalApi.brief().overview("amethyst-door",
    "Amethyst doors you can lock to everyone but the people you build with.");
```

One or two lines. This is the paragraph before the readme, not the readme: a player who wants the
rest is one press from it at the end of the brief, and a mod that explains everything here is
explaining it to somebody with no idea yet which parts they will care about.

**Saying nothing is fine.** A mod with no overview is described by the summary already in its own
`fabric.mod.json`, so the brief is never a list of names with gaps in it. Declaring one replaces
that summary for a mod that would rather say it in its own words. A mod with neither is left out
rather than listed blank.

It arrives as a notice, so it waits rather than taking somebody's first minute, and it is offered
once per player ever - `/pandorical brief` brings it back for anyone who turned it down or wants
another look - marked as offered when it is put to them, not when they read it, so turning
it down is not an invitation to ask again tomorrow.

Mods are listed in the same order the mods screen uses, which is alphabetical by name. There is no
way to rank them, deliberately: a new player has no idea yet what matters to them, so an order
claiming to know is guessing.

## Gating play

A mod that holds a player short of play - a pen at the spawn until a password is said, a rules
screen, anything they are kept behind - should say so, and everything Pandorical would put to a
newcomer waits.

```java
// At mod init.
PandoricalApi.heldWhile(Gate::isWaiting);
```

The brief, what changed since last time, and the action menus are held back for as long as any
registered test says this player is held, and delivered the moment none of them do. Held back, not
dropped: the same arrival a second late.

Two reasons it matters. A player being asked for a password does not need a bell, a tray badge and
a list of forty mods over the one instruction they are trying to read. And that list is the whole
inventory of the server, which is not a thing to hand to somebody who has not answered yet.

`onPlayerReady` is a different moment - it means the client has finished its handshake, not that
the player is free to play. A mod can want both.

## Navigable screens

A Pandorical screen builds its UI from server-sent component definitions rather than
vanilla widgets, so `Screen.children()` reports it as empty and anything navigating by
keyboard focus or a gamepad finds nothing to press. `NavigableScreen` (in the common API,
`justfatlard.pandorical.api`) is how a screen says where its interactive parts are:

```java
// Both Pandorical screens, plain and container, implement this; a component opts in by overriding
// isNavigable(), which should be true exactly when it handles mouseClicked.
List<NavigableScreen.NavRegion> regions = ((NavigableScreen) screen).navRegions();
```

Regions are geometry only, with no activate hook. A navigator moves the pointer onto one
and clicks it through the screen's ordinary mouse path, so vanilla slots, vanilla widgets
and Pandorical components are all driven by one mechanism and none of them need to know
what is doing the navigating. Regions are computed per call rather than cached, because
component geometry is mutable and interpolates for several ticks after a server update.

## Crafting stations

A screen that is a crafting bench can say so, and a recipe book will speak for it:

```java
new ScreenBuilder("my-mod:bench")
    .container(10, true)
    .recipeStation("my-mod:my_category")
```

The category is a registered `RecipeBookCategory` id. Your recipes must return a real
`RecipeDisplay` from `Recipe#display()` and carry that category from `recipeBookCategory()` -
without a display the client cannot see the recipe at all, and without the category a workbench's
book will offer it and then fail to craft it.

Pandorical draws nothing for this. It has no book of its own and no opinion about whose should
appear; it only answers "what is this screen for", through
`PandoricalContainerScreen#getRecipeStation()`, which is what a book needs before it can offer
anything. smart-recipe-book puts a button on any screen that declares one.

Filling the grid is the station's own job. Vanilla's `ServerboundPlaceRecipePacket` is answered
only for menus carrying a recipe book, and a Pandorical menu does not, so a book asks instead with
a screen action on `ScreenApi.PLACE_RECIPE_COMPONENT`; Pandorical resolves the recipe and hands
it to the handler registered for the screen type, which places the ingredients itself:

```java
screens.onPlaceRecipe(SCREEN_TYPE, (player, recipe, useMaxItems) -> { /* check it is yours, fill the grid */ });
```

A station with no handler ignores the request.

## Banner decals

`PandoricalApi.bannerDecals()` lays banner pattern layers flat on a block, per player and
per position, drawn by the client with vanilla's own pattern sprites through the banner's
flag model laid on its back. No base colour is drawn: the block's own texture is the ground,
which is what makes a patterned bed read as a bed. A decal is described from its anchor block
(`toHead`, `lift`, `fromHead`, `length`, `width`), sent with `send(player, decals)`, and cleared
with `clear(player, positions)`, which sends each position with no layers. Clients skip a decal whose chunk is not loaded or whose block is gone. Vanilla clients
see nothing; a mod keeping an item display as their fallback marks the displayed item's custom
data with `BannerDecalApi.HIDDEN_ITEM_KEY` and Pandorical clients leave that display undrawn.

## Settings

Per-player settings a server mod would otherwise put behind a command, on the mod's own page of
the mod menu. A mod declares its settings once at init, under its own name; `/pandorical settings`
reaches the same values as text on a vanilla client.

```java
PandoricalApi.settings().group("block-tip", "Block Tip")
    .choice("mode", "Show tips", options, "always")
    .describe("Names what you are looking at")
    .backedBy(player -> ..., (player, value) -> ...);
```

Four kinds of setting: a **toggle**, a **choice** among named options, a **number** with a
range and step, and a **list** of the player's own entries, each with a button that takes it
off - for what a mod collects by command or by play, where seeing the list and pruning it is
the whole ask. Each is read with `get(player)` and written with `set(player, value)`, and takes
`onChange` listeners. Values are kept per player by Pandorical, on the overworld, unless the
mod already keeps them, in which case `backedBy` makes the screen another way to reach the mod's
own store and the two can never disagree. A change from the screen re-labels the control in
place, and lays the page out again only when a setting may have appeared or gone.

A client-side mod, with no server half to declare anything, declares from the client instead:
`PandoricalClientApi.settings().group(...)` takes the first three kinds, each as a getter and a
setter over whatever the mod already keeps. The client tells the server what it has after the
hello, the mod appears in the same menu marked *this client*, and a change made there is handed
back to the client to apply. `changed()` tells the server the values moved some other way.

`serverGroup` declares a mod's **server settings**: one value for everyone, shown to ops alone
under their own heading on the mod's page, and refused to anyone else from the command too. They
are for what a mod would otherwise keep in a config file - rates, cooldowns, world-shaping
switches - and `backedBy` points each at the mod's own config with a setter that writes the file,
so the file stays the record. Unbacked ones are kept by Pandorical on the overworld.

A setting can say when it is shown: `shownWith("village-mail")` only while that mod is
installed, `shownWhen(other, value)` only while a setting declared before it has that value, or
`shownWhen(player -> ...)` for anything else. A setting not shown is not counted, not listed, and
refused from the command, and the page is laid out again when a change may have shown or hidden
one.

## Traps in structures, tints and renderers

`structures`, `playerInventory`, `blockTints` and built-in entity renderers work the same way,
through `PandoricalApi`; read the javadoc on `StructureApi`, `PlayerInventoryApi`,
`BlockTintApi` and `PandoricalApi#registerEntityRenderer` before wiring them up. Each carries a
trap that nothing reports:

- **Structure ids are unique server-wide**, and a structure not despawned leaks its state.
- **Anything drawn on a moving structure blends by the structure's own rule**
  (`StructureInterpolationHandler`, over `StructureManager.INTERPOLATION_TICKS`). A vanilla blend
  sits a different distance behind the server, and the gap is what a rider sees. Server-only
  entity stubs and cushions already do; push their position every tick (cushions need
  `needsSync`, since vanilla never expects one to move).
- **Tints are registered before the client asks.**
- **A tint only reaches faces with a `tintindex`**, and most vanilla models carry none.
- **A tint multiplies**, so over a coloured texture half the palette disappears. Drain the texture
  to grey and give `positional(fallbackArgb, ...)` the colour every unpainted position keeps.
  Particles a painted block throws from its animate tick wear the paint too, brightness kept.
- **Painting a vanilla block needs a model override that adds a `tintindex`**, shipped through
  `ContentApi#registerAsset` under the `minecraft` namespace; the synced pack sits at
  `Pack.Position.TOP`, so it wins over vanilla's copy. Without it the colours arrive, land
  nowhere, and the block stays exactly as it was.

`playerInventory` covers more than slots, and the rest is easy to miss looking for it:
`registerButton` puts a square glyph button on the vanilla inventory panel, `onButton`
answers a press, and `setButtonGlyph` changes what one player sees on one button - which
is how a button that is a switch says which way it is set. `registeredSlots` walks every
slot group, for the mods that have to empty the whole extra inventory rather than one
slot they already know the name of. A slot written on respawn must be written from an
`AFTER_RESPAWN` listener in a phase after `PlayerInventoryApi.RESPAWN_PHASE`: Fabric carries the
extra slots across from the default phase, and anything written before that is overwritten with
the dead player's copy.

## For client-side mods

A mod with no server half reaches Pandorical from the client. What the suite's client mods use:

- `PandoricalClientApi.settings()`: a page in the mod menu, see Settings.
- `NavigableScreen`: where a Pandorical screen's interactive parts are, for a navigator.
- `PandoricalContainerScreen#getRecipeStation()`: what a crafting screen crafts, for a recipe book.
- `InputHints`: whether the hands in use are a pad or a keyboard, which hint lines read.
- `KeybindManager.poolMapping(slot)`: a pooled keybind to drive from another input.

Only `client.api` is kept stable; the rest are classes a client mod can reach, and may move.

## Checking a build

`./gradlew build` compiles, which proves nothing about mixins: they bind at launch.
`xvfb-run -a ./gradlew runClientGameTest` launches a client off-screen, applies every mixin,
joins a world and opens a screen, and fails on any mixin whose target has moved.
