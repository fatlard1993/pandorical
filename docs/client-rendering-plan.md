# Client rendering: culling, batched drawing, dynamic lights

Status: proposal, nothing built. Written 2026-11-23 against Pandorical 15.36.0, Minecraft 26.3,
Fabric loader 0.19.5, with Sodium in the house client pack.

Goal: three things players usually get from separate client mods (EntityCulling, ImmediatelyFast,
LambDynamicLights) come with Pandorical instead, built to know about everything Pandorical draws, and
offered to every server that runs it with nothing more to install.

Facts are marked **[V]** where they were read in this repository or a mod's own page, and **[I]**
where inferred and still to be checked.

## Why in Pandorical rather than in the pack

- **The outside mods do not know Pandorical's own drawing [I].** A moving structure is drawn as one
  batch anchored to a server entity's tracking radius [V: `structures()`], not as that entity's model.
  A culler that judges the anchor by its own small box can hide a whole ship whose hull is in plain
  view. Portal views and captures draw the world from cameras other than the player's
  [V: `client/portal/PortalViews.java`, `client/capture/ViewCaptures.java`]; a culler that only knows
  the player's camera can blank them. Context models and entity models replace vanilla shapes
  [V: `client/contextmodel`, `client/entitymodel`].
- **One install, every server.** A household client follows one pack; a player on another Pandorical
  server has none of it. Built in, the feature reaches everyone the server reaches.
- **Precedent for stepping aside [V].** `clumpExperience` is left out when Clumps is installed and
  `itemMergeRadius` when Get It Together, Drops! is. Each feature here does the same for the mod it
  stands in for, so a player who already runs EntityCulling, ImmediatelyFast or LambDynamicLights
  keeps it.

## 1. Culling

Skip drawing entities and block entities the player cannot see.

- Test against the section occlusion graph Pandorical already reaches
  [V: `client/mixin/SectionOcclusionGraphAccessor.java`] rather than ray-casting per entity: cheap,
  and it agrees with what the chunk renderer already decided.
- A structure is judged by the bounds of its blocks, not its anchor entity's box.
- Per camera: a portal view or capture culls against its own camera, or not at all.
- **Measure first.** Profile a busy scene (a village, a farm with item frames, a ship) on a low-end
  client with and without EntityCulling. If the gain is small with Sodium already culling chunk
  sections, this feature is not worth its risk.

## 2. Batched drawing

Draw Pandorical's own HUD overlays, screens and text with fewer buffer flushes per frame.

- Scope it to what Pandorical draws. Batching vanilla's HUD is ImmediatelyFast's job and needs no
  copy here.
- **Measure first, most of all here [I].** Pandorical's overlays may cost little next to the world.
  Profile a frame with every suite HUD showing (Map++ minimap, Poopsmith's gut bar, armor-hud) before
  writing any of it; if they are cheap, the outcome is "test ImmediatelyFast against our screens"
  rather than a feature.

## 3. Dynamic lights

A torch, a glowing item or a burning mob lights the blocks around it while it moves.

- A **client setting** (on by default) for whether this player sees them, and a **server setting**
  (on by default) for whether the server offers them, in the same sections the Mods screen already
  shows [V: README, "The Mod Menu"].
- Light comes from: a held or dropped item whose block emits light; an entity on fire or glowing;
  and an item or entity a server mod declares, by a tag such as `pandorical:dynamic_light` with a
  level, so an item a suite mod adds lights without a client update.
- **Sodium is in the house pack, so it is the case that decides the design [I].** Sodium builds chunk
  meshes with its own light pipeline; LambDynamicLights ships Sodium compatibility for that reason.
  Read how it does it before choosing a lightmap approach, and test with Sodium from the first spike.
- Bedrock players see nothing from it (Geyser has no dynamic lights); nothing breaks for them.

## Order

Dynamic lights first: it adds something players see, with no gamble on a performance gain. Then
measure for culling and batching, and build only what the measurements justify.
