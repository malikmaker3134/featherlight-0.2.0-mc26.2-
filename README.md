# Featherlight 0.2.0 (set up for Minecraft 26.2)

Small client-side Fabric mod. This copy builds against 26.2 (see minecraft_version in gradle.properties).
Skips rendering far-away entities, scales that distance with your FPS, and has a
Sodium-style settings menu.

## Menu
Options > Video Settings now opens the Featherlight menu (Culling / Adaptive / About).
Changes are staged: nothing happens until you press Apply, and Undo reverts them.
The "Vanilla Video..." button in the sidebar opens Minecraft's own video settings.
To turn the replacement off, set "replaceVideoSettings": false in config/featherlight.json.

## Build
Needs JDK 25.

Easiest: generate a blank 26.3 project at https://fabricmc.net/develop/template, then copy
`src/` from this folder over its `src/` (keep its build files and wrapper).

Or use this folder directly:

    gradle wrapper --gradle-version 9.7.1
    ./gradlew runClient     # test in a dev client
    ./gradlew build         # jar ends up in build/libs/

## Verify it works
Open Options > Video Settings > About. Under Status:
- "Entity render hook: running" means the culling mixin applied (it flips once entities are drawn).
- "Video settings hook: running" means the menu replacement applied (you are looking at it).
The log also prints "Featherlight: entity culling is active" the first time something is culled.

## If something breaks
Written against Minecraft 26.2/26.3 Mojang names; not compiled against the real jars.
- Compile error in Featherlight.java: `Minecraft#getFps()` / `getCameraEntity()` may have moved.
- Compile error in the gui package: check `Screen#extractBackground`, `GuiGraphicsExtractor#fill/text`,
  `AbstractSliderButton#updateMessage/applyValue`, `Gui#screen()/setScreen()`.
- Vanilla buttons visible on top of the menu: the alpha-0 trick stopped working; tell me and I'll
  switch to overriding the widget render method.
- Mixins are fail-soft: a wrong target means that feature does nothing instead of crashing.
- Version numbers live in gradle.properties; compare with https://fabricmc.net/develop.

## Config: config/featherlight.json
enabled, entityCullDistance (48), itemCullDistance (24), sizeBonusPerBlock (8),
adaptive (true), targetFps (60), minScale (0.5), replaceVideoSettings (true).

## Build online (no installs)
1. Create a free GitHub account and a new empty repository.
2. Upload everything in this folder (including the hidden .github folder).
3. Open the Actions tab, run "Build mod jar", then download the "featherlight-jar" artifact.
4. Unzip it and put the .jar in your Minecraft mods folder (Fabric 26.2 installed).
