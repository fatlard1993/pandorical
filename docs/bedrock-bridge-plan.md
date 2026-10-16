# Bedrock bridge: phase 0 plan

Status: phase 1 built (uncommitted); the rest is a reviewed proposal. Written 2026-10-15 against Pandorical 15.22.0,
Minecraft 26.3, Fabric loader 0.19.5, Fabric API 0.160.6+26.3, Geyser 2.12.0 (build b1258) and
Floodgate-Modded 2.2.7-b69.

Goal: a Bedrock player, console included, joins a Pandorical server through Geyser and Floodgate and
gets a useful game, without any mod on the server knowing what kind of client they hold.

Facts are marked **[V]** where they were read in source or official docs, **[I]** where inferred,
and **[S]** where only a spike (listed in [Spikes](#spikes)) can settle them.

## Phase 1 run, 2026-10-15: past registry sync, and what Geyser does with modded ids

Run on a dev server (`../pandorical-geyser`, `./gradlew runServer`) with Geyser-Fabric 2.12.0-b1258,
Floodgate-Fabric 2.2.7-b69, and a headless `bedrock-protocol` 3.60.1 client
(`auth-type: floodgate`, `validate-bedrock-login: false`).

- **Fabric API refuses every client without Fabric, on every Pandorical server [V, observed].**
  The Bedrock client got through Geyser and Floodgate (`.Bot1`, UUID
  `00000000-0000-0000-0000-000000000000`). It was then disconnected in configuration with "This
  server requires Fabric Loader and Fabric API installed on your client! ... namespaces: pandorical".
  That happens with Pandorical as the only content (its own `pandorical:menu` `MenuType`, registered
  in `Pandorical.MENU_TYPE`). With dirt-slab and emerald-tools added, their namespaces join the list.
  The check is `RegistrySyncManager#configureClient` in fabric-registry-sync-v0 8.0.1: a client
  that cannot take `RegistrySyncPayload`, while any synced registry holds a modded entry and isn't
  marked optional, is disconnected. There is no property or API to skip it. A vanilla Java client
  meets the same check, so it is refused too.
- So `ContentRegistry`'s note "kept out of Fabric's registry sync, so a vanilla client can connect"
  had been wrong since the first commit. Nothing removes those namespaces from the sync. The note
  now says what the set does.
- S1 ("a vanilla client disconnects at the first custom block") is moot. It never gets that far.
  S2 (what Geyser does with modded ids) can't be run until something lets the player past
  configuration.
- The rest of phase 1 works:
  - Floodgate detection.
  - The `.Bot1` login.
  - The new dependency floor. Geyser's release jar calls itself `2.12.0-SNAPSHOT`, so the floor is
    `>=2.12.0-`.
  - Geyser's own startup alongside Pandorical: it registered its built-in custom blocks and items
    with `enable-custom-content: true`.

Getting a Bedrock player in means skipping Fabric's registry sync for that connection. Fabric has no
API for that, so it takes a mixin into `net.fabricmc.fabric.impl.registry.sync`, which is a **hack**
by this plan's own rule.

**Decision (reviewed): skip it for Bedrock players only.** Vanilla Java clients stay refused by
Fabric. Letting them in would only move their disconnect to the first modded id, and fixing that
means rewriting packets per player, which is the Polymer approach the suite retired. Classification
still tells vanilla Java apart, so the dialog work in phase 2 reaches them if that ever changes.

- **The bypass** is `RegistrySyncManagerMixin` in pandorical-geyser. It cancels `configureClient` at
  `HEAD` when Floodgate or Geyser says the profile UUID is Bedrock, and every other client still
  goes through Fabric's check. `defaultRequire: 1`, so a Fabric API that renames or reshapes the
  method fails the server at startup instead of letting Bedrock players be refused quietly.
- **S3, settled:** Floodgate knows the player by `configureClient` (the same `CONFIGURE` stage), so
  the bypass works with Floodgate. Geyser-only servers (no Floodgate) are not verified, because
  Geyser may still be reading the login reply then. Install Floodgate.

### S2: Geyser against modded ids, observed

The bot joined, spawned, and stayed connected for 55 s while it was given an emerald pickaxe and dirt
slabs, had the dead-heads Soulbound enchantment applied, and had dirt and grass slabs placed beside
it. It rejoined later with those items in its inventory. Nothing disconnected it. What Geyser did
instead:

- **Unknown block states become air [V, `BlockMappings#getBedrockBlock`].** No error is logged. The
  server still has the block, so the Bedrock player walks into invisible collision and can't see or
  aim at what they are breaking. Phase 4a (map each state to its base block) fixes most of this.
- **An unknown item id makes Geyser drop the whole packet [observed].** It logs "Could not translate
  packet ... Missing ID 1667 in Java registry minecraft:item" and sends nothing. So:
  - `ClientboundContainerSetContentPacket`: **one modded item anywhere in the inventory and the
    Bedrock player's whole inventory never syncs**, at join or on any full resync.
  - `ClientboundContainerSetSlotPacket`: that slot never updates.
  - `ClientboundRecipeBookAddPacket`: the recipe book loses every recipe in a packet that names a
    modded item, vanilla recipes included. At join that is all of them.
- Enchantments (a data-driven registry) translated without errors.

So phase 3 is the floor of a playable game, not polish. Even before textures, every modded item has
to be registered with Geyser (`GeyserDefineCustomItemsEvent` with `NonVanillaCustomItemData`, using
the item's Java id) so inventory packets translate at all. Phase 3 now goes before phase 2, with a
first step of registering every modded item as a named non-vanilla item with a placeholder
icon.

### Phase 3a, built and observed

`ModdedItems` in pandorical-geyser subscribes to `GeyserDefineCustomItemsEvent` at
`SERVER_STARTING`. Geyser's API may not exist before its own initializer runs, and it fires the
event at `SERVER_STARTED`. Every item outside the `minecraft` namespace is registered as a
`NonVanillaCustomItemDefinition` under its Java id and network id. Each one carries:

- the English name, from `assets/<namespace>/lang/en_us.json`, then Pandorical's own lang file, then
  the item's path in title case. Names are read through the new `ContentApi#asset(path)`, which 3b
  will use for textures;
- `max_stack_size`, `max_damage` and `enchantable` from the item's default components;
- a hand-held pose for items with a `tool` component;
- the Bedrock vanilla `paper` texture as a placeholder icon.

On the dev server, Geyser took all 14 modded items with no rejections. The bot rejoined carrying
dirt slabs, grass slabs and an emerald sword, and the run showed:

- The full inventory sync arrived with the modded items under their own ids.
- A later `/give` arrived as a slot update.
- No packet failed to translate, the recipe book included.
- The item registry Geyser sent carried "Emerald Sword", stack size 1, durability 1717 and
  enchantability 20.

Not covered yet: tool mining speeds (`JavaItemDataComponents.TOOL`), food and equippables. Bedrock
predicts these on the client, so a modded pickaxe digs at hand speed in the Bedrock player's
prediction. The server stays authoritative.

### Phase 3b, built and observed

`ItemIcons` follows each modded item from its `items/` definition to the first model it names. It
then walks that model's parents through Pandorical's asset store until it reaches
`minecraft:item/generated` or `item/handheld`, and takes `layer0` from it, resolving any `#`
references. An animated texture (one with a `.png.mcmeta`) is cut down to its first frame.
`BedrockPack` writes those textures, an `item_texture.json` and a manifest to
`<game dir>/pandorical-geyser/bedrock-items.mcpack`, and registers the file through
`GeyserDefineResourcePacksEvent`. Its UUIDs come from a hash of its contents, as in Geyser's own
skull pack, because Bedrock caches packs by UUID and version. Each item's definition then names its
icon key, and `handheld` comes from the model rather than from the `tool` component.

What the dev server showed:

- 5 icons converted (the emerald tools).
- 9 placeholders, one list logged at startup: the dirt-slab items. They are block models drawn only
  from vanilla textures, which a server doesn't hold, so they wait for phase 4.
- The pack is about 3 KB. The client was offered it alongside Geyser's own.
- The item registry Geyser sent named `pandorical_emerald_tools_justfatlard_emerald_sword` as the
  sword's icon, with `hand_equipped: 1`.

Not verified: how the icons look. The bot doesn't download or render packs, so this needs a real
Bedrock client.

Left out on purpose:

- Multi-layer items (`layer1` and up, e.g. tinted overlays) show `layer0` only.
- Items whose model isn't flat keep the placeholder: block items, 3D item models, and `special`
  model types. For block items, phase 4's block mapping can give them an icon through Geyser's
  block placer.

### Phase 4, built and observed

The plan's 4a ("map each state to its base block's Bedrock state") isn't possible. Geyser's API
maps a modded Java state only onto a Geyser custom block, never onto a vanilla Bedrock state [V,
`GeyserDefineCustomBlocksEvent#registerOverride`]. So 4a and 4b merged: every modded block becomes a
custom block drawn from its own Java model.

- **`BlockModels`** reads the blockstate file (variants, or every multipart piece whose `when`
  holds) and walks each model's parents. It bakes the elements with the variant's x/y/z rotation,
  using Minecraft's own `Quadrant` and `OctahedralGroup`, so the maths is Java's. Default face UVs
  copy `FaceBakery#defaultFaceUV`.
- **`ModdedBlocks`** turns the baked elements into Bedrock geometry:
  - x is mirrored about the centre;
  - up and down UVs are read from the opposite corner;
  - one material instance per Java texture variable.

  These conventions match GeyserMC's PackConverter and Blockbench. Element rotation signs follow
  Blockbench (`[-x, -y, z]`); PackConverter negates z as well, and only a real client can settle
  which is right. Each distinct look among a block's states becomes one permutation, picked by
  `pandorical:v` (plus `pandorical:w` past 16 looks). A look carries:
  - collision boxes from the server's collision shape;
  - a selection box from its outline;
  - light emission and dampening;
  - hardness.

  Every state is registered with Geyser along with its Java collision, hardness and piston
  behaviour.
- **`BlockTextures`:** a mod's own texture is copied into the pack. A vanilla one points at
  Bedrock's copy under its Bedrock name, using the 883-entry table vendored from PackConverter (MIT,
  see `NOTICE`).
- **Vanilla models:** a dedicated server has no Minecraft models, so `vanilla-block-models.json`
  bundles all 2883 26.3 block models (parents, textures and elements only; about 55 KB in the jar).
  **Licensing to review:** that file is Mojang's data, redistributed.
- **Tints:** guessed from the texture name (grass, foliage per tree, water), since a mod's tint is
  client-side code.
- **Stand-ins:** a state with no readable model becomes a box the size of its outline, drawn with
  the best texture available.
- **Block items:** a block item without a flat icon gets Geyser's block placer, so Bedrock draws its
  block as the icon.
- **Isolation:** a block that throws during conversion is logged and skipped, so it can't take the
  other blocks, the items or the pack down with it. That happened once in testing before the guard
  existed.

What the dev server showed with dirt-slab (80 blocks):

- Geyser registered 80 custom blocks and 654 non-vanilla state overrides; items stayed at 14.
- The pack holds 181 geometry files.
- Stand-ins: only `pitcher_crop_slab` and `torchflower_crop_slab`. Their blockstates name vanilla
  models that don't exist in 26.3 (`pitcher_crop_stage_0` should be `pitcher_crop_bottom_stage_0`,
  and there is no `torchflower_crop_stage2`). That's a bug in dirt-slab, and Java clients get a
  missing model there too.
- The bot received all 80 block definitions in `start_game`. The grass slab carried its geometry,
  `dirt` / `snow` / `grass_side_snowed` material instances, a half-height collision box and
  hardness 0.6.
- A throwaway probe of Geyser's internal `BlockMappings#getBedrockBlock` showed modded states
  resolving to their custom block (`grass_slab`, `dirt_slab`, `fern_slab`), where before they
  resolved to air. The control, Java id 0, resolved to `minecraft:air`.

Not verified, all needing a real Bedrock client:

- How anything looks: UV orientation, rotation signs, tints, and whether `alpha_test` suits each
  block.
- Chunk translation end to end. The headless bot never receives chunks: the Java server never loads
  them for it, even when it sends `set_local_player_as_initialized` and `player_auth_input`.
  Getting it to would be a project of its own.

Known gaps:

- Face `rotation` and `uvlock` are ignored, so a rotated block's top texture keeps its unrotated
  orientation.
- Bedrock has no translucent (`blend`) detection; blocks that don't occlude use `alpha_test`.
- Tool-tier breaking speeds aren't declared on Bedrock. The server still decides drops.

### Phase 5, built and observed

Pandorical's `CameraHints` hands every hint for a Bedrock player to a bridge registered through
`PandoricalApi.bridgeBedrockCamera(CameraApi)`, so mods keep calling `camera()` unchanged.
pandorical-geyser's `BedrockCamera` implements it on Geyser's `CameraData`:

- `setPerspective`: `first_person`, `third_person_back` and `third_person_front` map to Geyser's
  perspectives; null or empty clears.
- `reset` clears the camera instructions.
- `setDistance`, `zoom` and unknown perspectives go through `reportNotUnderstood`, once per kind and
  value per session. That is what a Java client does when it can't act on something.
- `reportNotUnderstood` now explains a Bedrock player's report as "Bedrock has nothing to do it
  with" rather than blaming an old Pandorical.

What the bot saw, after a temporary hook called the API on join:

- `setPerspective("third_person_back")` arrived as a `camera_instruction` setting preset 2, which
  is `THIRD_PERSON`.
- Two `zoom` calls logged one warning.
- `reset` arrived as `clear: true`.

Not verified: big-boats' own third-person hint on a real client, which is the plan's acceptance
test.

### Phase 2, built and observed

**The form layer.** All of it is in Pandorical:

- `form/SimpleForm` builds a vanilla dialog. A buttons form becomes `multi_action` with static
  `custom` clicks. A fields form (toggle, choice, number, text) gets a submit button carrying
  `CustomAll`, which sends every input. The dialog goes out inline with
  `ClientboundShowDialogPacket(Holder.direct(...))`.
- Input keys are positional (`f0`, `f1`, ...), because setting ids like `mod:key` aren't valid
  input keys.
- `form/Forms` keeps each player's open forms (up to 8; closing a dialog sends nothing) under ids
  `pandorical:form/<n>`.
- An answer is accepted once, only from the player it was sent to, and only for a button or fields
  that form offered. Each value is read by its field's type (byte for a toggle, float for a number,
  a known option id for a choice).
