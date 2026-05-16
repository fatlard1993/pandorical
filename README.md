# Pandorical

Client-side enablement bridge for server-driven UI, content, and rendering on Fabric.

Server mods declare screens, HUD overlays, custom blocks/items, and camera hints. Pandorical handles the protocol, asset sync, and client rendering — vanilla clients join unaffected.

## Capabilities

| Capability | What it does |
|---|---|
| `screens` | Open, update, and close declarative UI screens per-player |
| `hud` | Show, update, and hide persistent HUD overlays |
| `content` | Sync custom blocks, items, and assets to the client on join |
| `camera` | Control camera distance and perspective |

## Requirements

- Minecraft 26.1
- Fabric Loader 0.18.5+
- Fabric API 0.144.3+26.1
- Java 25

## Usage

Add Pandorical as a dependency and interact through `PandoricalApi`.

```java
// Check before any API call — guards players on vanilla clients
if (!PandoricalApi.isAvailable(player)) return;

// Per-capability guard
if (!PandoricalApi.hasCapability(player, "screens")) return;
```

### Screens

```java
ScreenApi screens = PandoricalApi.screens();

// Open a screen
screens.open(player, new OpenScreenS2C(...));

// Open a screen backed by a server-side container
screens.openContainer(player, screenDef, serverContainer, readOnlySlots);

// Push live updates to an open screen
screens.update(player, screenId, List.of(new ComponentUpdate(...)));

// Close a screen
screens.close(player, screenId);

// Handle actions from the client
screens.onAction("my-screen", "confirm-button", (player, data) -> { ... });
screens.onActionFallback("my-screen", (player, data) -> {
    String componentId = data.get("_componentId");
    // ...
});
screens.onClose("my-screen", player -> { ... });
screens.onSlotChange("my-screen", (player, slotIndex, stack) -> { ... });
screens.onContainerRemoved("my-screen", player -> { /* return items */ });
```

### HUD

```java
HudApi hud = PandoricalApi.hud();

hud.show(player, new ShowHudS2C(...));
hud.update(player, overlayId, updates);
hud.hide(player, overlayId);
```

### Content

Register blocks, items, and assets during `onInitialize`. They are synced to clients in the configuration phase, before Fabric's registry sync.

```java
ContentApi content = PandoricalApi.content();

content.registerBlock("my-mod:my-block", new BlockRegistration(...));
content.registerItem("my-mod:my-item", new ItemRegistration(...));
content.registerAsset("my-mod/models/block/my-block.json", jsonBytes);
content.registerModAssets("my-mod"); // auto-scans classpath assets/

// Override a vanilla item's appearance for Pandorical clients only
content.overrideVanillaItem("minecraft:rabbit_hide", new VanillaItemOverride(...));
```

Content readiness is tracked per-player — wait for `isContentReady` before opening screens that depend on synced assets:

```java
if (PandoricalApi.isContentReady(player)) {
    screens.open(player, ...);
}
```

### Camera

```java
CameraApi camera = PandoricalApi.camera();

camera.setDistance(player, 6.0f);
camera.setPerspective(player, "third_person_back");
camera.reset(player);
```

## Components

Screens and HUD overlays are composed from these component types:

`panel` · `scroll-panel` · `text` · `button` · `text-input` · `sprite` · `item-slot` · `inventory-grid` · `map`

## License

MIT
