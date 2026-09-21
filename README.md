# Pandorical

Install this if a server asked you to. It lets that server draw screens, overlays and
blocks vanilla Minecraft has no way to show you.

Normally a modded server means every player installs every mod, matched version for
version. Pandorical replaces that with one install. The server keeps its mods, you keep
Pandorical. Its mods send the screens, overlays, blocks and keys; your client draws
them. Add a mod to the server and you see it next time you log in, with nothing to
download.

## What a server can show you

- **Real interfaces**, instead of everything pretending to be a chest. Quest dialogue,
  a mailbox, a trade window, a crafting station with its own layout.
- **HUD overlays** that sit alongside your hotbar or replace a vanilla bar outright, and
  update live.
- **Custom blocks and items** that arrive when you join, with no resource pack to install
  and no download prompt.
- **Rebindable keys** the server defines. They appear in your normal controls screen
  under "Pandorical", and rebind like any other key.
- **Moving structures**, like a ship built out of blocks that sails as one piece. One the
  server marks walkable is solid underfoot and carries you with it, so you can stand and
  walk on a ship's deck while it sails and turns.
- **Camera control**, when a server wants to pull the view back.
- **Cosmetic overlays** on particular mobs or chests, so you can tell one from another.
- **One block at one place a different colour**, so two of the same block in two places can
  be told apart on sight.
- **Extra squares and buttons on your own inventory screen**: a slot that only takes a
  map, a compass slot beside it, a button that tidies your pack. They sit in the vanilla
  panel and behave like the rest of it.

None of this is Pandorical's own content: no items, no blocks, no recipes, nothing
added to a world. Everything you see through it comes from a mod on the server.

## Do I need it?

- **Playing on a server that uses it**: yes, and you will usually be told. Without it,
  a mod built on Pandorical either degrades quietly (an item shows a raw name instead of
  a proper one) or refuses outright, telling you in chat that Pandorical is required.
- **Playing on a vanilla or unrelated server**: the handshake never happens and nothing
  draws. What it leaves is sixteen rebindable "Pandorical Action" rows in your controls screen,
  which sit there unused until a server names them, and one habit in every container
  screen: dragging with an empty hand moves every stack you cross. `container_habits=false`
  in `config/pandorical-client.properties` turns it off.
- **Running a server**: install it on the server too, alongside whichever mods depend on
  it. It is the one mod in this suite that belongs on both sides.

## The Mod Menu

Every mod on the server, in-game. A **Mods** button on the pause menu and the options menu opens
it on a Pandorical client: the mods down the left, and the chosen one on the right with its
description, its settings if it has any, and its readme, read straight out of its jar - every mod
in this suite ships its README beside its licence. `/pandorical mods` opens the same screen, or
lists the mods as text on a vanilla client. The screen is built to the size of the window it
will show in, so a readme gets the room the window has. The readme is rendered by block: titles
with a rule beneath, list items hanging off their markers, code in an inset with its spacing
kept, quotes with a bar down their side, tables as a list of their rows. A mod that added commands
has a **Commands** tab listing every form of each with its arguments, the ones only an op may
run under their own heading; a mod that claimed keys has a **Keys** tab showing what each is
bound to now, where pressing its button and then a key rebinds it, and Escape clears it.

A mod's settings sit in sections by whose they are: **Your settings**, kept on this server for
the player alone; **Your client's settings**, kept by the player's own game wherever they play;
and **Server settings**, one value for everyone, which only an op is shown. `/pandorical
settings` opens the menu on a mod with settings, or lists them as text on a vanilla client,
where `/pandorical settings <mod> <key> <value>` changes them; `/pandorical settings list`
lists them as text anywhere. Anyone can run these, but a server setting is listed only to ops
and refused to anyone else. A switch takes `on` or `off`.