- `mixin/CustomClickActionMixin` hooks `ServerCommonPacketListenerImpl#handleCustomClickAction`
  just before the hand-off to `MinecraftServer`, which is on the server thread. It claims
  `pandorical:form/*` for game connections and passes everything else on.

**What uses it.** Every one of these is for a player without Pandorical's `SCREENS` capability; a
Pandorical client sees exactly what it did before.

- `/pandorical settings`: a list of groups, then a page per group.
  - Toggle, choice and number settings become fields; a list setting becomes a button into a page
    of Remove buttons.
  - Answers go through `parse` like a typed command, and server groups are checked for op at
    answer time as well as at open.
  - The reply names what changed ("Changed Head lock, minutes: 10, ...") or says "Nothing
    changed."
- `/pandorical mods` lists the mods, each opening a page with its description, authors, the first
  section of its readme, and a Settings button when it has settings.
- `/pandorical brief` and `/pandorical changes` open form versions of the brief and what's new,
  beside the screens they mirror.
- Notices:
  - A notice offered to such a player is announced by one chat line ending in `/pandorical
    notices`, clickable on Java.
  - The new `/pandorical notices` opens the waiting notice, or a list when there are several, with
    a button per choice.
  - Choices go through the same handler path as the tray.
- Joining: those players now get `Arrivals` at JOIN, since no handshake is coming. That is how the
  brief and what's new reach them at all.
- `ModCatalog` now leaves out jars nested inside another mod. With Geyser installed the mod menu
  listed some forty of Geyser's bundled libraries, for Pandorical clients too.

What the bot did through Geyser (op for the settings run):

- Joined and got "A quick look at the 6 mods running here. /pandorical notices" in chat.
- `/pandorical notices` opened the brief notice; "Have a look" opened the brief form; "Every mod,
  in full" opened the mod list, now 7 real mods.
- `/pandorical settings` opened the group list. Dead Heads' page arrived as a Bedrock custom form:
  two sliders with their ranges and steps, a toggle, and a Save / Back / Close dropdown.
- Submitting new values replied "Changed Head lock, minutes: 10, Mob heads by default: true".
  Reopening showed the new values, and a second submit restored them.

**Quick actions.** Pandorical ships `data/pandorical/dialog/quick_actions.json`, a notice tagged
`#minecraft:quick_actions`. Its Open button sends the fixed click `pandorical:quick_actions`, which
`Forms.onClick` routes to `actions/QuickActions`. `/pandorical actions` opens the same form. It
lists:

- Welcome, What's new, Mods, Settings, and Notices with its count;
- every claimed keybind, which `KeybindPool#pressFromMenu` fires as a press and release (Pandorical's
  own notices key is left out, being the Notices button);
- every command button a mod suggested, labelled with its menu, run as the player. `{player}` and
  `{players}` ask who first, from the players online, one per press.

**Trust.** `/pandorical trust` gives an op without Pandorical's screens three forms:

- a list: "Everyone, by default" and every known player, online first;
- the everyone page: a toggle per rule (PvP is the gamerule);
- a player page: Default (on/off) / On / Off per rule.

Op is checked at open and at every answer.

What the bot did, as op (Geyser's `/geyser quickactions` needs op on Fabric without a permissions
mod):

- `/geyser quickactions` showed the shipped "Pandorical" dialog. Open led to the quick actions
  form, and Mods led on to the mod list.
- On the trust everyone page, Fire turned off and the reopened page showed it off; turned back on.
- On the trust player page, Fire set to Off; RCON's `/pandorical trust` then listed ".Bot1: fire
  off". Restored to default.

Not done yet:

- A gametest that sends a forged answer.
- A suggested command button, `{player}` and a keybind press, through the bot: no mod on the dev
  server suggests a button, and the keybind path ran only as far as listing. The checks are in `SimpleForm#read` and `Forms#handle`,
  but only Geyser's honest answers have exercised them.
- A vanilla Java client's view of the forms: such a client can't join while Fabric's registry sync
  refuses it.

### Review, 2026-10-16