Pandorical's own page has four server settings, each leaving the game as vanilla has it until
an op changes it or a mod on the server asks otherwise: `pairNetherPortals` (off), so each
nether portal remembers the one its first traveller came out of, both ways round, and a trip
home comes out where it went in;
`clumpExperience` (off), so XP orbs of any value merge into one that a touch takes whole, left
out when Clumps is installed; `itemMergeRadius` (5, in tenths of a block, vanilla's reach, up to
40), how far apart dropped stacks merge, never through a block, left out when Get It Together,
Drops! is installed; and `itemTrackingRange` (0, meaning vanilla's 96 blocks), how far off
players are sent dropped items and orbs. Vanilla clients see only the bigger orbs. Its client
settings are the container habits above (on), and on Windows a load guard (on): for the first
three minutes after launch and the first ninety seconds of each join it keeps the JVM's own log
and samples its threads, which stopped a native crash some Windows players hit while loading.
It is kept in `config/pandorical/load-guard.properties` as `enabled`.

### The mods screen

`/pandorical mods` lists everything you are running, in three groups: **on this server**, **on your
client**, and **switched off**. A server mod is the server's business and is shown for reference; a
mod of your own carries a **Disable** button, and one you have switched off carries **Enable**.

Switching off renames the jar to `.disabled`, which is the only thing the loader understands - it
takes what it finds at startup and there is no unloading afterwards - so nothing changes until you
restart, and the message says so. A disabled jar is invisible to the loader, so its name and version
are read out of the jar itself; a mod you switch off does not vanish from the screen that switched
it off.

For that screen to list anything, your client tells the server which mods you have: their names and
versions, and the names of the jar files, including the ones you have switched off. Nothing is sent
from inside the files. A server can ask you to switch one off or back on, but it cannot do it
itself: the question is always yours to answer, it always names the file, and Pandorical's own jar
is not on offer - switching that off would take away the screen that switches it back.

### What is running here

The first time you join a server running Pandorical, a notice offers you **the brief**: every mod
on the server in a line each, saying what it is rather than what changed in it. It waits in your
tray rather than taking your first minute, and at the end it offers the mods screen for anything
you want to read properly.

It is offered once. `/pandorical brief` brings it back whenever you want it.

### Action menus

Also on Pandorical's page, under your client's settings: **Action menus**, whose **Edit...**
opens an editor for your own grids of buttons. Give a menu a name and a key; fill it with
buttons, each with an icon (any item, found by searching or taken from your hand), a label shown
when you point at it, and what it does: **run a command**, as if you had typed it, with your own
permissions; **press a key**, any key in the controls screen, other mods' keys included; or **open
another menu**, so menus can be pages of one another, and a page needs no key of its own. Press
the menu's key with nothing else open and the grid comes up in the middle of the screen; click a
button to do it. Escape goes back a page, and the key you opened with puts them all away. A key that opens something of
the game's own when pressed leaves the menu shut rather than covering it.

**One key opens all of them.** **J** by default, **d-pad up** on a controller, and rebindable as
"Open action menus" in the controls screen like any other key. It opens **Menus**, a menu whose
buttons are the other menus, each wearing the first thing on it. A menu you reach for constantly
can still have a key of its own; this is so the rest do not each need one.

**You do not have to start from nothing.** Some menus are the server's, and they are there the
moment you join: **Game**, for corners of the vanilla game worth reaching for, **Server**, for what
the mods here have put forward, and one more for each mod with enough to fill a grid of its own -
Emotes, or Arena.

What is on them is chosen, not swept up. An action menu is for the useful but less common: the
thing you would otherwise have to remember a command for. Anything you do constantly - your
inventory, chat, petting an animal - is already faster on the key it is bound to, so it is not
here. A menu that mirrored the controls screen would only be a slower controls screen.

These are rebuilt from the server every time you join, so they are never out of date: a mod that
adds a button has added it for everybody, and a mod that goes away takes its buttons with it. That
also means they are not yours to edit. The one thing about them that is yours is the key you open
them with, which is remembered for you in `config/pandorical/action-menu-keys.json`.

**Anything you read about, you can keep.** Every mod's page in the mods screen has a **Commands**
tab listing what it offers, and beside each command that needs no arguments there is an **Add**
button: press it and you are asked which of your menus it should go on, or offered a new one. A
command the mod also promotes arrives with the name and icon that mod chose for it; anything else
arrives as itself, ready to rename. Commands taking a `<placeholder>` have no Add button, because a
button sends one fixed string and gives nobody anywhere to type the rest.

**To make one of your own, take what you like from theirs.** The editor's **Add what this server
offers...** lists every promoted button, grouped by where it came from, so building your own grid
is a few clicks rather than typing commands and hunting items to stand for them. What is on offer
is whatever the server you are on promotes, and it is forgotten when you leave.

**Your own** menus are your client's, not the server's: kept on your machine in
`config/pandorical-action-menus.json`, one set per account, so they come with you to every server
you play on from that machine and two players sharing one keep their own. The server's menus are
not in that file at all, and the only part of them that is yours - the key each one opens with -
is kept beside it in `config/pandorical/action-menu-keys.json`.

## Installation

Fabric, plus Fabric API. Drop the jar in `mods/` on the client, and in `mods/` on the
server if you run one.

For a crash nobody can reproduce, rename the jar so its name contains `diagnostic`. It then
writes `logs/pandorical-trace.log` (each startup step and mixin applied, and where the game's
threads are) and has the JVM keep `logs/pandorical-jvm.log`; the run before is kept as
`pandorical-trace-previous.log`.

![A Pandorical server drawing to the client](screenshot.png)

Everything above the grass in that shot comes from the server: the corner panel and the
prompt, the ration bar standing where the hunger bar would be, the marked chests, and the
raft floating as one piece. The client installed one mod.

## Mods that use it

Around thirty server-side mods are built on Pandorical, all at
[github.com/fatlard1993](https://github.com/fatlard1993). The ones where you will see
it most:

- [village-mail](https://github.com/fatlard1993/village-mail): a postal system with a
  full mailbox interface and an unread-mail badge
- [village-quests](https://github.com/fatlard1993/village-quests): villager dialogue and
  quest screens
- [player-trade](https://github.com/fatlard1993/player-trade): player-to-player trading
  windows
- [fletch-craft](https://github.com/fatlard1993/fletch-craft): a working fletching table
  with its own crafting layout
- [map-plus-plus](https://github.com/fatlard1993/map-plus-plus): map and compass slots on
  the inventory screen, and a live minimap
- [chest-utils](https://github.com/fatlard1993/chest-utils): sort and transfer buttons on
  every chest, and sixteen colours of paint on the chests themselves

## For mod developers

Your mod stays server-side and describes what it wants drawn. Pandorical does the rest,
so you ship no client code.

See [DEVELOPMENT.md](DEVELOPMENT.md) for the API, the capability handshake, the settings a
mod can put on its page of the mod menu, and the two string mistakes that fail silently.

For a worked example, [pandorical-demo](https://github.com/fatlard1993/pandorical-demo)
puts every component type in one screen and every world capability in one frame, and it
is where the screenshots on this page come from.

## License

MIT, see [LICENSE](LICENSE).