Two reviews (Pandorical's side, and all of pandorical-geyser). What changed because of them:

- **Who gets forms** is decided by `ClientKind` (`Forms.wanted`), not by the screens capability. A
  Pandorical client mid-handshake hasn't got that capability yet, and was about to get forms and
  chat lines. Forms players skip the notice badge, whose HUD call logged a warning on every notice.
- **Form answers:**
  - An answer is refused unless it comes from the very `ServerPlayer` the form was shown to, since
    a respawn makes a new one and handlers hold the old.
  - Form ids carry a random token, because any custom click in a book or chat line could otherwise
    aim at a guessed id.
  - NaN and infinite numbers are refused, and the gametest now covers NaN.
- **Changelog:** a mod still loaded is never reported gone. The nested-jar filter would otherwise
  have told every returning player that Geyser's libraries had left.
- **Smaller fixes:**
  - `ClientKinds` lets go of connections that never reach play after ten minutes.
  - `pressFromMenu` releases in `finally`.
  - The settings Back button is left off when there is only one mod.
  - `ContentApi#asset` returns a copy.
  - Comments that had drifted were fixed.
- **pandorical-geyser:**
  - The pack's UUIDs were different on every start: `Map.copyOf` and the registries iterate in an
    order a restart doesn't keep, and zip entries carried the time. Files and texture listings are
    now sorted and entry times zeroed, and two boots gave the same UUID and identical bytes.
  - Each item converts on its own, and a broken lang file no longer stops the rest. A state whose
    model can't be read gets a stand-in instead of costing the whole block.
  - A block is registered only once all its overrides are built, and items only place blocks
    Geyser accepted.
  - Detector calls inside the registry-sync mixin can't fail a Java player's login, and a Geyser
    that didn't start is logged rather than thrown.
  - Material instances keep the model's variable names, numbered where multipart pieces collide.
  - Invisible blocks draw nothing.
  - NOTICE says where the vanilla models come from and that MIT doesn't cover them.

Still open from the reviews, needing a real client or a decision:

- Whether east and west face names need swapping under the x mirror.
- Texture turning on rotated blocks (no `uvlock` or face `rotation` yet).
- `blend` for translucent blocks.
- Whether Pandorical clients should see the shipped quick-actions entry.
- Gametests for a non-op answering an ops-only form.

### Real-client checklist

What the headless bot can't see, to be run once on a real Bedrock client (phone, Windows or
console).

**Setup.** A server with:

- Pandorical and pandorical-geyser;
- Geyser-Fabric and Floodgate, both installed;
- dirt-slab, emerald-tools and dead-heads.

In Geyser's config, set `enable-custom-content: true`. Join from Bedrock and accept the resource
pack ("Pandorical content").

**Joining and items**

1. You spawn, and the server log says "Bedrock, through Geyser".
2. `/give @s emerald-tools-justfatlard:emerald_sword`.
   - It shows the emerald sword's own icon, not paper.
   - It is held like a tool, is named "Emerald Sword", and stacks to 1.
3. `/give @s dirt-slab-justfatlard:grass_slab 8`.
   - The icon is a 3D slab, not paper.
   - The inventory screen shows every item you carry.

**Blocks**

1. Place a grass slab.
   - The grass top is biome-green.
   - The side shows the grass edge.
   - It is half height.
   - You can walk onto it, and the outline hugs it.
2. Place a top slab and a double slab.
   - Each has the right height and look.
3. Look from every side.
   - No face is missing.
   - No face shows another face's texture.
4. **The east and west check.** Find a modded block whose east and west faces differ, or a
   furnace-like block, and look at both sides.
   - If they are swapped, report it: it decides whether face names flip under Bedrock's mirrored x.
5. Place a block that turns, facing each way. A log on its side, a furnace or a stair will do.
   - Note any top texture or side texture that sits at the wrong angle.
   - This one checks `uv_rotation`, and which way Bedrock turns a bottom face.
6. Mine a modded block.
   - The crack animation plays.
   - Breaking takes about as long as on Java.
7. Walk where modded blocks were placed before you joined.
   - Nothing invisible blocks you, which checks that chunks arrive with the blocks.
8. If any modded glass or ice exists, it is see-through (`blend`).

**Menus**

1. `/pandorical actions`, then each button.
   - The brief, the mod list and the notices open as forms.
2. `/pandorical settings` as an op.
   - Change a setting, reopen the form, and see the new value; then put it back.
3. `/pandorical trust` as an op.
   - The everyone page and a player's page both save.

**Camera** (needs big-boats)

1. Board a big boat.
   - The view goes to third person.
   - Dismounting gives it back.

Write each result next to its step here, and anything wrong goes into the open items.

## What the research changed

The plan going in had three premises. Two hold, one does not, and one finding makes half the work
unnecessary.

1. **"Bedrock lands on the vanilla fallback at zero extra cost" is false for content.** There is no
   vanilla fallback for blocks or items. Pandorical registers mod blocks and items in the real
   registries and keeps their namespaces out of Fabric's registry sync (`ContentRegistry`
   `serverOnlyNamespaces`) so a vanilla client can *connect*, but nothing rewrites what it is then
   sent. In 26.3 a chunk palette is read with `byIdOrThrow` (`LinearPalette`, `HashMapPalette`,
   `SingleValuePalette`) and an item with `ByteBufCodecs.registry(...).byIdOrThrow`, so the first
   custom block in view or custom item in a slot ends a vanilla client's connection **[V source,
   S1 to confirm in a real client]**. About 40 suite mods register blocks or items. Entities are the
   exception: `EntityKindPairingMixin` already rewrites an entity's type per player.
   Geyser is a different case, because it is a reader inside the server process, not a vanilla
   client, and has an API for non-vanilla registry ids. See [Content](#content-is-the-real-phase-1).
2. **Extra inventory slots are sent to every client.** `InventoryMenuMixin` adds them to every
   `ServerPlayer`'s menu regardless of client. A vanilla client's `initializeContents` calls
   `getSlot(i)` for every item it is sent, so slot 46 onwards should throw **[V source, S1]**.
   dead-heads, map-plus-plus and tp-me-plz register slots.
3. **Notices are silent off Pandorical.** `Notices#offer` plays a bell and shows a HUD badge; the
   badge no-ops without Pandorical, so a vanilla or Bedrock player hears a bell and the notice
   waits unseen until it expires.
4. **Geyser already renders Java dialogs as Bedrock forms [V].** Geyser 2.12 translates
   `show_dialog` in both configuration and play (`translator/protocol/java/dialogues/`): every
   dialog type, every input type, `custom`/`dynamic/custom` actions sent back as
   `ServerboundCustomClickActionPacket`, `after_action` honoured. A dialog without inputs becomes a
   `SimpleForm`, one with inputs a `CustomForm`; item bodies are dropped. Bedrock players reach the
   `quick_actions` and `pause_screen_additions` tags with `/geyser quickactions` and
   `/geyser options`, and `GeyserConnection#openQuickActions` opens them from code.

   So the "shared simple form model rendered twice" collapses to **one renderer: Java dialogs**.
   Vanilla Java clients draw them natively and Geyser draws them for Bedrock. Cumulus is held back
   for the one thing dialogs cannot do (button images, see [Forms](#3-the-simple-form-model)).

The other verify items: Geyser-Fabric for 26.3 exists, built against exactly this repo's loader and
Fabric API **[V]**; Geyser's `api` module is shaded unrelocated into its jar, so another mod calls
`GeyserApi.api()` in-process **[V packaging, I usable]**; Floodgate knows a Bedrock player before
the configuration phase **[V hook points, I timing]**. Details are in [Risks](#7-risks-and-open-questions).

## 1. Capability inventory

Three client types. *Today* is what happens now; *Target* is what this plan proposes. "Bridge" means
the code described in section 4. "Dialog" means a Java dialog, which Geyser shows Bedrock as a form.

| Capability (API) | Pandorical | Vanilla Java today → target | Bedrock today → target |
|---|---|---|---|
| Free-layout screens (`ScreenApi`, `ScreenBuilder`) | supported | unsupported: `ScreenRegistry#open` no-ops → unchanged | unsupported → unchanged |
| Container screens (`openContainer`) | supported | unsupported → unchanged | unsupported → unchanged |
| HUD overlays (`HudApi`) | supported | unsupported → unchanged | unsupported → unchanged (JSON UI out of scope) |
| Hiding vanilla HUD elements (`hideVanillaElements`) | supported | unsupported → unchanged | unsupported → **unchanged on purpose**: Geyser's `camera().hideElement` covers most elements, but hiding is only ever done to make room for an overlay Bedrock will not get |
| Mod menu (`SettingsRegistry#open`, `/pandorical mods`) | supported | degraded: text list → **degraded+: dialog list, one dialog per mod** | degraded: text list → **degraded+: same dialogs, as forms** |
| Player settings, toggle / choice / number / list (`SettingsApi`) | supported | degraded: `/pandorical settings` commands → **supported: dialog per mod** | degraded: commands → **supported: same dialog** |
| Client settings (`PandoricalClientApi.settings`) | supported | n/a: no client half | n/a |
| Server settings (`serverGroup`, ops) | supported | degraded: commands → **supported: dialog** | same |
| Brief, what's new (`Brief`, `Changelog`) | supported | unsupported (sent as notices) → **supported: notice dialogs** | unsupported → **supported** |
| Notices (`NoticeApi`) | supported | **broken**: bell only → **supported: clickable chat line opening a dialog** | **broken**: bell only → **supported: Geyser toast plus a quick-actions entry** |
| Action menus (`ActionMenuApi`) | supported | unsupported → **supported: quick-actions dialog (G)** | unsupported → **supported: same, via `/geyser quickactions` or a bridge hook** |
| Keybinds (`KeybindApi`) | supported | unsupported → **degraded: each declared key is a button in quick actions, calling the press handler server-side** | unsupported → **degraded: same** |
| Command help (`CommandHelpApi`) | supported | degraded: none → **supported: in the mod's dialog** | same |
| Trust screen (`TrustScreen`) | supported | degraded: `/pandorical trust` → **supported: dialog** | same |
| Custom blocks (`ContentApi#registerBlock`) | supported | **broken** (S1) → **open**: needs a vanilla stand-in per client, not in this plan | **unknown** (S2) → **degraded: mapped to the base block's Bedrock state**, later **supported: Geyser custom block** |
| Custom items (`registerItem`) | supported | **broken** (S1) → **open**, as above | **unknown** (S2) → **supported: Geyser non-vanilla item plus a generated pack** |
| Vanilla item overrides (`overrideVanillaItem`) | supported | unaffected (vanilla look) | unaffected → later: Bedrock pack override |
| Mod assets (`registerModAssets`, `registerAsset`) | supported | n/a | n/a → **source for the generated Bedrock pack** |
| Entities (`registerEntity`) | supported | degraded: `vanillaFallback` base mob | degraded: same base mob, Geyser translates it **[I]** → unchanged (Geyser custom entities are experimental) |
| Entity overlays, entity models, animations, built-in renderers | supported | unsupported | unsupported → unchanged |
| Skins (`SkinApi`) | supported | unsupported (profile skin) | unsupported → open question |
| Camera distance / perspective (`CameraApi`) | supported | unsupported | unsupported → **perspective: supported via `forceCameraPerspective`; distance: unsupported** |
| Captures (`CaptureApi`) | supported | unsupported (`canCapture` false) | unsupported → unchanged |
| Pictures, map reliefs, banner decals, chest overlays | supported | unsupported | unsupported → unchanged |
| Block tints, block marks, context models | supported | unsupported | unsupported → unchanged |
| Moving structures, walkable / dry structures | supported | unsupported | unsupported → unchanged (out of scope) |
| Render policy, mount policy | supported | overruled by the client every tick | same → unchanged |
| Extra inventory slots and buttons (`PlayerInventoryApi`) | supported | **broken** (S1) → **unsupported but safe**: slots kept off non-Pandorical menus | **unknown** (S2) → **unsupported but safe** |
| Keepsakes (`KeepsakeApi`) | supported | unsupported (`canKeep` false), by design | same |
| Solid rails (`content.solidRails`) | supported | degraded: server collision the client cannot predict **[I]** | same |
| Server-only rules: trust, portal pairing, drops | supported | supported | supported |
| Client mod serving (`ServedClientMods`), versions (`ClientVersions`) | supported | n/a | n/a; `/pandorical clients` should say "Bedrock (Geyser)" |

What a Bedrock player gets is decided almost entirely by two rows: blocks and items. A server with
no custom content is playable today **[I]**. One with custom content is playable only if Geyser
survives the ids (S2), and a vanilla Java client never is (S1).

## 2. The tier model

### Classification

One server-side value per connection, decided before anything is sent:

```
ClientKind = PANDORICAL | VANILLA_JAVA | BEDROCK
```

- **PANDORICAL**: decided as now, by the channels the client registered.
  `ServerConfigurationNetworking.canSend(handler, SyncContentConfigS2C.TYPE)` at
  `BEFORE_CONFIGURE` (`Pandorical#agreeOnVersion`), then the capability set from `HelloC2S`. No change.
- **BEDROCK**: asked of a registered `BedrockDetector`, keyed by the profile UUID available in
  `BEFORE_CONFIGURE` (`handler.getOwner()`). The bridge supplies it. With Floodgate:
  `FloodgateApi.getInstance().isFloodgatePlayer(uuid)`, which covers linked accounts. With Geyser
  in online mode and no Floodgate: `GeyserApi.api().isBedrockPlayer(uuid)`. Floodgate records the
  player at the handshake (`FloodgateHandshakeHandler`) and rewrites the profile at
  `ServerboundHelloPacket`, so the answer exists before configuration **[V hook points, I timing,
  S3]**. Never guessed from the `.` name prefix or the zero-high UUID, which are configurable and
  spoofable on a server without Floodgate.
- **VANILLA_JAVA**: everyone else.

Pandorical holds the value in the per-player state it already keeps (`PandoricalApi`
`playerCapabilities` and its neighbours) and drops it on disconnect alongside them.

### What the handshake reports

Nothing new goes over the wire. Capabilities stay what a Pandorical client declares, and
`isAvailable` and `hasCapability` stay false for VANILLA_JAVA and BEDROCK, so **every existing
degrade path applies unchanged**: screens, HUD, camera hints, overlays and the rest still no-op,
entity fallbacks still pair, `SettingsCommand` still answers in text.

The two new client kinds get a server-side capability set, documented next to `Capabilities` but
in a separate class so nobody passes them to `hasCapability`:

- `ClientFeatures.DIALOGS`: VANILLA_JAVA and BEDROCK. Pandorical clients too, as a fallback for an
  older client that lacks a screen.
- `ClientFeatures.TOASTS`, `ClientFeatures.FORCED_PERSPECTIVE`, `ClientFeatures.CUSTOM_CONTENT`:
  BEDROCK only, when the bridge is installed.

These are read **only inside Pandorical** to pick a rendering. Mods keep calling the APIs they call
now. A mod that wants to know is told the kind through one read-only accessor
(`PandoricalApi.clientKind(player)`), for logging and for `/pandorical clients`, and is told in the
javadoc not to branch on it.

### Changes to existing paths

- `ClientVersions#vanilla` logs "Bedrock (Geyser)" for BEDROCK.
- `InventoryMenuMixin` adds extra slots only for a PANDORICAL player. **[S1 first]** This is a fix
  for vanilla Java that stands on its own. The catch: the menu is built at player construction,
  before `HelloC2S`, so it has to key on the configuration-phase answer, which exists by then.
- `Notices#offer` gains the non-Pandorical delivery in the table above, and nothing else changes.

## 3. The simple form model

### Why it is a Java dialog

A dialog is already a schema both renderers understand, and 26.3's has everything the menu,
settings and notices need **[V source: `net.minecraft.server.dialog`]**:

- `CommonDialogData`: title, external title, body (`plain_message` or `item`), inputs,
  `can_close_with_escape`, `pause`, `after_action`.
- Types: `notice`, `confirmation`, `multi_action`, `dialog_list`, `server_links`.
- Inputs: `text` (optionally multiline), `boolean`, `single_option`, `number_range`
  (start, end, initial, step).
- Actions: static (`run_command`, `show_dialog`, `custom` with id and payload, and others) plus
  `dynamic/custom`, which sends every input value back as an NBT compound under its key.
- `ClientboundShowDialogPacket` carries `Holder<Dialog>`, and `ByteBufCodecs.holder` writes id 0
  followed by the dialog itself, so a dialog can be **sent inline without registering it**.

Pandorical adds a thin builder rather than a schema of its own, so there is one model and no
translation layer to keep in step:

```
SimpleForm  (Pandorical's name; builds a net.minecraft.server.dialog.Dialog)
  title, body text
  either   buttons:  [ { id, label } ]                   -> multi_action, each a custom action
  or       fields:   [ toggle | choice | number | text ] -> one multi_action whose submit is dynamic/custom
           submit / cancel labels
  onSubmit(player, buttonId, Map<String,String> values)
```

Sent through `player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)))`.

### Answers come back without a player

The answer arrives as `ServerboundCustomClickActionPacket(id, payload)`. Vanilla's
`ServerCommonPacketListenerImpl#handleCustomClickAction` passes only the id and payload to
`MinecraftServer#handleCustomClickAction`, which logs at debug and drops it. **No player is
attached [V source].** No Fabric API hook for it was found in the cached 0.160.6 jars.

That needs one mixin on `ServerGamePacketListenerImpl` (which has `player`) at
`handleCustomClickAction`, claiming ids in the `pandorical:` namespace and passing the rest on.
This is an ordinary injection like the others in `pandorical.mixins.json`, not a hack, and it is the
only new mixin in the plan. The alternative, `dynamic/run_command` with a hidden command, needs no
mixin, but Geyser puts a confirmation form in front of every `run_command` and Java shows a
confirmation for some commands, so every press would be asked twice. Rejected.

A payload is the player's own upload and is validated like `ScreenActionC2S`: the id names a form
the server sent to *this* player and has not answered yet (an open-form table per player, as
`ScreenRegistry` keeps open screens), and each value is parsed by the setting it claims to set
(`SettingImpl#parse`), so a forged value is refused, not applied.

### Where dialogs fall short

- **No images on buttons.** Action menus have icons. Bedrock `SimpleForm` buttons take an image
  (path or URL) and Java dialog buttons do not. Phase 2 ships without icons. If they matter, the
  bridge sends a Cumulus `SimpleForm` for BEDROCK in that one place.
- **Body length.** A long readme in a `plain_message` body is untested on both sides **[S4]**.
  Readmes are cut to their first section, with the rest left to the mod's page on the web, until S4
  says otherwise.
- **Quick actions are a registry tag.** `#minecraft:quick_actions` is a tag over the `dialog`
  registry, so its member is fixed at datapack load. Pandorical ships one static dialog,
  `data/pandorical/dialog/quick_actions.json`, tagged in `data/minecraft/tags/dialog/quick_actions.json`.
  Its single `custom` action asks the server for the player's own dynamic dialog. That is two
  steps where one would be nicer, and it is the stable way to do it.
- **The `pandorical` dialog registry entry reaches every client.** Dialogs are a synced data
  registry, so the entry goes to vanilla clients in configuration. They accept unknown dialog
  entries as data **[I, S1]**, and Geyser parses dialogs itself **[V]**.

### Which existing screens fit

| Screen | Today | As a simple form |
|---|---|---|
| Mod menu (`SettingsRegistry#open`) | three-pane server-built screen | `dialog_list` of mods; each opens a `notice` with description, command help and a Settings button |
| Settings page | the right pane | fields: toggle→`boolean`, choice→`single_option`, number→`number_range`; list→`multi_action` with one Remove button per entry; ops see server settings below a label |
| Brief (`BriefScreen`) | screen | `notice`, one line per mod, a button into the mod menu |
| What's new (`WhatsNewScreen`) | screen grouped new / changed / gone | `notice` with three headed sections |
| Notice (`Notices`) | tray plus screen | `multi_action`, one button per `Choice`, no icons |
| Trust (`TrustScreen`) | screen | fields: one `single_option` per rule, for ops |
| Action menus, keybinds | client-owned grids, keys | quick actions: buttons for every suggested menu and button, plus every declared keybind |
| Anything with `item_slot`, `pixel_canvas`, `map`, `radar`, `dial`, sprites | | **not expressible**; mods keep their current degrade |

`{player}` and `{players}` placeholders in action-menu commands become a `single_option` of online
players. `{players}` loses its multi-select, since dialogs have no multi-select input, and becomes
one pick per press.

## 4. Module layout

Two homes, split by what each one has to import.

**In Pandorical (`justfatlard.pandorical`)**: everything that is about vanilla Java as much as
Bedrock and needs nothing from Geyser.

- `client/ClientKind`, `client/ClientKinds`: classification and per-player state;
  `BedrockDetector`, the interface the bridge implements.
- `form/SimpleForm`, `form/Forms`: builder, open-form table, answer routing.
- `mixin/CustomClickActionMixin`: the one new mixin.
- `settings/SettingsDialogs`: mod menu, settings, brief, what's new and trust as forms, beside the
  screens they mirror. `SettingsCommand#open` and `#brief` pick a dialog over text when
  `DIALOGS` is present.
- `actions/QuickActions`: the quick-actions dialog from `ActionMenuRegistry` and `KeybindPool`.
- `notice/Notices`: the non-Pandorical delivery.
- `data/pandorical/dialog/quick_actions.json` and the tag file.

**In a sibling mod, `pandorical-geyser`** (`../pandorical-geyser`, the suite's usual layout, with
Pandorical as a Gradle subproject per DEVELOPMENT.md): everything that imports Geyser or Floodgate.

- `detect/FloodgateDetector`, `detect/GeyserDetector`: register a `BedrockDetector` at
  `SERVER_STARTING`, after Geyser's API is registered **[V: `Geyser.api()` throws before]**.
- `content/BedrockContent`: subscribes to `GeyserDefineCustomItemsEvent` and
  `GeyserDefineCustomBlocksEvent`, which fire at `SERVER_STARTED` from `Registries.load()`, and
  registers mappings from `ContentRegistry`'s blocks and items.
- `pack/BedrockPackBuilder`: turns registered Java assets into a Bedrock pack and registers it
  through `GeyserDefineResourcePacksEvent`.
- `camera/BedrockCamera`: perspective through `connection.camera().forceCameraPerspective`.
- `notice/BedrockToasts`: `connection.sendToast` for notice arrival.

`fabric.mod.json`: depends on `pandorical` and `geyser-fabric`, suggests `floodgate`. Geyser and
Floodgate APIs are `compileOnly` from `repo.opencollab.dev` and never bundled, since both arrive
shaded in their own jars **[V]**.

Why split it that way:

- A server without Geyser loads no Geyser types. Putting the code in Pandorical behind
  `isModLoaded("geyser-fabric")` would work, but every Geyser API change would then be a Pandorical
  release, and Pandorical is the one mod that has to be on every client.
- Not a Geyser extension: an extension cannot see Fabric or Pandorical classes, and the bridge's
  whole job is reading Pandorical's registries in-process. Geyser documents subscribing from a
  mod through `EventRegistrar` **[V]**.
- Not a fork: nothing here needs Geyser changed.
- The dialog work is not Bedrock work. It fixes vanilla Java players, who today get only text
  commands, so it belongs in Pandorical even if the sibling is never built.

The bridge reads two things Pandorical does not expose yet: the registered block and item lists
with their base blocks (`ContentRegistry#blocks`/`items`, private now) and the asset map. They
become read-only accessors on an internal `ContentView`, documented as unstable like the rest of
`client.api`'s neighbours.

## 5. Build order

Phases 1 and 2 are in Pandorical and help vanilla Java players as much as Bedrock ones. Phase 2 is
deliberately before content: it is cheap, independent of the spikes, and useful on a server with
no custom content.

**Phase 1: classification, and no Bedrock player is disconnected by what Pandorical sends.**
- `ClientKind` with `BedrockDetector`; the sibling mod's two detectors; `/pandorical clients` names
  the kind.
- Extra inventory slots kept off non-Pandorical menus.
- Spikes S1 to S3 run and written up here.
- Accept when: a Bedrock client (Windows or Android, Geyser 2.12, Floodgate 2.2.7) joins a server
  running Pandorical, pandorical-demo and one slot mod (dead-heads); is logged as Bedrock; plays
  for ten minutes with a custom block and a custom item *out of view*; and is not disconnected.
  A vanilla Java client is still refused by Fabric's registry sync (see the decision above). With
  Geyser absent, the sibling mod is not loaded and nothing
  changes. Existing gametests pass.

**Phase 2: menus and settings as dialogs.**
- `SimpleForm`, the click mixin, mod menu, settings, brief, what's new, notices, quick actions.
- Accept when: on a vanilla Java client and a Bedrock client, `/pandorical settings` opens a form;
  each setting kind round-trips (toggle, choice, number, list removal) and is read back by
  `get(player)`; a server setting is not offered to a non-op and a forged payload for it is refused
  and logged; G (Java) or `/geyser quickactions` (Bedrock) lists action-menu buttons and keybinds,
  and pressing a keybind there fires its `onPress`; a notice offered to either reaches them and its
  choice reaches `onChoice`; a Pandorical client sees exactly what it saw before. Gametests cover
  form building and payload validation.

**Phase 3: custom items on Bedrock.** Built before phase 2, because S2 showed one unmapped item
blanks a Bedrock player's whole inventory.
- 3a: every modded entry in the item registry (not only `ContentRegistry`'s) registered with Geyser as a non-vanilla item under its Java id,
  with its name and a placeholder icon, so inventory and recipe packets translate.
- 3b: pack builder for item textures and flat item models, replacing the placeholders.
- Accept when: every item from pandorical-demo and two suite mods (emerald-tools, more-leather)
  shows its own texture and name in a Bedrock hotbar and inventory, stacks and mines as on Java;
  an item whose model the builder cannot convert shows as a named fallback, is logged once, and
  disconnects nobody.

**Phase 4: custom blocks on Bedrock.** Two steps.
- 4a: every custom block state mapped to its base block's Bedrock state (`BlockRegistration`
  `baseBlock`). The block looks like its base, behaves like itself.
- 4b: full-cube and slab-shaped blocks as Geyser custom blocks with their own textures. Anything
  else stays at 4a.
- Accept when: a world with custom blocks from fence-posts, dirt-slab and mixed-slabs renders
  without holes or disconnects on Bedrock, with breaking time matching the server's; 4b blocks show
  their own texture.

**Phase 5: camera.** `CameraApi#setPerspective` forces the Bedrock perspective and `reset` clears it;
`setDistance` stays unsupported and is reported as such in the log. Accept when big-boats'
third-person hint works on Bedrock.

## 6. Test plan

### Running a Bedrock client

- Server: the dev server from `./gradlew runServer` with Geyser-Fabric 2.12.0 and Floodgate 2.2.7
  in `run/mods`, `auth-type: floodgate`, `gameplay.enable-custom-content: true` from phase 3 on.
  Geyser listens on UDP 19132.
- Windows, Android and iOS can add any address in the Servers tab **[I, widely known]**. The client
  must be Bedrock 26.30 to 26.34, 26.40 to 26.45 or 26.50 to 26.52 **[V]**.
- Consoles (Xbox, PlayStation, Switch) can only join featured servers. For a console test, use
  BedrockConnect's DNS redirect or a LAN-broadcast phone app, or MCXboxBroadcast so the server shows
  in the friends list **[V]**. None of this is needed to develop. Run one console session before
  calling phase 2 done, since forms are where touch and gamepad differ.
- Geyser 2.12 adds a NetherNet transport. Whether it makes console joins easier is not confirmed.

### Automation

- **Gametests (existing harness in `src/gametest`, run by `xvfb-run -a ./gradlew runClientGameTest`)**: classification with a stub
  `BedrockDetector`; `SimpleForm` to `Dialog` round-trip through `Dialog.DIRECT_CODEC`; payload
  validation, including forged ids, forged values and server settings from a non-op; slot gating.
  No Geyser needed.
- **Headless Bedrock client**: `bedrock-protocol` 3.60.1 (npm) speaks up to Bedrock 1.26.51, which
  overlaps Geyser's range, and can join offline when Geyser has
  `advanced.bedrock.validate-bedrock-login: false` **[V version, I offline]**. A script joins,
  waits for spawn, fails on disconnect, and answers `modal_form_request` with a scripted
  `modal_form_response`. That covers phases 1 and 2 end to end in CI. Phases 3 to 5 are visual
  and stay manual with screenshots, as `Pictures` does for readmes.
- **Vanilla Java**: `xvfb-run -a ./gradlew runClientGameTest` launches a Pandorical client, so a
  vanilla client test needs a separate launch without Pandorical. Reaching for something like a
  Mineflayer bot is S1's first job.

## 7. Risks and open questions

### Verify items, resolved

| Claim from the chat | Finding |
|---|---|
| Bedrock lands on the vanilla fallback at zero cost | **False for content.** No block or item fallback exists for vanilla Java. Bedrock depends on S2. |
| Render the shared model as Cumulus forms and as Java dialogs | **Unneeded.** Geyser translates dialogs. One renderer. |
| Geyser extension API for items, blocks, forms, camera, packs | **True, with differences.** Items v2 is `CustomItemDefinition` keyed on a Java item plus `item_model`; non-vanilla items use `NonVanillaCustomItemDefinition` with the Java network id; blocks use `CustomBlockData` with `JavaBlockState` overrides; camera covers fade, position, shake, fog, perspective, HUD elements; packs go through `GeyserDefineResourcePacksEvent` (the old load event is deprecated). Custom items and blocks need `enable-custom-content: true` and register only inside their events. |
| Geyser-Fabric runs alongside this mod version | **True.** 2.12.0 is built on minecraft 26.3, loader 0.19.5, Fabric API 0.160.6+26.3. |
| A mod reaches Geyser's API in-process | **True [I].** API shaded unrelocated; not available before Geyser's own `onInitialize`, so use `SERVER_STARTING`. |
| Java dialogs: shape, inputs, Quick Actions | **True.** Checked in 26.3 source. Quick Actions defaults to G. Dialogs can be sent inline. The click answer carries no player (mixin needed). |
| Floodgate identifies Bedrock players server-side | **True.** `isFloodgatePlayer(uuid)`, UUID `new UUID(0, xuid)`, prefix `.`, linked players keep their Java UUID. Known before configuration [I, S3]. |

### Spikes

Each is throwaway code in a scratch branch, deleted after its finding is written into this doc.

- **S1, vanilla Java against custom content.** Join pandorical-demo with a vanilla client, walk to
  a custom block, pick up a custom item, open the inventory with a slot mod installed. Record each
  disconnect and its message. Also check that a vanilla client accepts the `pandorical`
  quick-actions dialog entry.
- **S2, Geyser against custom content.** Done; see
  [S2: Geyser against modded ids, observed](#s2-geyser-against-modded-ids-observed).
- **S3, classification timing.** Log `isFloodgatePlayer` and `isBedrockPlayer` from
  `BEFORE_CONFIGURE` for a Bedrock join.
- **S4, dialog limits.** Send a dialog with the longest readme in the suite and 40 buttons, and look
  at it on Java and on Bedrock.

### Risks

- **Phase 1 might not be cheap.** If S2 shows Geyser disconnects on unknown ids, phase 1 has to
  pull 4a (block mapping) and a minimal item mapping forward. Without them, Bedrock players can't
  enter a world that has any custom content.
- **The vanilla Java content gap is out of scope here and should not be.** Fixing it means
  rewriting chunk and item packets per player, which is what Polymer did and what the suite retired.
  This plan flags the gap and does not solve it.
- **Item and block conversion.** Java item models and block models do not convert to Bedrock
  geometry in general. The pack builder handles flat items and cubes, and everything else keeps a
  fallback. A full converter is out of scope.
- **Geyser moves fast.** Four 2.12.0 builds in two days, an experimental entity API, a renamed
  config key (`add-non-bedrock-items` became `enable-custom-content`). Keeping the bridge in its own
  mod is what contains that churn.
- **Forms on gamepads.** A settings page with 15 fields is a long scroll on a controller. S4 and
  the console session decide whether settings pages split.
- **Custom click mixin.** If vanilla or Fabric ever passes the player through, the mixin should be
  deleted in favour of that hook.

### Open questions for review

1. Is a Bedrock player on a server with custom content worth building for, if vanilla Java players
   on the same server still cannot join? Phases 3 and 4 make Bedrock better off than vanilla Java.
2. Should action-menu icons on Bedrock be worth a Cumulus special case, or are labels enough?
3. Should `{players}` multi-select survive as a Bedrock `CustomForm` of toggles, or is one pick per
   press acceptable?
4. `SkinApi` on offline-mode servers: Floodgate sends Bedrock skins to Java players. Does anything
   need to go the other way?
